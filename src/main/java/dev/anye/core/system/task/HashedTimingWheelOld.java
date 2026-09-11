package dev.anye.core.system.task;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 高性能单层时间轮定时器。
 *
 * <p>适合大量定时任务，例如：</p>
 * <ul>
 *     <li>缓存过期</li>
 *     <li>网络连接超时</li>
 *     <li>游戏 Buff / Cooldown</li>
 *     <li>延迟任务</li>
 *     <li>大量周期任务</li>
 * </ul>
 *
 * <p>时间复杂度：</p>
 * <ul>
 *     <li>添加任务：O(1)</li>
 *     <li>取消任务：O(1)</li>
 *     <li>时间推进：O(n)，n 为当前槽中的任务数量</li>
 * </ul>
 */
public final class HashedTimingWheel implements AutoCloseable {

	/**
	 * 默认 tick 时间。
	 */
	public static final long DEFAULT_TICK_MILLIS = 10L;

	/**
	 * 默认槽位数量。
	 *
	 * 必须是 2 的幂。
	 */
	public static final int DEFAULT_WHEEL_SIZE = 512;

	/**
	 * 时间轮槽位。
	 */
	private final Bucket[] wheel;

	/**
	 * tick 时间，纳秒。
	 */
	private final long tickDuration;

	/**
	 * tick 时间，毫秒。
	 */
	private final long tickDurationMillis;

	/**
	 * 槽位数量。
	 */
	private final int wheelSize;

	/**
	 * wheelSize - 1。
	 *
	 * 当 wheelSize 为 2 的幂时：
	 *
	 * index = tick & mask
	 *
	 * 比取模更快。
	 */
	private final int mask;

	/**
	 * Worker 线程。
	 */
	private final Thread workerThread;

	/**
	 * 是否正在运行。
	 */
	private volatile boolean running = true;

	/**
	 * 当前 tick。
	 */
	private volatile long tick;

	/**
	 * 当前时间轮起始时间。
	 */
	private final long startTime;

	/**
	 * 当前任务数量。
	 */
	private final AtomicInteger taskCount = new AtomicInteger();

	/**
	 * 创建默认时间轮。
	 */
	public HashedTimingWheel() {
		this(
				DEFAULT_TICK_MILLIS,
				TimeUnit.MILLISECONDS,
				DEFAULT_WHEEL_SIZE,
				"Timing-Wheel"
		);
	}

	/**
	 * 创建时间轮。
	 *
	 * @param tickDuration tick 时间
	 * @param unit         时间单位
	 * @param wheelSize    槽位数量，必须为 2 的幂
	 */
	public HashedTimingWheel(
			long tickDuration,
			TimeUnit unit,
			int wheelSize
	) {
		this(
				tickDuration,
				unit,
				wheelSize,
				"Timing-Wheel"
		);
	}

	/**
	 * 创建时间轮。
	 *
	 * @param tickDuration tick 时间
	 * @param unit         时间单位
	 * @param wheelSize    槽位数量，必须为 2 的幂
	 * @param threadName   Worker 线程名称
	 */
	public HashedTimingWheel(
			long tickDuration,
			TimeUnit unit,
			int wheelSize,
			String threadName
	) {
		Objects.requireNonNull(unit, "unit");

		if (tickDuration <= 0) {
			throw new IllegalArgumentException(
					"tickDuration must be > 0"
			);
		}

		if (wheelSize <= 0) {
			throw new IllegalArgumentException(
					"wheelSize must be > 0"
			);
		}

		if ((wheelSize & (wheelSize - 1)) != 0) {
			throw new IllegalArgumentException(
					"wheelSize must be a power of two"
			);
		}

		this.tickDuration =
				unit.toNanos(tickDuration);

		this.tickDurationMillis =
				TimeUnit.NANOSECONDS.toMillis(
						this.tickDuration
				);

		if (this.tickDuration <= 0) {
			throw new IllegalArgumentException(
					"tickDuration is too small"
			);
		}

		this.wheelSize = wheelSize;
		this.mask = wheelSize - 1;

		this.wheel = new Bucket[wheelSize];

		for (int i = 0; i < wheelSize; i++) {
			wheel[i] = new Bucket();
		}

		this.startTime = System.nanoTime();

		this.workerThread =
				new Thread(
						this::run,
						threadName
				);

		this.workerThread.setDaemon(true);
		this.workerThread.start();
	}

	/**
	 * 延迟执行任务。
	 *
	 * @param task  任务
	 * @param delay 延迟
	 * @param unit  时间单位
	 *
	 * @return 任务句柄
	 */
	public TimerTask schedule(
			Runnable task,
			long delay,
			TimeUnit unit
	) {
		Objects.requireNonNull(task, "task");
		Objects.requireNonNull(unit, "unit");

		return scheduleNanos(
				task,
				unit.toNanos(delay)
		);
	}

