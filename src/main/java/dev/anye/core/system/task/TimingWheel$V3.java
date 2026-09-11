package dev.anye.core.system.task;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * 高性能分层时间轮。
 *
 * <p>
 * 核心设计：
 *
 * <pre>
 *                 schedule()
 *                     │
 *                     ▼
 *          ConcurrentLinkedQueue
 *                     │
 *                     ▼
 *               Timing Worker
 *                     │
 *          ┌──────────┴──────────┐
 *          ▼                     ▼
 *      时间轮调度             Executor
 *          │                     │
 *          ▼                     ▼
 *       Bucket               Runnable
 * </pre>
 *
 * <p>
 * 特点：
 *
 * <ul>
 *     <li>MPSC 任务提交队列</li>
 *     <li>LockSupport park/unpark</li>
 *     <li>单 Worker 操作时间轮</li>
 *     <li>四级时间轮</li>
 *     <li>O(1) 平均添加任务</li>
 *     <li>O(1) 取消任务</li>
 *     <li>一次性任务</li>
 *     <li>Fixed Rate</li>
 *     <li>Fixed Delay</li>
 *     <li>任务状态 CAS</li>
 *     <li>调度与任务执行分离</li>
 * </ul>
 *
 * <p>
 * 注意：
 *
 * <ul>
 *     <li>时间轮负责什么时候执行</li>
 *     <li>Executor 负责执行 Runnable</li>
 * </ul>
 *
 *TimingWheel wheel =
 *         TimingWheel.builder()
 *                 .tick(1, TimeUnit.MILLISECONDS)
 *                 .wheelSize(512)
 *                 .levels(4)
 *                 .executor(executor)
 *                 .threadName("Core-Scheduler")
 *                 .build();
 *
 *
 * ExecutorService executor =
 *         Executors.newFixedThreadPool(
 *                 Runtime.getRuntime().availableProcessors()
 *         );
 *
 * TimingWheel wheel =
 *         new TimingWheel(
 *                 10,
 *                 TimeUnit.MILLISECONDS,
 *                 512,
 *                 executor
 *         );
 */
public final class TimingWheel$V3 implements AutoCloseable {

	/*
	 * ============================================================
	 * 状态
	 * ============================================================
	 */

	private static final int NEW = 0;

	private static final int SCHEDULED = 1;

	private static final int RUNNING = 2;

	private static final int CANCELLED = 3;

	private static final int DONE = 4;

	/*
	 * ============================================================
	 * 默认配置
	 * ============================================================
	 */

	/**
	 * 默认 Tick：
	 *
	 * 10ms
	 */
	public static final long DEFAULT_TICK =
			10;

	public static final TimeUnit DEFAULT_TICK_UNIT =
			TimeUnit.MILLISECONDS;

	/**
	 * 每一级 512 个槽。
	 */
	public static final int DEFAULT_WHEEL_SIZE =
			512;

	/*
	 * ============================================================
	 * Configuration
	 * ============================================================
	 */

	/**
	 * Level 0 tick。
	 */
	private final long tickNanos;

	/**
	 * 每一级槽位数量。
	 */
	private final int wheelSize;

	/**
	 * wheelSize - 1。
	 */
	private final int mask;

	/**
	 * 时间轮起始时间。
	 */
	private final long startTime;

	/**
	 * 各级时间轮。
	 */
	private final WheelLevel[] levels;

	/**
	 * Worker。
	 */
	private final Thread worker;

	/**
	 * 用户任务执行器。
	 */
	private final Executor executor;

	/**
	 * 是否由本类创建 Executor。
	 *
	 * 当前版本默认 false。
	 */
	private final boolean ownsExecutor;

	/*
	 * ============================================================
	 * Queues
	 * ============================================================
	 */

	/**
	 * MPSC：
	 *
	 * Multiple Producer
	 * Single Consumer
	 *
	 * 所有外部线程：
	 *
	 * schedule()
	 * cancel()
	 *
	 * 都不直接操作 Bucket。
	 */
	private final ConcurrentLinkedQueue<TimerTask> pending =
			new ConcurrentLinkedQueue<>();

	/*
	 * ============================================================
	 * State
	 * ============================================================
	 */

	private final AtomicBoolean running =
			new AtomicBoolean(true);

	/**
	 * 任务数量。
	 */
	private final AtomicInteger taskCount =
			new AtomicInteger();

