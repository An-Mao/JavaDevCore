package dev.anye.core.system.task;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;

/**
 * 高性能分层时间轮。
 *
 * <p>特点：
 * <ul>
 *     <li>O(1) ~ O(k) 平均调度成本</li>
 *     <li>多个线程可以同时 schedule / cancel</li>
 *     <li>单 Worker 独占修改 Bucket，无 Bucket 锁</li>
 *     <li>支持一次性任务</li>
 *     <li>支持 Fixed Rate</li>
 *     <li>支持 Fixed Delay</li>
 *     <li>支持 Debounce</li>
 *     <li>支持长时间暂停恢复</li>
 *     <li>使用 System.nanoTime() 单调时钟</li>
 *     <li>调度线程与任务执行线程分离</li>
 * </ul>
 *
 * 普通定时任务
 * TimingWheel wheel = TimingWheel.builder()
 *         .tick(10, TimeUnit.MILLISECONDS)
 *         .wheelSize(512)
 *         .executor(ForkJoinPool.commonPool())
 *         .threadName("Core-Timer")
 *         .daemon(true)
 *         .build();
 *
 * wheel.schedule(
 *         () -> System.out.println("Hello"),
 *         1,
 *         TimeUnit.SECONDS
 * );
 *
 *周期任务
 *wheel.scheduleAtFixedRate(
 *         () -> {
 *             System.out.println("tick");
 *         },
 *         0,
 *         1,
 *         TimeUnit.SECONDS
 * );
 *
 *
 *取消
 *TimingWheel.TimerTask task =
 *         wheel.schedule(
 *                 () -> System.out.println("test"),
 *                 10,
 *                 TimeUnit.SECONDS
 *         );
 *
 * task.cancel();
 *
 *
 *
 * <p>注意：
 * TimingWheel 只负责「什么时候执行」，
 * Executor 负责「在哪里执行」。
 */
public final class TimingWheel implements AutoCloseable {

	/* ============================================================
	 * State
	 * ============================================================ */

	private static final int NEW = 0;
	private static final int SCHEDULED = 1;
	private static final int RUNNING = 2;
	private static final int CANCELLED = 3;
	private static final int DONE = 4;

	/* ============================================================
	 * Default
	 * ============================================================ */

	public static final long DEFAULT_TICK = 10;

	public static final TimeUnit DEFAULT_TICK_UNIT =
			TimeUnit.MILLISECONDS;

	public static final int DEFAULT_WHEEL_SIZE = 512;

	/**
	 * 时间轮层数。
	 *
	 * L0 -> L1 -> L2 -> L3
	 */
	private static final int LEVEL_COUNT = 4;

	/**
	 * 一轮最多从 pending 中取多少任务。
	 *
	 * 防止 producer 疯狂提交任务时 Worker 长时间
	 * 卡在 pending 队列，无法处理时间轮。
	 */
	private static final int MAX_DRAIN_PER_ROUND = 8192;

	/**
	 * Worker 长时间暂停后的最大正常 catch-up Tick。
	 *
	 * 超过该值直接进入全 Bucket 恢复。
	 */
	private static final long MAX_CATCH_UP_TICKS = 100_000;

	/* ============================================================
	 * Configuration
	 * ============================================================ */

	private final long tickNanos;

	private final int wheelSize;

	private final int mask;

	/**
	 * 时间轮起点。
	 */
	private final long startTime;

	/**
	 * 实际执行 Runnable 的 Executor。
	 */
	private final Executor executor;

	/**
	 * 用户异常处理器。
	 */
	private final BiConsumer<TimerTask, Throwable>
			exceptionHandler;

	/**
	 * 时间轮层。
	 */
	private final WheelLevel[] levels;

	/**
	 * Producer -> Worker。
	 *
	 * ConcurrentLinkedQueue 天然适合 MPSC。
	 */
	private final ConcurrentLinkedQueue<TimerTask> pending =
			new ConcurrentLinkedQueue<>();

	/**
	 * Debounce 任务。
	 *
	 * key -> 当前任务
	 */
	private final ConcurrentHashMap<Object, TimerTask>
			keyedTasks =
			new ConcurrentHashMap<>();

	/**
	 * Worker 是否运行。
	 */
	private final AtomicBoolean running =
			new AtomicBoolean(true);

	/**
	 * Worker 是否准备进入 park。
	 */
	private final AtomicBoolean parked =
			new AtomicBoolean(false);

	/**
	 * 活跃任务数量。
	 */
	private final AtomicInteger taskCount =
			new AtomicInteger();

	/**
	 * 任务 ID。
	 */
	private final AtomicLong taskId =
			new AtomicLong();

	/**
	 * 统计。
	 */
	private final Metrics metrics =
			new Metrics();