	/**
	 * 固定延迟执行。
	 *
	 * <pre>
	 * 任务执行完成
	 *      ↓
	 * 等待 delay
	 *      ↓
	 * 下一次执行
	 * </pre>
	 */
	public TimerTask scheduleWithFixedDelay(
			Runnable task,
			long initialDelay,
			long delay,
			TimeUnit unit
	) {
		Objects.requireNonNull(task, "task");
		Objects.requireNonNull(unit, "unit");

		if (delay <= 0) {
			throw new IllegalArgumentException(
					"delay must be > 0"
			);
		}

		TimerTask timerTask =
				new TimerTask(
						this,
						task,
						unit.toNanos(delay),
						false
				);

		scheduleTask(
				timerTask,
				unit.toNanos(
						Math.max(0, initialDelay)
				)
		);

		return timerTask;
	}

	/**
	 * 固定频率执行。
	 *
	 * <p>
	 * 注意：
	 * 如果任务执行时间超过 period，
	 * 不会并发执行同一个任务。
	 * </p>
	 */
	public TimerTask scheduleAtFixedRate(
			Runnable task,
			long initialDelay,
			long period,
			TimeUnit unit
	) {
		Objects.requireNonNull(task, "task");
		Objects.requireNonNull(unit, "unit");

		if (period <= 0) {
			throw new IllegalArgumentException(
					"period must be > 0"
			);
		}

		long periodNanos =
				unit.toNanos(period);

		TimerTask timerTask =
				new TimerTask(
						this,
						task,
						periodNanos,
						true
				);

		timerTask.nextDeadline =
				System.nanoTime()
						+ unit.toNanos(
						Math.max(0, initialDelay)
				);

		addTask(timerTask);

		return timerTask;
	}

	/**
	 * 纳秒延迟任务。
	 */
	private TimerTask scheduleNanos(
			Runnable task,
			long delayNanos
	) {
		if (!running) {
			throw new IllegalStateException(
					"TimingWheel is shutdown"
			);
		}

		TimerTask timerTask =
				new TimerTask(
						this,
						task,
						0L,
						false
				);

		scheduleTask(
				timerTask,
				Math.max(
						0L,
						delayNanos
				)
		);

		return timerTask;
	}

	/**
	 * 调度任务。
	 */
	private void scheduleTask(
			TimerTask task,
			long delayNanos
	) {
		task.nextDeadline =
				System.nanoTime()
						+ delayNanos;

		addTask(task);
	}

	/**
	 * 添加任务到时间轮。
	 */
	private void addTask(
			TimerTask task
	) {
		if (!running || task.cancelled) {
			return;
		}

		long now =
				System.nanoTime();

		long delay =
				task.nextDeadline - now;

		if (delay < 0) {
			delay = 0;
		}

		/*
		 * 计算任务需要多少个 tick。
		 *
		 * 向上取整。
		 */
		long ticks =
				(delay + tickDuration - 1)
						/ tickDuration;

		if (ticks <= 0) {
			ticks = 1;
		}

		long currentTick =
				this.tick;

		long targetTick =
				currentTick + ticks;

		int index =
				(int) (targetTick & mask);

		long rounds =
				ticks / wheelSize;

		task.remainingRounds = rounds;

		wheel[index].add(task);

		taskCount.incrementAndGet();
	}

	/**
	 * Worker 主循环。
	 */
	private void run() {

		while (running) {

			long deadline =
					startTime
							+ (++tick)
							* tickDuration;

			waitForNextTick(deadline);

			if (!running) {
				break;
			}

			int index =
					(int) (tick & mask);

			wheel[index].expire(
					this,
					System.nanoTime()
			);
		}
	}

	/**
	 * 等待到下一个 tick。
	 */
	private void waitForNextTick(
			long deadline
	) {

		for (;;) {

			long currentTime =
					System.nanoTime();

			long remaining =
					deadline - currentTime;

			if (remaining <= 0) {
				return;
			}

			/*
			 * 剩余时间较长时使用 sleep。
			 */
			if (remaining > 1_000_000L) {

				try {

					long millis =
							remaining / 1_000_000L;

					int nanos =
							(int) (
									remaining
											% 1_000_000L
							);

					Thread.sleep(
							millis,
							nanos
					);

				} catch (
						InterruptedException ignored
				) {

					if (!running) {
						return;
					}
				}

			} else {

				/*
				 * 最后 1ms 使用 yield，
				 * 避免过度 sleep 导致精度下降。
				 */
				Thread.yield();
			}
		}
	}

	/**
	 * 当前任务数量。
	 */
	public int size() {
		return taskCount.get();
	}

	/**
	 * 是否正在运行。
	 */
	public boolean isRunning() {
		return running;
	}

	/**
	 * 停止时间轮。
	 */
	public void shutdown() {

		if (!running) {
			return;
		}

		running = false;

		workerThread.interrupt();

		for (Bucket bucket : wheel) {
			bucket.clear();
		}

		taskCount.set(0);
	}

	@Override
	public void close() {
		shutdown();
	}