	/**
	 * Task ID。
	 */
	private final AtomicLong taskId =
			new AtomicLong();

	/**
	 * Worker 当前 Tick。
	 *
	 * 只允许 Worker 修改。
	 */
	private long currentTick;

	/**
	 * Worker 是否正在运行。
	 */
	private volatile boolean workerStarted;

	/*
	 * ============================================================
	 * Constructor
	 * ============================================================
	 */

	/**
	 * 默认构造。
	 *
	 * <pre>
	 * tick = 10ms
	 * wheel = 512
	 * </pre>
	 */
	public TimingWheel$V3() {

		this(
				DEFAULT_TICK,
				DEFAULT_TICK_UNIT,
				DEFAULT_WHEEL_SIZE,
				Runnable::run,
				"TimingWheel-Worker"
		);
	}

	/**
	 * 指定 Executor。
	 */
	public TimingWheel$V3(
			long tick,
			TimeUnit unit,
			int wheelSize,
			Executor executor
	) {

		this(
				tick,
				unit,
				wheelSize,
				executor,
				"TimingWheel-Worker"
		);
	}

	/**
	 * 完整构造器。
	 */
	public TimingWheel$V3(
			long tick,
			TimeUnit unit,
			int wheelSize,
			Executor executor,
			String threadName
	) {

		Objects.requireNonNull(
				unit,
				"unit"
		);

		Objects.requireNonNull(
				executor,
				"executor"
		);

		Objects.requireNonNull(
				threadName,
				"threadName"
		);

		if (tick <= 0) {
			throw new IllegalArgumentException(
					"tick must be > 0"
			);
		}

		if (wheelSize <= 0 ||
				(wheelSize & (wheelSize - 1)) != 0) {

			throw new IllegalArgumentException(
					"wheelSize must be power of 2"
			);
		}

		long tickNanos =
				unit.toNanos(tick);

		if (tickNanos <= 0) {
			throw new IllegalArgumentException(
					"tick is too small"
			);
		}

		this.tickNanos = tickNanos;

		this.wheelSize = wheelSize;

		this.mask = wheelSize - 1;

		this.executor = executor;

		this.ownsExecutor = false;

		this.startTime =
				System.nanoTime();

		/*
		 * 创建四级时间轮。
		 *
		 * L0:
		 *   10ms
		 *
		 * L1:
		 *   10ms × 512
		 *
		 * L2:
		 *   10ms × 512²
		 *
		 * L3:
		 *   10ms × 512³
		 */
		this.levels =
				new WheelLevel[4];

		long duration =
				tickNanos;

		for (int i = 0;
			 i < levels.length;
			 i++) {

			levels[i] =
					new WheelLevel(
							duration,
							wheelSize
					);

			/*
			 * 防止 duration 溢出。
			 */
			if (
					i < levels.length - 1
							&& duration
							> Long.MAX_VALUE / wheelSize
			) {

				throw new IllegalArgumentException(
						"Timing wheel duration overflow"
				);
			}

			duration *= wheelSize;
		}

		this.currentTick = 0;

		this.worker =
				new Thread(
						this::workerLoop,
						threadName
				);

		this.worker.setDaemon(true);

		this.worker.start();
	}

	/*
	 * ============================================================
	 * Public API
	 * ============================================================
	 */

	/**
	 * 延迟执行。
	 *
	 * @param task 任务
	 * @param delay 延迟
	 * @param unit 时间单位
	 */
	public TimerTask schedule(
			Runnable task,
			long delay,
			TimeUnit unit
	) {

		Objects.requireNonNull(
				task,
				"task"
		);

		Objects.requireNonNull(
				unit,
				"unit"
		);

		checkRunning();

		long delayNanos =
				unit.toNanos(
						Math.max(
								0,
								delay
						)
				);

		TimerTask timerTask =
				new TimerTask(
						this,
						taskId.incrementAndGet(),
						task
				);

		timerTask.deadline =
				safeAdd(
						System.nanoTime(),
						delayNanos
				);

		timerTask.state.set(
				SCHEDULED
		);

		taskCount.incrementAndGet();

		pending.offer(timerTask);

		/*
		 * 不使用 interrupt。
		 */
		LockSupport.unpark(worker);

		return timerTask;
	}

