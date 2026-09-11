package dev.anye.core.system.task;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 高性能分层时间轮。
 *
 * <p>
 * 特点：
 *
 * <ul>
 *     <li>MPSC 任务提交队列</li>
 *     <li>单 Worker 管理时间轮</li>
 *     <li>四级时间轮</li>
 *     <li>无锁 Bucket 调度</li>
 *     <li>一次性任务</li>
 *     <li>固定频率任务</li>
 *     <li>固定延迟任务</li>
 *     <li>任务取消</li>
 *     <li>自定义 Executor</li>
 *     <li>支持大量定时任务</li>
 * </ul>
 *
 * <p>
 * 时间轮本身只负责：
 *
 * <pre>
 *     "什么时候执行"
 * </pre>
 *
 * 真正的 Runnable 默认交给 Executor 执行。
 */
public final class $_HashedTimingWheel implements AutoCloseable {

	/*
	 * ============================================================
	 * 默认配置
	 * ============================================================
	 */

	/**
	 * Level 0 tick。
	 *
	 * 10ms × 512 = 5.12s
	 */
	public static final long DEFAULT_TICK_NANOS =
			TimeUnit.MILLISECONDS.toNanos(10);

	/**
	 * 每一级槽位数量。
	 */
	public static final int DEFAULT_WHEEL_SIZE = 512;

	/**
	 * 默认 Worker 名称。
	 */
	public static final String DEFAULT_THREAD_NAME =
			"TimingWheel-Worker";

	/*
	 * ============================================================
	 * 时间轮层级
	 * ============================================================
	 */

	private final WheelLevel[] levels;

	/**
	 * 最低一级 tick。
	 */
	private final long tickNanos;

	/**
	 * 每一级槽位数量。
	 */
	private final int wheelSize;

	/**
	 * mask。
	 */
	private final int mask;

	/**
	 * Worker。
	 */
	private final Thread worker;

	/**
	 * 实际执行任务的 Executor。
	 */
	private final Executor executor;

	/**
	 * MPSC 提交队列。
	 */
	private final ConcurrentLinkedQueue<TimerTask> pending =
			new ConcurrentLinkedQueue<>();

	/**
	 * Worker 是否运行。
	 */
	private final AtomicBoolean running =
			new AtomicBoolean(true);

	/**
	 * 当前任务数量。
	 */
	private final AtomicInteger taskCount =
			new AtomicInteger();

	/**
	 * 任务 ID。
	 */
	private final AtomicLong taskId =
			new AtomicLong();

	/**
	 * 时间轮起始时间。
	 */
	private final long startTime;

	/**
	 * 当前 tick。
	 *
	 * 只允许 Worker 修改。
	 */
	private long currentTick;

	/**
	 * 创建默认时间轮。
	 */
	public $_HashedTimingWheel() {
		this(
				DEFAULT_TICK_NANOS,
				TimeUnit.NANOSECONDS,
				DEFAULT_WHEEL_SIZE,
				new ThreadPoolExecutor(
						0,
						Integer.MAX_VALUE,
						60L,
						TimeUnit.SECONDS,
						new SynchronousQueue<>(),
						r -> {
							Thread thread =
									new Thread(
											r,
											"TimingWheel-Task"
									);

							thread.setDaemon(true);

							return thread;
						}
				),
				DEFAULT_THREAD_NAME
		);
	}

	/**
	 * 使用指定 Executor。
	 *
	 * @param tickDuration tick 时间
	 * @param unit         tick 单位
	 * @param wheelSize    槽位数量
	 * @param executor     任务执行器
	 */
	public $_HashedTimingWheel(
			long tickDuration,
			TimeUnit unit,
			int wheelSize,
			Executor executor
	) {
		this(
				tickDuration,
				unit,
				wheelSize,
				executor,
				DEFAULT_THREAD_NAME
		);
	}