	/**
	 * Worker。
	 */
	private final Thread worker;

	/**
	 * 当前 L0 Tick。
	 *
	 * 只由 Worker 修改。
	 */
	private long currentTick;

	/* ============================================================
	 * Constructor
	 * ============================================================ */

	public TimingWheel() {
		this(builder());
	}

	public TimingWheel(
			long tick,
			TimeUnit unit,
			int wheelSize,
			Executor executor
	) {
		this(
				builder()
						.tick(tick, unit)
						.wheelSize(wheelSize)
						.executor(executor)
		);
	}

	private TimingWheel(Builder builder) {

		Objects.requireNonNull(
				builder.unit,
				"unit"
		);

		Objects.requireNonNull(
				builder.executor,
				"executor"
		);

		Objects.requireNonNull(
				builder.threadName,
				"threadName"
		);

		Objects.requireNonNull(
				builder.exceptionHandler,
				"exceptionHandler"
		);

		if (builder.tick <= 0) {
			throw new IllegalArgumentException(
					"tick <= 0"
			);
		}

		if (
				builder.wheelSize < 2 ||
						(builder.wheelSize &
								(builder.wheelSize - 1)) != 0
		) {
			throw new IllegalArgumentException(
					"wheelSize must be a power of 2"
			);
		}

		long nanos =
				builder.unit.toNanos(
						builder.tick
				);

		if (nanos <= 0) {
			throw new IllegalArgumentException(
					"tick is too small"
			);
		}

		this.tickNanos = nanos;

		this.wheelSize =
				builder.wheelSize;

		this.mask =
				builder.wheelSize - 1;

		this.executor =
				builder.executor;

		this.exceptionHandler =
				builder.exceptionHandler;

		this.startTime =
				System.nanoTime();

		/*
		 * 创建 4 层时间轮。
		 *
		 * L0 = tick
		 * L1 = tick * wheelSize
		 * L2 = tick * wheelSize^2
		 * L3 = tick * wheelSize^3
		 */
		this.levels =
				new WheelLevel[LEVEL_COUNT];

		long duration = tickNanos;

		long scale = 1;

		for (int i = 0;
			 i < LEVEL_COUNT;
			 i++) {

			levels[i] =
					new WheelLevel(
							duration,
							scale,
							wheelSize
					);

			if (i + 1 < LEVEL_COUNT) {

				duration =
						safeMultiply(
								duration,
								wheelSize
						);

				scale =
						safeMultiply(
								scale,
								wheelSize
						);
			}
		}

		currentTick = 0;

		worker =
				new Thread(
						this::workerLoop,
						builder.threadName
				);

		worker.setDaemon(
				builder.daemon
		);

		worker.start();
	}

	/* ============================================================
	 * Builder
	 * ============================================================ */

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private long tick =
				DEFAULT_TICK;

		private TimeUnit unit =
				DEFAULT_TICK_UNIT;

		private int wheelSize =
				DEFAULT_WHEEL_SIZE;

		private Executor executor =
				ForkJoinPool.commonPool();

		private String threadName =
				"TimingWheel-Worker";

		private boolean daemon =
				true;

		private BiConsumer<TimerTask, Throwable>
				exceptionHandler =
				(task, throwable) ->
						throwable.printStackTrace();

		public Builder tick(
				long tick,
				TimeUnit unit
		) {
			this.tick = tick;

			this.unit =
					Objects.requireNonNull(unit);

			return this;
		}

		public Builder wheelSize(
				int wheelSize
		) {
			this.wheelSize =
					wheelSize;

			return this;
		}

		public Builder executor(
				Executor executor
		) {
			this.executor =
					Objects.requireNonNull(executor);

			return this;
		}

		public Builder threadName(
				String name
		) {
			this.threadName =
					Objects.requireNonNull(name);

			return this;
		}

		public Builder daemon(
				boolean daemon
		) {
			this.daemon = daemon;

			return this;
		}

		public Builder exceptionHandler(
				BiConsumer<TimerTask, Throwable>
						handler
		) {
			this.exceptionHandler =
					Objects.requireNonNull(handler);

			return this;
		}