	/**
	 * Fixed Rate。
	 *
	 * <pre>
	 * t = 0
	 * t = period
	 * t = period * 2
	 * t = period * 3
	 * </pre>
	 */
	public TimerTask scheduleAtFixedRate(
			Runnable task,
			long initialDelay,
			long period,
			TimeUnit unit
	) {

		Objects.requireNonNull(
				task,
				"task"
		);

		Objects.requireNonNull(
				unit,
				"unit"
		);

		checkRunning();

		if (period <= 0) {
			throw new IllegalArgumentException(
					"period must be > 0"
			);
		}

		long initialNanos =
				unit.toNanos(
						Math.max(
								0,
								initialDelay
						)
				);

		long periodNanos =
				unit.toNanos(period);

		TimerTask timerTask =
				new TimerTask(
						this,
						taskId.incrementAndGet(),
						task
				);

		timerTask.periodNanos =
				periodNanos;

		timerTask.fixedRate = true;

		timerTask.deadline =
				safeAdd(
						System.nanoTime(),
						initialNanos
				);

		timerTask.state.set(
				SCHEDULED
		);

		taskCount.incrementAndGet();

		pending.offer(timerTask);

		LockSupport.unpark(worker);

		return timerTask;
	}

	/**
	 * Fixed Delay。
	 *
	 * <pre>
	 * execute
	 *   ↓
	 * delay
	 *   ↓
	 * execute
	 *   ↓
	 * delay
	 * </pre>
	 */
	public TimerTask scheduleWithFixedDelay(
			Runnable task,
			long initialDelay,
			long delay,
			TimeUnit unit
	) {

		Objects.requireNonNull(
				task,
				"task"
		);

		Objects.requireNonNull(
				unit,
				"unit"
		);

		checkRunning();

		if (delay <= 0) {
			throw new IllegalArgumentException(
					"delay must be > 0"
			);
		}

		long initialNanos =
				unit.toNanos(
						Math.max(
								0,
								initialDelay
						)
				);

		long delayNanos =
				unit.toNanos(delay);

		TimerTask timerTask =
				new TimerTask(
						this,
						taskId.incrementAndGet(),
						task
				);

		timerTask.periodNanos =
				delayNanos;

		timerTask.fixedRate = false;

		timerTask.deadline =
				safeAdd(
						System.nanoTime(),
						initialNanos
				);

		timerTask.state.set(
				SCHEDULED
		);

		taskCount.incrementAndGet();

		pending.offer(timerTask);

		LockSupport.unpark(worker);

		return timerTask;
	}

	/**
	 * 当前任务数量。
	 */
	public int size() {
		return taskCount.get();
	}

	/**
	 * 是否运行。
	 */
	public boolean isRunning() {
		return running.get();
	}

	/**
	 * 当前 Worker Tick。
	 */
	public long currentTick() {
		return currentTick;
	}

	/**
	 * 停止调度器。
	 *
	 * <p>
	 * 已经提交给 Executor 的任务不会被强制中断。
	 */
	public void shutdown() {

		if (!running.compareAndSet(
				true,
				false
		)) {

			return;
		}

		/*
		 * 唤醒 Worker。
		 */
		LockSupport.unpark(worker);
	}

	/**
	 * 停止并等待 Worker。
	 */
	public void shutdownAndWait()
			throws InterruptedException {

		shutdown();

		worker.join();
	}

	@Override
	public void close() {
		shutdown();
	}

	/*
	 * ============================================================
	 * Worker
	 * ============================================================
	 */

	private void workerLoop() {

		workerStarted = true;

		while (running.get()) {

			/*
			 * ① 处理外部提交。
			 */
			drainPending();

			/*
			 * ② 处理已经过去的 tick。
			 */
			long now =
					System.nanoTime();

			long targetTick =
					elapsedToTick(now);

			/*
			 * 防止系统暂停 / STW 后一次性
			 * 执行无限多 tick。
			 *
			 * 这里最多补 10000 个 tick。
			 */
			long delta =
					targetTick
							- currentTick;

			if (delta > 10_000) {

				/*
				 * 大幅落后时直接跳到当前时间附近。
				 *
				 * 不能直接执行所有过去 tick，
				 * 否则 JVM 从长时间 sleep / suspend
				 * 恢复后可能产生巨大的 CPU 峰值。
				 */
				currentTick =
						targetTick - 1;
			}

			while (
					currentTick < targetTick
							&& running.get()
			) {

				currentTick++;

				advance(
						currentTick
				);
			}

			/*
			 * ③ 计算下一次 tick。
			 */
			long nextTickTime =
					tickDeadline(
							currentTick + 1
					);

			long wait =
					nextTickTime
							- System.nanoTime();

			if (wait > 0) {

				/*
				 * 如果 pending 非空，
				 * 不需要等待。
				 */
				if (pending.isEmpty()) {

					LockSupport.parkNanos(
							this,
							wait
					);
				}

			} else {

				Thread.yield();
			}
		}

		/*
		 * shutdown 后清理。
		 */
		cleanup();
	}