	/**
	 * 完整构造器。
	 */
	public $_HashedTimingWheel(
			long tickDuration,
			TimeUnit unit,
			int wheelSize,
			Executor executor,
			String threadName
	) {

		Objects.requireNonNull(unit);
		Objects.requireNonNull(executor);
		Objects.requireNonNull(threadName);

		if (tickDuration <= 0) {
			throw new IllegalArgumentException(
					"tickDuration must be > 0"
			);
		}

		if (wheelSize <= 0 ||
				(wheelSize & (wheelSize - 1)) != 0) {

			throw new IllegalArgumentException(
					"wheelSize must be power of 2"
			);
		}

		long nanos =
				unit.toNanos(tickDuration);

		if (nanos <= 0) {
			throw new IllegalArgumentException(
					"tickDuration is too small"
			);
		}

		this.tickNanos = nanos;
		this.wheelSize = wheelSize;
		this.mask = wheelSize - 1;
		this.executor = executor;

		/*
		 * 创建四级时间轮。
		 */
		this.levels = new WheelLevel[4];

		long duration =
				tickNanos;

		for (int i = 0; i < levels.length; i++) {

			levels[i] =
					new WheelLevel(
							duration,
							wheelSize
					);

			duration *= wheelSize;
		}

		/*
		 * System.nanoTime() 作为单调时间源。
		 */
		this.startTime =
				System.nanoTime();

		this.currentTick = 0;

		this.worker =
				new Thread(
						this::run,
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
	 * 一次性任务。
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
				unit.toNanos(
						Math.max(0, delay)
				);

		TimerTask task =
				new TimerTask(
						this,
						taskId.incrementAndGet(),
						runnable
				);

		task.deadline =
				System.nanoTime()
						+ delayNanos;

		taskCount.incrementAndGet();

		/*
		 * 不直接操作时间轮。
		 */
		pending.offer(task);

		/*
		 * 唤醒 Worker。
		 */
		worker.interrupt();

		return task;
	}

	/**
	 * 固定频率。
	 *
	 * <p>
	 * 下一次执行时间：
	 *
	 * <pre>
	 * deadline += period
	 * </pre>
	 */
	public TimerTask scheduleAtFixedRate(
			Runnable runnable,
			long initialDelay,
			long period,
			TimeUnit unit
	) {

		Objects.requireNonNull(runnable);
		Objects.requireNonNull(unit);

		checkRunning();

		if (period <= 0) {
			throw new IllegalArgumentException(
					"period must be > 0"
			);
		}

		long periodNanos =
				unit.toNanos(period);

		TimerTask task =
				new TimerTask(
						this,
						taskId.incrementAndGet(),
						runnable
				);

		task.periodNanos =
				periodNanos;

		task.fixedRate = true;

		task.deadline =
				System.nanoTime()
						+ unit.toNanos(
						Math.max(
								0,
								initialDelay
						)
				);

		taskCount.incrementAndGet();

		pending.offer(task);

		worker.interrupt();

		return task;
	}

	/**
	 * 固定延迟。
	 *
	 * <p>
	 * 每次任务完成后重新计算下一次执行时间。
	 */
	public TimerTask scheduleWithFixedDelay(
			Runnable runnable,
			long initialDelay,
			long delay,
			TimeUnit unit
	) {

		Objects.requireNonNull(runnable);
		Objects.requireNonNull(unit);

		checkRunning();

		if (delay <= 0) {
			throw new IllegalArgumentException(
					"delay must be > 0"
			);
		}

		long delayNanos =
				unit.toNanos(delay);

		TimerTask task =
				new TimerTask(
						this,
						taskId.incrementAndGet(),
						runnable
				);

		task.periodNanos =
				delayNanos;

		task.fixedRate = false;

		task.deadline =
				System.nanoTime()
						+ unit.toNanos(
						Math.max(
								0,
								initialDelay
						)
				);

		taskCount.incrementAndGet();

		pending.offer(task);

		worker.interrupt();

		return task;
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
	 * 停止。
	 */
	public void shutdown() {

		if (!running.compareAndSet(true, false)) {
			return;
		}

		worker.interrupt();

		/*
		 * 清空等待队列。
		 */
		pending.clear();

		/*
		 * 清理所有时间轮。
		 */
		for (WheelLevel level : levels) {
			level.clear();
		}

		taskCount.set(0);
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

	private void run() {

		while (running.get()) {

			/*
			 * 先处理外部提交。
			 */
			drainPending();

			long now =
					System.nanoTime();

			long targetTick =
					elapsedToTick(now);

			/*
			 * 如果时间已经跨过多个 tick，
			 * 逐 tick 补偿。
			 */
			while (
					currentTick < targetTick
							&& running.get()
			) {

				currentTick++;

				advance(currentTick);
			}

			/*
			 * 计算下一次 tick。
			 */
			long nextDeadline =
					startTime
							+ (currentTick + 1)
							* tickNanos;

			long waitNanos =
					nextDeadline
							- System.nanoTime();

			if (waitNanos <= 0) {
				continue;
			}

			waitNanos(waitNanos);
		}
	}

	/**
	 * 将 pending 任务加入时间轮。
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

			if (task.cancelled.get()) {
				decrementTaskCount(task);
				continue;
			}

			placeTask(task);
		}
	}

	/**
	 * 时间轮前进。
	 */
	private void advance(long tick) {

		/*
		 * 低级时间轮。
		 */
		int index =
				(int) (tick & mask);

		Bucket bucket =
				levels[0].buckets[index];

		expireBucket(bucket);

		/*
		 * 每转完一级，
		 * 将高级时间轮任务降级。
		 */
		if ((tick & mask) == 0) {

			cascade(
					1,
					tick
			);
		}
	}

	/**
	 * 将高级时间轮任务降级。
	 */
	private void cascade(
			int level,
			long tick
	) {

		if (level >= levels.length) {
			return;
		}

		WheelLevel wheel =
				levels[level];

		long ticks =
				tick
						/ wheelSize;

		int index =
				(int) (ticks & mask);

		Bucket bucket =
				wheel.buckets[index];

		TimerTask task =
				bucket.head;

		while (task != null) {

			TimerTask next =
					task.next;

			bucket.remove(task);

			if (task.cancelled.get()) {

				decrementTaskCount(task);

			} else {

				/*
				 * 重新计算应该属于哪一级。
				 */
				placeTask(task);
			}

			task = next;
		}

		/*
		 * 当前层完成一圈后，
		 * 继续向更高级联。
		 */
		if ((ticks & mask) == 0) {

			cascade(
					level + 1,
					ticks
			);
		}
	}

	/**
	 * 放入正确的时间轮。
	 */
	private void placeTask(
			TimerTask task
	) {

		long now =
				System.nanoTime();

		long deadline =
				task.deadline;

		long delay =
				deadline - now;

		if (delay <= 0) {

			/*
			 * 已到期。
			 */
			executeTask(task);

			return;
		}

		/*
		 * 根据剩余时间选择层级。
		 */
		int level =
				selectLevel(delay);

		WheelLevel wheel =
				levels[level];

		long ticks =
				deadline / wheel.duration;

		/*
		 * 防止任务被放入当前已经过去的槽。
		 */
		long current =
				currentTickForLevel(level);

		if (ticks <= current) {
			ticks = current + 1;
		}

		int index =
				(int) (ticks & mask);

		wheel.buckets[index].add(task);

		task.level = level;
		task.bucket =
				wheel.buckets[index];
	}

	/**
	 * 选择层级。
	 */
	private int selectLevel(
			long delay
	) {

		for (int i = 0; i < levels.length; i++) {

			if (
					delay
							< levels[i].duration
							* wheelSize
			) {
				return i;
			}
		}

		/*
		 * 超过最大范围，
		 * 放入最高级。
		 */
		return levels.length - 1;
	}

	/**
	 * 当前层的 tick。
	 */
	private long currentTickForLevel(
			int level
	) {

		return currentTick
				* tickNanos
				/ levels[level].duration;
	}

	/**
	 * 执行 Bucket。
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

			if (task.cancelled.get()) {

				decrementTaskCount(task);

			} else {

				long now =
						System.nanoTime();

				/*
				 * 时间轮精度造成的提前进入。
				 */
				if (task.deadline > now) {

					placeTask(task);

				} else {

					executeTask(task);
				}
			}

			task = next;
		}
	}