		public TimingWheel build() {
			return new TimingWheel(this);
		}
	}

	/* ============================================================
	 * Schedule
	 * ============================================================ */

	/**
	 * 调度一次性任务。
	 */
	public TimerTask schedule(
			Runnable runnable,
			long delay,
			TimeUnit unit
	) {

		Objects.requireNonNull(runnable);
		Objects.requireNonNull(unit);

		checkRunning();

		long delayNanos =
				unit.toNanos(delay);

		if (delayNanos < 0) {
			delayNanos = 0;
		}

		long deadline =
				safeAdd(
						System.nanoTime(),
						delayNanos
				);

		TimerTask task =
				new TimerTask(
						taskId.incrementAndGet(),
						runnable,
						deadline,
						0,
						false,
						null
				);

		task.state.set(SCHEDULED);

		taskCount.incrementAndGet();

		metrics.scheduled.incrementAndGet();

		submit(task);

		return task;
	}

	/**
	 * Fixed Rate。
	 *
	 * <p>例如：
	 *
	 * <pre>
	 * 0s
	 * 1s
	 * 2s
	 * 3s
	 * </pre>
	 */
	public TimerTask scheduleAtFixedRate(
			Runnable runnable,
			long initialDelay,
			long period,
			TimeUnit unit
	) {

		return schedulePeriodic(
				runnable,
				initialDelay,
				period,
				unit,
				true
		);
	}

	/**
	 * Fixed Delay。
	 *
	 * <p>例如任务执行 200ms，delay=1s：
	 *
	 * <pre>
	 * 开始
	 *   ↓
	 * 执行 200ms
	 *   ↓
	 * 等 1s
	 *   ↓
	 * 再执行
	 * </pre>
	 */
	public TimerTask scheduleWithFixedDelay(
			Runnable runnable,
			long initialDelay,
			long delay,
			TimeUnit unit
	) {

		return schedulePeriodic(
				runnable,
				initialDelay,
				delay,
				unit,
				false
		);
	}

	private TimerTask schedulePeriodic(
			Runnable runnable,
			long initialDelay,
			long period,
			TimeUnit unit,
			boolean fixedRate
	) {

		Objects.requireNonNull(runnable);
		Objects.requireNonNull(unit);

		if (period <= 0) {
			throw new IllegalArgumentException(
					"period <= 0"
			);
		}

		checkRunning();

		long initialNanos =
				unit.toNanos(initialDelay);

		if (initialNanos < 0) {
			initialNanos = 0;
		}

		long periodNanos =
				unit.toNanos(period);

		if (periodNanos <= 0) {
			throw new IllegalArgumentException(
					"period is too small"
			);
		}

		long deadline =
				safeAdd(
						System.nanoTime(),
						initialNanos
				);

		TimerTask task =
				new TimerTask(
						taskId.incrementAndGet(),
						runnable,
						deadline,
						periodNanos,
						fixedRate,
						null
				);

		task.state.set(SCHEDULED);

		taskCount.incrementAndGet();

		metrics.scheduled.incrementAndGet();

		submit(task);

		return task;
	}

	/* ============================================================
	 * Debounce
	 * ============================================================ */

	/**
	 * Debounce。
	 *
	 * <p>同一个 key 在 delay 时间内重复提交，
	 * 只保留最后一次任务。
	 *
	 * <pre>
	 * save(A)
	 * save(A)
	 * save(A)
	 * save(A)
	 *
	 *       ↓
	 *
	 * 最终只执行一次
	 * </pre>
	 */
	public TimerTask scheduleDebounce(
			Object key,
			Runnable runnable,
			long delay,
			TimeUnit unit
	) {

		Objects.requireNonNull(key);
		Objects.requireNonNull(runnable);
		Objects.requireNonNull(unit);

		checkRunning();

		long delayNanos =
				unit.toNanos(delay);

		if (delayNanos < 0) {
			delayNanos = 0;
		}

		long deadline =
				safeAdd(
						System.nanoTime(),
						delayNanos
				);

		TimerTask task =
				new TimerTask(
						taskId.incrementAndGet(),
						runnable,
						deadline,
						0,
						false,
						key
				);

		task.state.set(SCHEDULED);

		taskCount.incrementAndGet();

		metrics.scheduled.incrementAndGet();

		/*
		 * 先放新任务。
		 *
		 * 这样旧任务完成/取消时：
		 *
		 * keyedTasks.remove(key, old)
		 *
		 * 不会误删新任务。
		 */
		TimerTask old =
				keyedTasks.put(
						key,
						task
				);

		if (old != null) {
			old.cancel();
		}

		submit(task);

		return task;
	}

	/**
	 * 根据 key 取消 Debounce 任务。
	 */
	public boolean cancelDebounce(
			Object key
	) {

		if (key == null) {
			return false;
		}

		TimerTask task =
				keyedTasks.remove(key);

		if (task == null) {
			return false;
		}

		return task.cancel();
	}

	/* ============================================================
	 * Submit
	 * ============================================================ */

	private void submit(
			TimerTask task
	) {

		pending.offer(task);

		/*
		 * 只有 Worker 已经准备 park 时，
		 * 才需要 unpark。
		 */
		if (parked.get()) {
			LockSupport.unpark(worker);
		}
	}

	/* ============================================================
	 * Worker
	 * ============================================================ */

	private void workerLoop() {

		while (running.get()) {

			/*
			 * 1. 批量接收新任务。
			 */
			drainPending();

			/*
			 * 2. 一轮只读取一次时间。
			 */
			long now =
					System.nanoTime();

			/*
			 * 3. 计算目标 Tick。
			 */
			long targetTick =
					elapsedToTick(now);

			long delta =
					targetTick - currentTick;

			/*
			 * 4. 长时间暂停恢复。
			 */
			if (
					delta >
							MAX_CATCH_UP_TICKS
			) {

				recoverAfterLongPause(now);

				currentTick =
						targetTick;

			} else {

				/*
				 * 5. 正常 Catch-up。
				 */
				while (
						currentTick <
								targetTick &&
								running.get()
				) {

					currentTick++;

					advance(
							currentTick,
							now
					);
				}
			}

			if (!running.get()) {
				break;
			}

			/*
			 * pending 已经有任务，
			 * 下一轮继续处理。
			 */
			if (!pending.isEmpty()) {
				continue;
			}

			/*
			 * 6. 计算距离下一个 Tick 的时间。
			 */
			long nextTickTime =
					tickDeadline(
							currentTick + 1
					);

			long wait =
					nextTickTime -
							System.nanoTime();

			if (wait <= 0) {
				continue;
			}

			/*
			 * 准备 park。
			 */
			parked.set(true);

			/*
			 * 防止：
			 *
			 * producer:
			 * offer()
			 *
			 * 恰好发生在：
			 *
			 * pending.isEmpty()
			 *
			 * 和 park() 之间。
			 */
			if (!pending.isEmpty()) {

				parked.set(false);

				continue;
			}

			LockSupport.parkNanos(
					this,
					wait
			);

			parked.set(false);
		}

		cleanup();
	}

	/* ============================================================
	 * Pending
	 * ============================================================ */

	private void drainPending() {

		int count = 0;

		while (
				count++ <
						MAX_DRAIN_PER_ROUND
		) {

			TimerTask task =
					pending.poll();

			if (task == null) {
				break;
			}

			int state =
					task.state.get();

			if (state == CANCELLED) {

				finishCancelled(task);

				continue;
			}

			if (!running.get()) {

				cancelInternal(task);

				continue;
			}

			/*
			 * 同一轮 Worker 中，
			 * 传递统一 now。
			 */
			placeTask(
					task,
					System.nanoTime()
			);
		}
	}

	/* ============================================================
	 * Advance
	 * ============================================================ */

	private void advance(
			long tick,
			long now
	) {

		/*
		 * L0。
		 */
		expireBucket(
				levels[0].buckets[
						(int) (
								tick & mask
						)
						],
				now
		);

		/*
		 * L0 转一圈。
		 *
		 * 触发 L1。
		 */
		if ((tick & mask) == 0) {

			cascade(
					1,
					now
			);
		}
	}

	/**
	 * 高层 Bucket 下沉。
	 */
	private void cascade(
			int level,
			long now
	) {

		if (level >= LEVEL_COUNT) {
			return;
		}

		/*
		 * 注意：
		 *
		 * 这里使用：
		 *
		 * currentTick / scale
		 *
		 * 而不是把上一层 tick 继续传下来。
		 *
		 * 这是分层时间轮正确计算的关键。
		 */
		long levelTick =
				currentLevelTick(level);

		Bucket bucket =
				levels[level].buckets[
						(int) (
								levelTick & mask
						)
						];

		TimerTask task =
				bucket.head;

		while (task != null) {

			TimerTask next =
					task.next;

			bucket.remove(task);

			task.bucket = null;

			if (
					task.state.get() ==
							CANCELLED
			) {

				finishCancelled(task);

			} else {

				placeTask(
						task,
						now
				);
			}

			task = next;
		}

		/*
		 * 当前层也转了一圈，
		 * 继续向上 Cascade。
		 */
		if ((levelTick & mask) == 0) {

			cascade(
					level + 1,
					now
			);
		}
	}

	/* ============================================================
	 * Place
	 * ============================================================ */

	private void placeTask(
			TimerTask task,
			long now
	) {

		if (
				task.state.get() ==
						CANCELLED
		) {

			finishCancelled(task);

			return;
		}

		long delay =
				task.deadline - now;

		/*
		 * 已经到期。
		 */
		if (delay <= 0) {

			executeTask(task);

			return;
		}

		/*
		 * 转换成 L0 deadline Tick。
		 *
		 * 这是整个时间轮的统一时间坐标。
		 */
		long elapsed =
				task.deadline - startTime;

		if (elapsed <= 0) {

			executeTask(task);

			return;
		}

		long deadlineTick =
				ceilDivPositive(
						elapsed,
						tickNanos
				);

		if (deadlineTick <= currentTick) {

			executeTask(task);

			return;
		}

		/*
		 * 根据剩余时间选择层。
		 */
		int level =
				selectLevel(delay);

		/*
		 * 将 L0 Tick 转换成对应层 Tick。
		 *
		 * 例如：
		 *
		 * L0 = 10ms
		 * wheel = 512
		 *
		 * L1 Tick = L0 / 512
		 */
		long targetLevelTick =
				deadlineTick /
						levels[level].scale;

		long currentLevel =
				currentLevelTick(level);

		/*
		 * 高层当前 Bucket 已经处理过。
		 *
		 * 不能重新放进这个 Bucket，
		 * 否则可能要再等一整圈。
		 *
		 * 直接降到 L0。
		 */
		if (
				level > 0 &&
						targetLevelTick <= currentLevel
		) {

			level = 0;

			targetLevelTick =
					deadlineTick;

			currentLevel =
					currentTick;
		}

		/*
		 * 防止放入已经处理过的 Slot。
		 */
		if (
				targetLevelTick <=
						currentLevel
		) {

			targetLevelTick =
					currentLevel + 1;
		}

		Bucket bucket =
				levels[level].buckets[
						(int) (
								targetLevelTick &
										mask
						)
						];

		bucket.add(task);

		task.bucket = bucket;

		task.level = level;
	}

	/**
	 * 根据 delay 选择时间轮层。
	 */
	private int selectLevel(
			long delay
	) {

		for (
				int i = 0;
				i < LEVEL_COUNT;
				i++
		) {

			long range =
					safeMultiply(
							levels[i].duration,
							wheelSize
					);

			if (delay < range) {
				return i;
			}
		}

		return LEVEL_COUNT - 1;
	}

	/* ============================================================
	 * Expire
	 * ============================================================ */

	private void expireBucket(
			Bucket bucket,
			long now
	) {

		TimerTask task =
				bucket.head;

		while (task != null) {

			TimerTask next =
					task.next;

			bucket.remove(task);

			task.bucket = null;

			if (
					task.state.get() ==
							CANCELLED
			) {

				finishCancelled(task);

			} else if (
					task.deadline <= now
			) {

				executeTask(task);

			} else {

				/*
				 * Bucket 到期了，
				 * 但任务实际 deadline 还没到。
				 *
				 * 重新计算位置。
				 */
				placeTask(
						task,
						now
				);
			}

			task = next;
		}
	}

	/* ============================================================
	 * Long Pause Recovery
	 * ============================================================ */

	private void recoverAfterLongPause(
			long now
	) {

		/*
		 * 极端情况下：
		 *
		 * Worker 可能暂停数分钟/数小时。
		 *
		 * 不能直接跳 currentTick，
		 * 否则会跳过 Bucket。
		 *
		 * 所以扫描全部 Bucket。
		 */
		for (
				WheelLevel level :
				levels
		) {

			for (
					Bucket bucket :
					level.buckets
			) {

				TimerTask task =
						bucket.head;

				while (task != null) {

					TimerTask next =
							task.next;

					bucket.remove(task);

					task.bucket = null;

					if (
							task.state.get() ==
									CANCELLED
					) {

						finishCancelled(task);

					} else if (
							task.deadline <= now
					) {

						executeTask(task);

					} else {

						placeTask(
								task,
								now
						);
					}

					task = next;
				}
			}
		}
	}

	/* ============================================================
	 * Execute
	 * ============================================================ */

	private void executeTask(
			TimerTask task
	) {

		/*
		 * 只有 SCHEDULED 才能进入 RUNNING。
		 */
		if (
				!task.state.compareAndSet(
						SCHEDULED,
						RUNNING
				)
		) {

			if (
					task.state.get() ==
							CANCELLED
			) {

				finishCancelled(task);
			}

			return;
		}

		try {

			executor.execute(
					() -> runTask(task)
			);

		} catch (
				RejectedExecutionException e
		) {

			metrics.rejected.incrementAndGet();

			if (
					task.state.compareAndSet(
							RUNNING,
							CANCELLED
					)
			) {

				finishCancelled(task);
			}

			handleException(
					task,
					e
			);

		} catch (Throwable e) {

			if (
					task.state.compareAndSet(
							RUNNING,
							CANCELLED
					)
			) {

				finishCancelled(task);
			}

			handleException(
					task,
					e
			);
		}
	}

	/* ============================================================
	 * Run
	 * ============================================================ */

	private void runTask(
			TimerTask task
	) {

		/*
		 * 任务可能已经：
		 *
		 * executor.execute()
		 *
		 * 但还没真正开始，
		 * 此时被 cancel。
		 */
		if (
				task.state.get() != RUNNING
		) {

			finishCancelled(task);

			return;
		}

		try {

			task.runnable.run();

			metrics.executed.incrementAndGet();

			if (task.periodNanos > 0) {

				metrics.periodicExecuted
						.incrementAndGet();
			}

		} catch (Throwable e) {

			metrics.failed.incrementAndGet();

			handleException(
					task,
					e
			);

			/*
			 * 默认：
			 *
			 * 周期任务异常后继续。
			 *
			 * 可以通过：
			 *
			 * task.cancelOnException(true)
			 *
			 * 改成异常即取消。
			 */
			if (
					task.periodNanos > 0 &&
							task.cancelOnException
			) {

				task.cancel();
			}

		} finally {

			afterRun(task);
		}
	}

	/* ============================================================
	 * After Run
	 * ============================================================ */

	private void afterRun(
			TimerTask task
	) {

		/*
		 * 已经被 cancel。
		 */
		if (
				task.state.get() ==
						CANCELLED
		) {

			finishCancelled(task);

			return;
		}

		/*
		 * 周期任务。
		 */
		if (
				task.periodNanos > 0 &&
						running.get()
		) {

			if (
					task.state.compareAndSet(
							RUNNING,
							SCHEDULED
					)
			) {

				long now =
						System.nanoTime();

				if (task.fixedRate) {

					/*
					 * Fixed Rate：
					 *
					 * next =
					 * old deadline + period
					 */
					long next =
							safeAdd(
									task.deadline,
									task.periodNanos
							);

					/*
					 * 如果已经错过多个周期，
					 * 不疯狂补执行。
					 */
					if (next <= now) {

						long behind =
								now - next;

						long missed =
								behind /
										task.periodNanos
										+ 1;

						next =
								safeAdd(
										next,
										safeMultiply(
												missed,
												task.periodNanos
										)
								);
					}

					task.deadline =
							next;

				} else {

					/*
					 * Fixed Delay：
					 *
					 * completion + delay
					 */
					task.deadline =
							safeAdd(
									now,
									task.periodNanos
							);
				}

				/*
				 * 周期任务复用同一个 TimerTask。
				 */
				submit(task);

				return;
			}
		}

		/*
		 * 一次性任务完成。
		 */
		if (
				task.state.compareAndSet(
						RUNNING,
						DONE
				)
		) {

			removeKey(task);

			decrementTaskCount(task);

		} else {

			finishCancelled(task);
		}
	}

	/* ============================================================
	 * Cancel
	 * ============================================================ */

	private boolean cancel(
			TimerTask task
	) {

		while (true) {

			int state =
					task.state.get();

			if (
					state == DONE ||
							state == CANCELLED
			) {

				return false;
			}

			if (
					task.state.compareAndSet(
							state,
							CANCELLED
					)
			) {

				/*
				 * Bucket 不立即删除。
				 *
				 * Worker 下次访问 Bucket 时清理。
				 */
				removeKey(task);

				metrics.cancelled.incrementAndGet();

				finishCancelled(task);

				return true;
			}
		}
	}

	private void cancelInternal(
			TimerTask task
	) {

		while (true) {

			int state =
					task.state.get();

			if (
					state == DONE ||
							state == CANCELLED
			) {

				return;
			}

			if (
					task.state.compareAndSet(
							state,
							CANCELLED
					)
			) {

				removeKey(task);

				metrics.cancelled.incrementAndGet();

				finishCancelled(task);

				return;
			}
		}
	}

	private void finishCancelled(
			TimerTask task
	) {

		decrementTaskCount(task);
	}

	/**
	 * 保证一个 TimerTask 只 decrement 一次。
	 */
	private void decrementTaskCount(
			TimerTask task
	) {

		if (
				task.counted.compareAndSet(
						true,
						false
				)
		) {

			taskCount.decrementAndGet();
		}
	}

	private void removeKey(
			TimerTask task
	) {

		if (task.key != null) {

			keyedTasks.remove(
					task.key,
					task
			);
		}
	}

	/* ============================================================
	 * Exception
	 * ============================================================ */

	private void handleException(
			TimerTask task,
			Throwable throwable
	) {

		try {

			exceptionHandler.accept(
					task,
					throwable
			);

		} catch (Throwable ignored) {

			/*
			 * 用户异常处理器不能杀死 Worker。
			 */
		}
	}

	/* ============================================================
	 * Shutdown
	 * ============================================================ */

	public void shutdown() {

		if (
				running.compareAndSet(
						true,
						false
				)
		) {

			LockSupport.unpark(worker);
		}
	}

	public void shutdownAndWait()
			throws InterruptedException {

		shutdown();

		worker.join();
	}

	public boolean awaitTermination(
			long timeout,
			TimeUnit unit
	) throws InterruptedException {

		Objects.requireNonNull(unit);

		long millis =
				Math.max(
						0,
						unit.toMillis(timeout)
				);

		worker.join(millis);

		return !worker.isAlive();
	}

	public boolean isRunning() {
		return running.get();
	}

	public int size() {
		return taskCount.get();
	}

	public long currentTick() {
		return currentTick;
	}

	public long tickNanos() {
		return tickNanos;
	}

	/* ============================================================
	 * Metrics
	 * ============================================================ */

	public MetricsSnapshot metrics() {
		return metrics.snapshot();
	}

	/* ============================================================
	 * Cleanup
	 * ============================================================ */

	private void cleanup() {

		/*
		 * 清理 pending。
		 */
		TimerTask task;

		while (
				(task = pending.poll()) != null
		) {

			cancelInternal(task);
		}

		/*
		 * 清理 Bucket。
		 *
		 * 注意：
		 *
		 * 不能：
		 *
		 * taskCount.set(0)
		 *
		 * 因为 Executor 中可能仍然存在
		 * RUNNING 任务。
		 */
		for (
				WheelLevel level :
				levels
		) {

			for (
					Bucket bucket :
					level.buckets
			) {

				task = bucket.head;

				while (task != null) {

					TimerTask next =
							task.next;

					bucket.remove(task);

					task.bucket = null;

					cancelInternal(task);

					task = next;
				}
			}
		}

		keyedTasks.clear();
	}

	/* ============================================================
	 * Time
	 * ============================================================ */

	private long elapsedToTick(
			long now
	) {

		long elapsed =
				now - startTime;

		if (elapsed <= 0) {
			return 0;
		}

		return elapsed / tickNanos;
	}

	private long tickDeadline(
			long tick
	) {

		return safeAdd(
				startTime,
				safeMultiply(
						tick,
						tickNanos
				)
		);
	}

	/**
	 * 当前层的 Tick。
	 *
	 * L0:
	 *
	 * currentTick / 1
	 *
	 * L1:
	 *
	 * currentTick / wheelSize
	 *
	 * L2:
	 *
	 * currentTick / wheelSize^2
	 */
	private long currentLevelTick(
			int level
	) {

		return currentTick /
				levels[level].scale;
	}

	/**
	 * 正数 ceil(a / b)。
	 */
	private static long ceilDivPositive(
			long a,
			long b
	) {

		if (a <= 0) {
			return 0;
		}

		return (a - 1) / b + 1;
	}

	private static long safeAdd(
			long a,
			long b
	) {

		if (
				b > 0 &&
						a > Long.MAX_VALUE - b
		) {

			return Long.MAX_VALUE;
		}

		if (
				b < 0 &&
						a < Long.MIN_VALUE - b
		) {

			return Long.MIN_VALUE;
		}

		return a + b;
	}

	private static long safeMultiply(
			long a,
			long b
	) {

		if (a == 0 || b == 0) {
			return 0;
		}

		if (a > 0 && b > 0) {

			if (a > Long.MAX_VALUE / b) {
				return Long.MAX_VALUE;
			}

		} else if (a < 0 && b < 0) {

			if (a < Long.MAX_VALUE / b) {
				return Long.MAX_VALUE;
			}

		} else if (a > 0) {

			if (b < Long.MIN_VALUE / a) {
				return Long.MIN_VALUE;
			}

		} else {

			if (a < Long.MIN_VALUE / b) {
				return Long.MIN_VALUE;
			}
		}

		return a * b;
	}

	private void checkRunning() {

		if (!running.get()) {

			throw new IllegalStateException(
					"TimingWheel is shutdown"
			);
		}
	}

	/* ============================================================
	 * Wheel Level
	 * ============================================================ */

	private static final class WheelLevel {

		/**
		 * 当前层一个 Tick 的时间长度。
		 */
		final long duration;

		/**
		 * 当前层相对于 L0 的 Tick 缩放。
		 *
		 * L0 = 1
		 * L1 = wheelSize
		 * L2 = wheelSize^2
		 * L3 = wheelSize^3
		 */
		final long scale;

		final Bucket[] buckets;

		WheelLevel(
				long duration,
				long scale,
				int wheelSize
		) {

			this.duration = duration;

			this.scale = scale;

			this.buckets =
					new Bucket[wheelSize];

			for (
					int i = 0;
					i < wheelSize;
					i++
			) {

				buckets[i] =
						new Bucket();
			}
		}
	}

	/* ============================================================
	 * Bucket
	 * ============================================================ */

	/**
	 * Worker 独占操作。
	 *
	 * 不需要 synchronized。
	 */
	private static final class Bucket {

		TimerTask head;

		TimerTask tail;

		void add(
				TimerTask task
		) {

			task.prev = tail;

			task.next = null;

			if (tail != null) {

				tail.next = task;

			} else {

				head = task;
			}

			tail = task;
		}

		void remove(
				TimerTask task
		) {

			TimerTask prev =
					task.prev;

			TimerTask next =
					task.next;

			if (prev != null) {

				prev.next = next;

			} else {

				head = next;
			}

			if (next != null) {

				next.prev = prev;

			} else {

				tail = prev;
			}

			task.prev = null;

			task.next = null;
		}
	}

	/* ============================================================
	 * TimerTask
	 * ============================================================ */

	public final class TimerTask {

		private final long id;

		private final Runnable runnable;

		private final AtomicInteger state =
				new AtomicInteger(NEW);

		/**
		 * 保证 taskCount 只减少一次。
		 */
		private final AtomicBoolean counted =
				new AtomicBoolean(true);

		/**
		 * 0 = 一次性。
		 */
		private final long periodNanos;

		private final boolean fixedRate;

		/**
		 * Debounce key。
		 */
		private final Object key;

		private volatile long deadline;

		/**
		 * 周期任务异常后是否取消。
		 */
		private volatile boolean cancelOnException;

		/**
		 * 当前所在 Bucket。
		 *
		 * 仅用于观察/debug。
		 */
		private volatile Bucket bucket;

		/**
		 * 当前所在层。
		 */
		private volatile int level = -1;

		/*
		 * 以下两个字段只由 Worker 修改。
		 */
		private TimerTask prev;

		private TimerTask next;

		private TimerTask(
				long id,
				Runnable runnable,
				long deadline,
				long periodNanos,
				boolean fixedRate,
				Object key
		) {

			this.id = id;

			this.runnable =
					runnable;

			this.deadline =
					deadline;

			this.periodNanos =
					periodNanos;

			this.fixedRate =
					fixedRate;

			this.key =
					key;
		}

		public boolean cancel() {
			return TimingWheel.this.cancel(this);
		}

		public boolean isCancelled() {
			return state.get() == CANCELLED;
		}

		public boolean isDone() {

			int state =
					this.state.get();

			return state == DONE ||
					state == CANCELLED;
		}

		public boolean isRunning() {
			return state.get() == RUNNING;
		}

		public boolean isPeriodic() {
			return periodNanos > 0;
		}

		public long getId() {
			return id;
		}

		public long getDeadlineNanos() {
			return deadline;
		}

		public long getPeriodNanos() {
			return periodNanos;
		}

		public int getLevel() {
			return level;
		}

		public boolean isCancelOnException() {
			return cancelOnException;
		}

		/**
		 * 周期任务异常后自动取消。
		 */
		public TimerTask cancelOnException(
				boolean value
		) {

			this.cancelOnException =
					value;

			return this;
		}

		@Override
		public String toString() {

			return "TimerTask{" +
					"id=" + id +
					", state=" + state.get() +
					", deadline=" + deadline +
					", period=" + periodNanos +
					", fixedRate=" + fixedRate +
					", level=" + level +
					'}';
		}
	}

	/* ============================================================
	 * Metrics
	 * ============================================================ */

	public static final class MetricsSnapshot {

		public final long scheduled;

		public final long cancelled;

		public final long executed;

		public final long failed;

		public final long rejected;

		public final long periodicExecuted;

		private MetricsSnapshot(
				long scheduled,
				long cancelled,
				long executed,
				long failed,
				long rejected,
				long periodicExecuted
		) {

			this.scheduled =
					scheduled;

			this.cancelled =
					cancelled;

			this.executed =
					executed;

			this.failed =
					failed;

			this.rejected =
					rejected;

			this.periodicExecuted =
					periodicExecuted;
		}

		@Override
		public String toString() {

			return "MetricsSnapshot{" +
					"scheduled=" +
					scheduled +
					", cancelled=" +
					cancelled +
					", executed=" +
					executed +
					", failed=" +
					failed +
					", rejected=" +
					rejected +
					", periodicExecuted=" +
					periodicExecuted +
					'}';
		}
	}

	private static final class Metrics {

		final AtomicLong scheduled =
				new AtomicLong();

		final AtomicLong cancelled =
				new AtomicLong();

		final AtomicLong executed =
				new AtomicLong();

		final AtomicLong failed =
				new AtomicLong();

		final AtomicLong rejected =
				new AtomicLong();

		final AtomicLong periodicExecuted =
				new AtomicLong();

		MetricsSnapshot snapshot() {

			return new MetricsSnapshot(
					scheduled.get(),
					cancelled.get(),
					executed.get(),
					failed.get(),
					rejected.get(),
					periodicExecuted.get()
			);
		}
	}

	/* ============================================================
	 * Close
	 * ============================================================ */

	@Override
	public void close() {
		shutdown();
	}
}