	/**
	 * 将外部任务放入时间轮。
	 *
	 * <p>
	 * 只有 Worker 调用。
	 */
	private void drainPending() {

		TimerTask task;

		while (
				(task = pending.poll())
						!= null
		) {

			/*
			 * 已经取消。
			 */
			if (
					task.state.get()
							== CANCELLED
			) {

				finishCancelled(
						task
				);

				continue;
			}

			/*
			 * 可能在排队期间已经 shutdown。
			 */
			if (!running.get()) {

				cancelInternal(task);

				continue;
			}

			placeTask(task);
		}
	}

	/*
	 * ============================================================
	 * Time Wheel
	 * ============================================================
	 */

	/**
	 * 推进一个 tick。
	 */
	private void advance(
			long tick
	) {

		/*
		 * L0 当前槽。
		 */
		int index =
				(int) (
						tick & mask
				);

		Bucket bucket =
				levels[0]
						.buckets[index];

		expireBucket(
				bucket
		);

		/*
		 * L0 每转一圈，
		 * 向 L1 级联。
		 */
		if (
				(tick & mask) == 0
		) {

			cascade(
					1,
					tick
			);
		}
	}

	/**
	 * 高级时间轮级联。
	 */
	private void cascade(
			int level,
			long lowerTick
	) {

		if (
				level >= levels.length
		) {

			return;
		}

		WheelLevel wheel =
				levels[level];

		/*
		 * lowerTick 是 L0 tick。
		 *
		 * 转换到当前 level 的 tick。
		 */
		long levelTick =
				ticksForLevel(
						lowerTick,
						level
				);

		int index =
				(int) (
						levelTick & mask
				);

		Bucket bucket =
				wheel.buckets[index];

		TimerTask task =
				bucket.head;

		while (task != null) {

			TimerTask next =
					task.next;

			bucket.remove(task);

			if (
					task.state.get()
							== CANCELLED
			) {

				finishCancelled(
						task
				);

			} else {

				/*
				 * 重新计算等级。
				 */
				placeTask(task);
			}

			task = next;
		}

		/*
		 * 当前 level 也完成一圈，
		 * 继续向上级联。
		 */
		if (
				(levelTick & mask) == 0
		) {

			cascade(
					level + 1,
					levelTick
			);
		}
	}

	/**
	 * 将任务放入正确的层。
	 */
	private void placeTask(
			TimerTask task
	) {

		if (
				task.state.get()
						== CANCELLED
		) {

			finishCancelled(
					task
			);

			return;
		}

		long now =
				System.nanoTime();

		long deadline =
				task.deadline;

		/*
		 * 已经到期。
		 */
		if (
				deadline <= now
		) {

			executeTask(
					task
			);

			return;
		}

		long delay =
				deadline - now;

		int level =
				selectLevel(
						delay
				);

		WheelLevel wheel =
				levels[level];

		/*
		 * 根据 startTime 计算目标 tick。
		 *
		 * 注意：
		 *
		 * 不能直接：
		 *
		 * deadline / duration
		 *
		 * 必须使用：
		 *
		 * (deadline - startTime)
		 *
		 * 保证各级时间轮边界一致。
		 */
		long elapsed =
				deadline - startTime;

		if (elapsed <= 0) {

			executeTask(
					task
			);

			return;
		}

		long targetTick =
				ceilDiv(
						elapsed,
						wheel.duration
				);

		long currentLevelTick =
				currentLevelTick(
						level
				);

		/*
		 * 不允许放入当前已经处理过的槽。
		 */
		if (
				targetTick
						<= currentLevelTick
		) {

			targetTick =
					currentLevelTick + 1;
		}

		int index =
				(int) (
						targetTick & mask
				);

		Bucket bucket =
				wheel.buckets[index];

		bucket.add(task);

		task.bucket = bucket;

		task.level = level;
	}