	/*
	 * ============================================================
	 * Task Execution
	 * ============================================================
	 */

	private void executeTask(
			TimerTask task
	) {

		if (
				task.cancelled.get()
						|| !running.get()
		) {

			decrementTaskCount(task);
			return;
		}

		/*
		 * 任务提交给 Executor。
		 *
		 * 注意：
		 * Worker 不执行用户 Runnable。
		 */
		try {

			task.running.set(true);

			executor.execute(() -> {

				try {

					if (!task.cancelled.get()) {

						task.runnable.run();
					}

				} catch (Throwable throwable) {

					handleTaskException(
							task,
							throwable
					);

				} finally {

					task.running.set(false);

					afterExecution(task);
				}
			});

		} catch (Throwable throwable) {

			task.running.set(false);

			handleTaskException(
					task,
					throwable
			);

			finishTask(task);
		}
	}

	/**
	 * 任务执行完成。
	 */
	private void afterExecution(
			TimerTask task
	) {

		if (
				task.cancelled.get()
						|| !running.get()
		) {

			finishTask(task);
			return;
		}

		/*
		 * 一次性任务。
		 */
		if (task.periodNanos <= 0) {

			finishTask(task);
			return;
		}

		/*
		 * Fixed Rate。
		 */
		if (task.fixedRate) {

			task.deadline +=
					task.periodNanos;

		} else {

			/*
			 * Fixed Delay。
			 *
			 * 从任务完成时间开始计算。
			 */
			task.deadline =
					System.nanoTime()
							+ task.periodNanos;
		}

		/*
		 * 重新进入 pending。
		 *
		 * 注意：
		 * Runnable 所在线程不会直接操作 Bucket。
		 */
		pending.offer(task);

		worker.interrupt();
	}