	/**
	 * 时间轮槽位。
	 *
	 * 使用双向链表，
	 * 任务删除 O(1)。
	 */
	private static final class Bucket {

		private TimerTask head;
		private TimerTask tail;

		synchronized void add(
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

		synchronized void remove(
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
			task.bucket = null;
		}

		synchronized void expire(
				HashedTimingWheel wheel,
				long now
		) {

			TimerTask current =
					head;

			while (current != null) {

				TimerTask next =
						current.next;

				if (current.cancelled) {

					remove(current);

					wheel.taskCount
							.decrementAndGet();

				} else if (
						current.remainingRounds > 0
				) {

					current.remainingRounds--;

				} else {

					remove(current);

					wheel.taskCount
							.decrementAndGet();

					if (
							current.nextDeadline > now
					) {

						/*
						 * 因为 tick 向上取整，
						 * 有可能还没有真正到 deadline。
						 */
						wheel.addTask(current);

					} else {

						wheel.execute(current);
					}
				}

				current = next;
			}
		}

		synchronized void clear() {

			TimerTask current =
					head;

			while (current != null) {

				TimerTask next =
						current.next;

				current.prev = null;
				current.next = null;
				current.bucket = null;

				current = next;
			}

			head = null;
			tail = null;
		}
	}

	/**
	 * 执行任务。
	 */
	private void execute(
			TimerTask task
	) {

		if (task.cancelled) {
			return;
		}

		try {

			task.running = true;

			task.runnable.run();

		} catch (Throwable throwable) {

			/*
			 * 不让任务异常导致
			 * TimingWheel Worker 停止。
			 */
			uncaughtTaskException(
					task,
					throwable
			);

		} finally {

			task.running = false;
		}

		if (
				!task.cancelled
						&& task.periodNanos > 0
						&& running
		) {

			if (task.fixedRate) {

				/*
				 * 固定频率：
				 *
				 * nextDeadline += period
				 */
				task.nextDeadline +=
						task.periodNanos;

				/*
				 * 如果严重落后，
				 * 直接跳过已经错过的周期。
				 */
				long now =
						System.nanoTime();

				if (
						task.nextDeadline < now
				) {

					long missed =
							(now
									- task.nextDeadline)
									/ task.periodNanos
									+ 1;

					task.nextDeadline +=
							missed
									* task.periodNanos;
				}

				addTask(task);

			} else {

				/*
				 * 固定延迟：
				 *
				 * 任务结束后再开始计算 delay。
				 */
				scheduleTask(
						task,
						task.periodNanos
				);
			}
		}
	}

	/**
	 * 任务异常处理。
	 *
	 * 可以继承或修改这里，
	 * 接入自己的日志系统。
	 */
	private void uncaughtTaskException(
			TimerTask task,
			Throwable throwable
	) {

		System.err.println(
				"[TimingWheel] Task execution failed:"
		);

		throwable.printStackTrace();
	}

	/**
	 * 定时任务句柄。
	 */
	public static final class TimerTask {

		private final HashedTimingWheel wheel;

		private final Runnable runnable;

		/**
		 * 周期。
		 *
		 * 0 表示一次性任务。
		 */
		private final long periodNanos;

		/**
		 * 是否固定频率。
		 */
		private final boolean fixedRate;

		/**
		 * 下一次执行时间。
		 */
		private volatile long nextDeadline;

		/**
		 * 剩余圈数。
		 */
		private volatile long remainingRounds;

		/**
		 * 是否取消。
		 */
		private volatile boolean cancelled;

		/**
		 * 是否正在执行。
		 */
		private volatile boolean running;

		/**
		 * 所属 Bucket。
		 */
		private volatile Bucket bucket;

		/**
		 * 双向链表。
		 */
		private TimerTask prev;
		private TimerTask next;

		private TimerTask(
				HashedTimingWheel wheel,
				Runnable runnable,
				long periodNanos,
				boolean fixedRate
		) {

			this.wheel =
					wheel;

			this.runnable =
					runnable;

			this.periodNanos =
					periodNanos;

			this.fixedRate =
					fixedRate;
		}

		/**
		 * 取消任务。
		 *
		 * @return 是否成功取消
		 */
		public boolean cancel() {

			if (cancelled) {
				return false;
			}

			cancelled = true;

			Bucket currentBucket =
					bucket;

			if (currentBucket != null) {

				synchronized (currentBucket) {

					if (bucket == currentBucket) {

						currentBucket.remove(this);

						wheel.taskCount
								.decrementAndGet();
					}
				}
			}

			return true;
		}

		/**
		 * 是否已取消。
		 */
		public boolean isCancelled() {
			return cancelled;
		}

		/**
		 * 是否正在执行。
		 */
		public boolean isRunning() {
			return running;
		}

		/**
		 * 是否为周期任务。
		 */
		public boolean isPeriodic() {
			return periodNanos > 0;
		}

		/**
		 * 下一次执行时间。
		 */
		public long getNextDeadlineNanos() {
			return nextDeadline;
		}
	}
}