	/**
	 * 根据剩余延迟选择时间轮等级。
	 */
	private int selectLevel(
			long delay
	) {

		for (
				int i = 0;
				i < levels.length;
				i++
		) {

			WheelLevel level =
					levels[i];

			long range =
					safeMultiply(
							level.duration,
							wheelSize
					);

			if (
					delay < range
			) {

				return i;
			}
		}

		/*
		 * 超过最大覆盖范围。
		 *
		 * 放到最高级。
		 */
		return levels.length - 1;
	}

	/**
	 * 获取某一级当前 tick。
	 */
	private long currentLevelTick(
			int level
	) {

		long elapsed =
				currentTick * tickNanos;

		return elapsed
				/ levels[level].duration;
	}

	/**
	 * 当前 Bucket 到期处理。
	 */
	private void expireBucket(
			Bucket bucket
	) {

		TimerTask task =
				bucket.head;

		while (task != null) {

			TimerTask next =
					task.next;

			bucket.remove(task);

			task.bucket = null;

			/*
			 * 已取消。
			 */
			if (
					task.state.get()
							== CANCELLED
			) {

				finishCancelled(
						task
				);

				task = next;

				continue;
			}

			long now =
					System.nanoTime();

			/*
			 * 因为时间轮是离散 tick，
			 * 任务可能只是进入了候选槽，
			 * 但 deadline 还没有真正到达。
			 */
			if (
					task.deadline > now
			) {

				placeTask(task);

			} else {

				executeTask(task);
			}

			task = next;
		}
	}

	/*
	 * ============================================================
	 * Execution
	 * ============================================================
	 */

	/**
	 * 调度任务执行。
	 */
	private void executeTask(
			TimerTask task
	) {

		/*
		 * SCHEDULED -> RUNNING
		 *
		 * 如果其他线程已经 cancel，
		 * CAS 失败。
		 */
		if (
				!task.state.compareAndSet(
						SCHEDULED,
						RUNNING
				)
		) {

			if (
					task.state.get()
							== CANCELLED
			) {

				finishCancelled(task);
			}

			return;
		}

		try {

			executor.execute(
					() -> runTask(task)
			);

		} catch (RejectedExecutionException e) {

			/*
			 * Executor 拒绝。
			 */
			if (
					task.state.compareAndSet(
							RUNNING,
							DONE
					)
			) {

				decrementTaskCount(
						task
				);
			}

			throw e;

		} catch (Throwable throwable) {

			handleExecutorFailure(
					task,
					throwable
			);
		}
	}

	/**
	 * 真正执行 Runnable。
	 */
	private void runTask(
			TimerTask task
	) {

		try {

			/*
			 * cancel() 在 RUNNING 状态下：
			 *
			 * 默认采用：
			 *
			 * 不打断当前执行
			 *
			 * 因此这里仍然可以执行。
			 */
			if (
					!task.cancelledBeforeRun
							&& runningOrPeriodic(task)
			) {

				task.runnable.run();
			}

		} catch (Throwable throwable) {

			handleTaskException(
					task,
					throwable
			);

		} finally {

			afterTask(task);
		}
	}

	/**
	 * 判断是否允许运行。
	 */
	private boolean runningOrPeriodic(
			TimerTask task
	) {

		int state =
				task.state.get();

		return state == RUNNING;
	}

	/**
	 * Runnable 执行完成。
	 */
	private void afterTask(
			TimerTask task
	) {

		/*
		 * 周期任务。
		 */
		if (
				task.periodNanos > 0
						&& running.get()
						&& task.state.compareAndSet(
						RUNNING,
						SCHEDULED
				)
		) {

			if (task.fixedRate) {

				/*
				 * Fixed Rate：
				 *
				 * deadline += period
				 */
				task.deadline =
						safeAdd(
								task.deadline,
								task.periodNanos
						);

				/*
				 * 如果因为任务执行太慢，
				 * 已经落后很多周期，
				 * 跳过已经错过的时间点。
				 */
				long now =
						System.nanoTime();

				if (
						task.deadline <= now
				) {

					long behind =
							now
									- task.deadline;

					long missed =
							behind
									/ task.periodNanos
									+ 1;

					task.deadline =
							safeAdd(
									task.deadline,
									safeMultiply(
											missed,
											task.periodNanos
									)
							);
				}

			} else {

				/*
				 * Fixed Delay：
				 *
				 * 从本次任务结束后重新计算。
				 */
				task.deadline =
						safeAdd(
								System.nanoTime(),
								task.periodNanos
						);
			}

			pending.offer(task);

			LockSupport.unpark(worker);

			return;
		}

		/*
		 * 一次性任务 / 已取消 / shutdown。
		 */
		if (
				task.state.compareAndSet(
						RUNNING,
						DONE
				)
		) {

			decrementTaskCount(
					task
			);
		}
	}