	/**
	 * 完成任务。
	 */
	private void finishTask(
			TimerTask task
	) {

		if (
				task.finished
						.compareAndSet(
								false,
								true
						)
		) {

			decrementTaskCount(task);
		}
	}

	private void decrementTaskCount(
			TimerTask task
	) {

		/*
		 * 防止重复 decrement。
		 */
		if (
				task.counted.compareAndSet(
						true,
						false
				)
		) {

			taskCount.decrementAndGet();
		}
	}

	/**
	 * 任务异常。
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
	 * Timing
	 * ============================================================
	 */

	private long elapsedToTick(
			long now
	) {

		long elapsed =
				now - startTime;

		return elapsed / tickNanos;
	}

	private void waitNanos(
			long nanos
	) {

		try {

			long millis =
					TimeUnit.NANOSECONDS
							.toMillis(nanos);

			int extra =
					(int) (
							nanos
									- TimeUnit.MILLISECONDS
									.toNanos(millis)
					);

			if (millis > 0) {

				Thread.sleep(
						millis,
						extra
				);

			} else {

				Thread.yield();
			}

		} catch (InterruptedException ignored) {

			/*
			 * interrupt 只是用于：
			 *
			 * 1. 新任务到达
			 * 2. shutdown
			 *
			 * 下一轮循环会重新检查。
			 */
		}
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
	 * Wheel Level
	 * ============================================================
	 */

	private static final class WheelLevel {

		final long duration;

		final Bucket[] buckets;

		WheelLevel(
				long duration,
				int size
		) {

			this.duration = duration;

			this.buckets =
					new Bucket[size];

			for (int i = 0; i < size; i++) {

				buckets[i] =
						new Bucket();
			}
		}

		void clear() {

			for (Bucket bucket : buckets) {
				bucket.clear();
			}
		}
	}

	/*
	 * ============================================================
	 * Bucket
	 * ============================================================
	 */

	private static final class Bucket {

		TimerTask head;

		TimerTask tail;

		void add(
				TimerTask task
		) {

			task.bucket = this;

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

			if (task.bucket == this) {
				task.bucket = null;
			}
		}

		void clear() {

			TimerTask task = head;

			while (task != null) {

				TimerTask next =
						task.next;

				task.prev = null;
				task.next = null;
				task.bucket = null;

				task.cancelled.set(true);

				task = next;
			}

			head = null;
			tail = null;
		}
	}

	/*
	 * ============================================================
	 * TimerTask
	 * ============================================================
	 */

	public static final class TimerTask {

		private final $_HashedTimingWheel owner;

		private final long id;

		private final Runnable runnable;

		/**
		 * 下一次执行时间。
		 */
		private volatile long deadline;

		/**
		 * 周期。
		 */
		private volatile long periodNanos;

		/**
		 * 是否 fixed-rate。
		 */
		private volatile boolean fixedRate;

		/**
		 * 是否取消。
		 */
		private final AtomicBoolean cancelled =
				new AtomicBoolean();

		/**
		 * 是否执行中。
		 */
		private final AtomicBoolean running =
				new AtomicBoolean();

		/**
		 * 是否完成。
		 */
		private final AtomicBoolean finished =
				new AtomicBoolean();

		/**
		 * 是否计入 taskCount。
		 */
		private final AtomicBoolean counted =
				new AtomicBoolean(true);

		/**
		 * 所属 Bucket。
		 */
		private volatile Bucket bucket;

		/**
		 * 所属 level。
		 */
		private volatile int level;

		/**
		 * 双向链表。
		 *
		 * 只由 Worker 操作。
		 */
		private TimerTask prev;

		private TimerTask next;

		private TimerTask(
				$_HashedTimingWheel owner,
				long id,
				Runnable runnable
		) {

			this.owner = owner;
			this.id = id;
			this.runnable = runnable;
		}

		/**
		 * 取消任务。
		 *
		 * <p>
		 * 取消操作不会直接修改时间轮。
		 * Worker 在访问 Bucket 时会发现 cancelled。
		 *
		 * <p>
		 * 这样可以避免：
		 *
		 * <pre>
		 * schedule()
		 * cancel()
		 * Worker
		 * </pre>
		 *
		 * 多线程同时修改链表。
		 */
		public boolean cancel() {

			if (
					cancelled.compareAndSet(
							false,
							true
					)
			) {

				/*
				 * 如果尚未执行，
				 * taskCount 可以立即减少。
				 */
				if (
						!running.get()
								&& !finished.get()
				) {

					owner.decrementTaskCount(
							this
					);
				}

				return true;
			}

			return false;
		}

		/**
		 * 是否取消。
		 */
		public boolean isCancelled() {
			return cancelled.get();
		}

		/**
		 * 是否正在执行。
		 */
		public boolean isRunning() {
			return running.get();
		}

		/**
		 * 是否完成。
		 */
		public boolean isDone() {
			return finished.get();
		}

		/**
		 * 任务 ID。
		 */
		public long getId() {
			return id;
		}

		/**
		 * 下一次执行时间。
		 *
		 * @return System.nanoTime() 时间基准
		 */
		public long getDeadlineNanos() {
			return deadline;
		}

		/**
		 * 周期。
		 */
		public long getPeriodNanos() {
			return periodNanos;
		}

		/**
		 * 是否周期任务。
		 */
		public boolean isPeriodic() {
			return periodNanos > 0;
		}
	}
}