	/**
	 * Executor 本身出现问题。
	 */
	private void handleExecutorFailure(
			TimerTask task,
			Throwable throwable
	) {

		if (
				task.state.compareAndSet(
						RUNNING,
						DONE
				)
		) {

			decrementTaskCount(
					task
			);
		}

		throwable.printStackTrace();
	}

	/**
	 * 用户 Runnable 异常。
	 */
	private void handleTaskException(
			TimerTask task,
			Throwable throwable
	) {

		Thread thread =
				Thread.currentThread();

		Thread.UncaughtExceptionHandler handler =
				thread.getUncaughtExceptionHandler();

		if (handler != null) {

			handler.uncaughtException(
					thread,
					throwable
			);

		} else {

			throwable.printStackTrace();
		}
	}

	/*
	 * ============================================================
	 * Cancellation
	 * ============================================================
	 */

	/**
	 * 从外部取消。
	 */
	private boolean cancel(
			TimerTask task
	) {

		for (;;) {

			int state =
					task.state.get();

			if (
					state == CANCELLED
							|| state == DONE
			) {

				return false;
			}

			/*
			 * NEW / SCHEDULED
			 */
			if (
					state == NEW
							|| state == SCHEDULED
			) {

				if (
						task.state.compareAndSet(
								state,
								CANCELLED
						)
				) {

					finishCancelled(
							task
					);

					return true;
				}

				continue;
			}

			/*
			 * RUNNING：
			 *
			 * 不强制 interrupt。
			 */
			if (state == RUNNING) {

				if (
						task.state.compareAndSet(
								RUNNING,
								CANCELLED
						)
				) {

					task.cancelledBeforeRun =
							true;

					return true;
				}

				continue;
			}
		}
	}

	/**
	 * Worker 内部取消。
	 */
	private void cancelInternal(
			TimerTask task
	) {

		int state =
				task.state.get();

		if (
				state == DONE
						|| state == CANCELLED
		) {

			finishCancelled(task);

			return;
		}

		if (
				task.state.compareAndSet(
						state,
						CANCELLED
				)
		) {

			finishCancelled(task);
		}
	}

	/**
	 * 完成取消。
	 */
	private void finishCancelled(
			TimerTask task
	) {

		decrementTaskCount(
				task
		);
	}

	/**
	 * 任务计数只减少一次。
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

	/*
	 * ============================================================
	 * Shutdown
	 * ============================================================
	 */

	private void cleanup() {

		TimerTask task;

		/*
		 * 清理 pending。
		 */
		while (
				(task = pending.poll())
						!= null
		) {

			cancelInternal(task);
		}

		/*
		 * 清理所有 Bucket。
		 */
		for (
				WheelLevel level :
				levels
		) {

			for (
					Bucket bucket :
					level.buckets
			) {

				task =
						bucket.head;

				while (task != null) {

					TimerTask next =
							task.next;

					bucket.remove(task);

					cancelInternal(task);

					task = next;
				}
			}
		}

		taskCount.set(0);
	}

	/*
	 * ============================================================
	 * Utility
	 * ============================================================
	 */

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

	/**
	 * Tick deadline。
	 */
	private long tickDeadline(
			long tick
	) {

		long offset =
				safeMultiply(
						tick,
						tickNanos
				);

		return safeAdd(
				startTime,
				offset
		);
	}

	/**
	 * level Tick。
	 */
	private long ticksForLevel(
			long lowerTick,
			int level
	) {

		long elapsed =
				safeMultiply(
						lowerTick,
						tickNanos
				);

		return elapsed
				/ levels[level].duration;
	}

	private static long ceilDiv(
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
				b > 0
						&& a > Long.MAX_VALUE - b
		) {

			return Long.MAX_VALUE;
		}

		if (
				b < 0
						&& a < Long.MIN_VALUE - b
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

		if (
				a > 0
						&& b > 0
						&& a > Long.MAX_VALUE / b
		) {

			return Long.MAX_VALUE;
		}

		if (
				a < 0
						&& b < 0
						&& a < Long.MAX_VALUE / b
		) {

			return Long.MAX_VALUE;
		}

		if (
				a > 0
						&& b < 0
						&& b < Long.MIN_VALUE / a
		) {

			return Long.MIN_VALUE;
		}

		if (
				a < 0
						&& b > 0
						&& a < Long.MIN_VALUE / b
		) {

			return Long.MIN_VALUE;
		}

		return a * b;
	}

	private void checkRunning() {

		if (!running.get()) {

			throw new RejectedExecutionException(
					"TimingWheel is shutdown"
			);
		}
	}

	/*
	 * ============================================================
	 * WheelLevel
	 * ============================================================
	 */

	private static final class WheelLevel {

		/**
		 * 当前层一个 tick 的时间。
		 */
		final long duration;

		/**
		 * Bucket。
		 */
		final Bucket[] buckets;

		WheelLevel(
				long duration,
				int size
		) {

			this.duration = duration;

			this.buckets =
					new Bucket[size];

			for (
					int i = 0;
					i < size;
					i++
			) {

				buckets[i] =
						new Bucket();
			}
		}
	}

	/*
	 * ============================================================
	 * Bucket
	 * ============================================================
	 */

	/**
	 * 时间轮槽。
	 *
	 * <p>
	 * 非线程安全。
	 *
	 * <p>
	 * 重要：
	 *
	 * <pre>
	 * 只有 Worker 可以修改链表。
	 * </pre>
	 *
	 * 外部线程取消任务时，
	 * 不会直接 remove。
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

			} else if (head == task) {

				head = next;
			}

			if (next != null) {

				next.prev = prev;

			} else if (tail == task) {

				tail = prev;
			}

			task.prev = null;
			task.next = null;

			if (
					task.bucket == this
			) {

				task.bucket = null;
			}
		}
	}

	/*
	 * ============================================================
	 * TimerTask
	 * ============================================================
	 */

	public static final class TimerTask {

		private final TimingWheel$V3 owner;

		private final long id;

		private final Runnable runnable;

		/**
		 * 状态。
		 */
		private final AtomicInteger state =
				new AtomicInteger(NEW);

		/**
		 * 防止 taskCount 重复减少。
		 */
		private final AtomicBoolean counted =
				new AtomicBoolean(true);

		/**
		 * 下一次 deadline。
		 */
		private volatile long deadline;

		/**
		 * 周期。
		 *
		 * 0 = 一次性任务。
		 */
		private volatile long periodNanos;

		/**
		 * Fixed Rate / Fixed Delay。
		 */
		private volatile boolean fixedRate;

		/**
		 * 是否在运行前被取消。
		 */
		private volatile boolean cancelledBeforeRun;

		/**
		 * 当前 Bucket。
		 *
		 * 只由 Worker 写入。
		 */
		private volatile Bucket bucket;

		/**
		 * 当前层。
		 */
		private volatile int level;

		/**
		 * 双向链表。
		 *
		 * Worker 独占。
		 */
		private TimerTask prev;

		private TimerTask next;

		private TimerTask(
				TimingWheel$V3 owner,
				long id,
				Runnable runnable
		) {

			this.owner = owner;

			this.id = id;

			this.runnable = runnable;
		}

		/**
		 * 取消。
		 */
		public boolean cancel() {

			return owner.cancel(this);
		}

		/**
		 * 是否取消。
		 */
		public boolean isCancelled() {

			return state.get()
					== CANCELLED;
		}

		/**
		 * 是否完成。
		 */
		public boolean isDone() {

			int state =
					this.state.get();

			return state == DONE
					|| state == CANCELLED;
		}

		/**
		 * 是否运行中。
		 */
		public boolean isRunning() {

			return state.get()
					== RUNNING;
		}

		/**
		 * 是否周期任务。
		 */
		public boolean isPeriodic() {

			return periodNanos > 0;
		}

		/**
		 * Task ID。
		 */
		public long getId() {

			return id;
		}

		/**
		 * deadline。
		 */
		public long getDeadlineNanos() {

			return deadline;
		}

		/**
		 * period。
		 */
		public long getPeriodNanos() {

			return periodNanos;
		}

		/**
		 * 当前层级。
		 */
		public int getLevel() {

			return level;
		}

		@Override
		public String toString() {

			return "TimerTask{" +
					"id=" + id +
					", state=" +
					state.get() +
					", deadline=" +
					deadline +
					", periodNanos=" +
					periodNanos +
					'}';
		}
	}
}