package dev.anye.core.system.task;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * 高性能 Hashed Timing Wheel 时间轮。
 *
 * <p>适用于大量延迟任务和定时任务。</p>
 *
 * <p>特点：</p>
 * <ul>
 *     <li>单次延迟执行</li>
 *     <li>固定延迟重复执行</li>
 *     <li>固定频率重复执行</li>
 *     <li>任务取消</li>
 *     <li>线程安全添加任务</li>
 *     <li>单独调度线程</li>
 *     <li>任务异常隔离</li>
 * </ul>
 *
 * <p>时间精度由 tickDuration 决定。</p>
 *
 * <p>例如：</p>
 * <pre>
 * TimingWheel wheel = new TimingWheel(
 *         512,
 *         10,
 *         TimeUnit.MILLISECONDS
 * );
 * </pre>
 *
 * @author Anye
 */
public final class $_TimingWheel implements AutoCloseable {

	/**
	 * 每一格代表的时间。
	 */
	private final long tickNanos;

	/**
	 * 时间轮槽位数量。
	 *
	 * <p>建议使用 2 的幂，例如：</p>
	 * <pre>
	 * 64
	 * 128
	 * 256
	 * 512
	 * 1024
	 * </pre>
	 */
	private final int wheelSize;

	/**
	 * wheelSize - 1。
	 *
	 * <p>当 wheelSize 是 2 的幂时，可以使用位运算代替取模。</p>
	 */
	private final int mask;

	/**
	 * 一个完整时间轮的时间。
	 */
	private final long wheelNanos;

	/**
	 * 所有槽位。
	 */
	private final Bucket[] buckets;

	/**
	 * 当前 Tick。
	 */
	private final AtomicLong tick = new AtomicLong();

	/**
	 * 是否运行。
	 */
	private final AtomicBoolean running = new AtomicBoolean(true);

	/**
	 * 时间轮线程。
	 */
	private final Thread workerThread;

	/**
	 * 时间轮启动时间。
	 */
	private final long startTime;

	/**
	 * 创建时间轮。
	 *
	 * @param wheelSize   槽位数量，必须为 2 的幂
	 * @param tickDuration 每个 Tick 的时间
	 * @param unit         时间单位
	 */
	public $_TimingWheel(
			int wheelSize,
			long tickDuration,
			TimeUnit unit
	) {
		Objects.requireNonNull(unit, "unit");

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

		if (tickDuration <= 0) {
			throw new IllegalArgumentException(
					"tickDuration must be > 0"
			);
		}

		this.tickNanos = unit.toNanos(tickDuration);

		if (this.tickNanos <= 0) {
			throw new IllegalArgumentException(
					"tickDuration is too small"
			);
		}

		this.wheelSize = wheelSize;
		this.mask = wheelSize - 1;
		this.wheelNanos = Math.multiplyExact(
				tickNanos,
				wheelSize
		);

		this.buckets = new Bucket[wheelSize];

		for (int i = 0; i < wheelSize; i++) {
			buckets[i] = new Bucket();
		}

		this.startTime = System.nanoTime();

		this.workerThread = new Thread(
				this::run,
				"Timing-Wheel"
		);

		this.workerThread.setDaemon(true);
		this.workerThread.start();
	}

	/**
	 * 使用默认配置创建时间轮。
	 *
	 * <p>
	 * 默认：
	 * </p>
	 *
	 * <pre>
	 * wheelSize = 512
	 * tick      = 10ms
	 * </pre>
	 */
	public $_TimingWheel() {
		this(
				512,
				10,
				TimeUnit.MILLISECONDS
		);
	}

	/**
	 * 提交一次性延迟任务。
	 *
	 * @param task  任务
	 * @param delay 延迟
	 * @param unit  时间单位
	 *
	 * @return 可取消的任务句柄
	 */
	public TimerTask schedule(
			Runnable task,
			long delay,
			TimeUnit unit
	) {
		Objects.requireNonNull(task, "task");
		Objects.requireNonNull(unit, "unit");

		if (delay < 0) {
			delay = 0;
		}

		TimerTask timerTask = new TimerTask(
				this,
				task,
				false,
				0L,
				false
		);

		long deadline = System.nanoTime() + unit.toNanos(delay);

		timerTask.deadline = deadline;

		addTask(timerTask);

		return timerTask;
	}

	/**
	 * 固定延迟执行。
	 *
	 * <p>
	 * 上一次任务执行结束后，
	 * 再等待 delay。
	 * </p>
	 *
	 * @param task         任务
	 * @param initialDelay 初始延迟
	 * @param delay        每次执行后的延迟
	 * @param unit         时间单位
	 *
	 * @return 可取消任务
	 */
	public TimerTask scheduleWithFixedDelay(
			Runnable task,
			long initialDelay,
			long delay,
			TimeUnit unit
	) {
		Objects.requireNonNull(task, "task");
		Objects.requireNonNull(unit, "unit");

		if (initialDelay < 0) {
			initialDelay = 0;
		}

		if (delay <= 0) {
			throw new IllegalArgumentException(
					"delay must be > 0"
			);
		}

		long delayNanos = unit.toNanos(delay);

		TimerTask timerTask = new TimerTask(
				this,
				task,
				true,
				delayNanos,
				false
		);

		timerTask.deadline =
				System.nanoTime()
						+ unit.toNanos(initialDelay);

		addTask(timerTask);

		return timerTask;
	}

	/**
	 * 固定频率执行。
	 *
	 * <p>
	 * 尽量按照固定时间点执行。
	 * </p>
	 *
	 * <p>
	 * 如果任务执行时间过长，
	 * 下一次任务可能立即补偿执行。
	 * </p>
	 *
	 * @param task         任务
	 * @param initialDelay 初始延迟
	 * @param period       执行周期
	 * @param unit         时间单位
	 *
	 * @return 可取消任务
	 */
	public TimerTask scheduleAtFixedRate(
			Runnable task,
			long initialDelay,
			long period,
			TimeUnit unit
	) {
		Objects.requireNonNull(task, "task");
		Objects.requireNonNull(unit, "unit");

		if (initialDelay < 0) {
			initialDelay = 0;
		}

		if (period <= 0) {
			throw new IllegalArgumentException(
					"period must be > 0"
			);
		}

		long periodNanos = unit.toNanos(period);

		TimerTask timerTask = new TimerTask(
				this,
				task,
				true,
				periodNanos,
				true
		);

		timerTask.deadline =
				System.nanoTime()
						+ unit.toNanos(initialDelay);

		addTask(timerTask);

		return timerTask;
	}

	/**
	 * 添加任务到时间轮。
	 */
	private void addTask(TimerTask task) {
		if (!running.get()) {
			task.cancel();
			return;
		}

		if (task.cancelled.get()) {
			return;
		}

		long currentTime = System.nanoTime();

		long remaining =
				task.deadline - currentTime;

		/*
		 * 已到期任务。
		 *
		 * 放入下一 Tick，
		 * 避免当前正在处理的槽位错过任务。
		 */
		if (remaining <= tickNanos) {
			long currentTick = tick.get();

			long targetTick = currentTick + 1;

			int index =
					(int) (targetTick & mask);

			task.remainingRounds = 0;

			buckets[index].add(task);

			return;
		}

		/*
		 * 计算还需要多少 Tick。
		 */
		long ticks =
				remaining / tickNanos;

		/*
		 * 向上取整。
		 */
		if (remaining % tickNanos != 0) {
			ticks++;
		}

		if (ticks <= 0) {
			ticks = 1;
		}

		long currentTick = tick.get();

		long targetTick =
				currentTick + ticks;

		/*
		 * 计算需要转多少圈。
		 */
		long rounds =
				ticks / wheelSize;

		/*
		 * 如果刚好落在完整轮数边界，
		 * 需要减一。
		 *
		 * 例如：
		 *
		 * wheelSize = 8
		 * ticks = 8
		 *
		 * 当前槽位经过 8 Tick 后到达同一槽位，
		 * 不应该再多等一整圈。
		 */
		if (ticks % wheelSize == 0 && rounds > 0) {
			rounds--;
		}

		int index =
				(int) (targetTick & mask);

		task.remainingRounds = rounds;

		buckets[index].add(task);
	}

	/**
	 * 时间轮主循环。
	 */
	private void run() {
		long nextTickTime =
				startTime + tickNanos;

		while (running.get()) {

			long now = System.nanoTime();

			long wait =
					nextTickTime - now;

			/*
			 * 还没有到下一 Tick。
			 */
			if (wait > 0) {

				LockSupport.parkNanos(wait);

				continue;
			}

			/*
			 * 当前 Tick。
			 */
			long currentTick =
					tick.incrementAndGet();

			int index =
					(int) (currentTick & mask);

			Bucket bucket =
					buckets[index];

			processBucket(bucket);

			/*
			 * 计算下一 Tick。
			 *
			 * 不使用：
			 *
			 * nextTickTime = System.nanoTime() + tickNanos
			 *
			 * 否则任务执行耗时会不断累积漂移。
			 */
			nextTickTime =
					startTime
							+ currentTick * tickNanos
							+ tickNanos;
		}

		/*
		 * 关闭后取消剩余任务。
		 */
		cancelAllTasks();
	}

	/**
	 * 处理一个槽位。
	 */
	private void processBucket(Bucket bucket) {
		TimerTask task = bucket.head;

		while (task != null) {

			TimerTask next = task.next;

			/*
			 * 从当前 Bucket 移除。
			 */
			bucket.remove(task);

			/*
			 * 已取消。
			 */
			if (task.cancelled.get()) {
				task = next;
				continue;
			}

			/*
			 * 还需要等待完整时间轮。
			 */
			if (task.remainingRounds > 0) {

				task.remainingRounds--;

				bucket.add(task);

				task = next;

				continue;
			}

			long now = System.nanoTime();

			/*
			 * 时间尚未真正到达。
			 *
			 * 因为 Tick 是离散的，
			 * 重新加入时间轮。
			 */
			if (task.deadline > now) {

				addTask(task);

				task = next;

				continue;
			}

			/*
			 * 执行任务。
			 */
			executeTask(task);

			task = next;
		}
	}

	/**
	 * 执行任务。
	 */
	private void executeTask(TimerTask task) {
		if (task.cancelled.get()) {
			return;
		}

		try {

			task.runningTask.set(true);

			task.task.run();

		} catch (Throwable throwable) {

			/*
			 * 时间轮线程不能因为任务异常退出。
			 */
			handleTaskException(
					task,
					throwable
			);

		} finally {

			task.runningTask.set(false);
		}

		/*
		 * 一次性任务。
		 */
		if (!task.repeat) {
			task.done.set(true);
			return;
		}

		/*
		 * 执行期间被取消。
		 */
		if (task.cancelled.get()) {
			task.done.set(true);
			return;
		}

		long now = System.nanoTime();

		/*
		 * Fixed Rate。
		 */
		if (task.fixedRate) {

			task.deadline += task.periodNanos;

			/*
			 * 如果已经严重落后，
			 * 直接跳到下一个未来时间点。
			 *
			 * 防止无限补偿执行。
			 */
			if (task.deadline <= now) {

				long behind =
						now - task.deadline;

				long skip =
						behind / task.periodNanos + 1;

				task.deadline +=
						skip * task.periodNanos;
			}

		} else {

			/*
			 * Fixed Delay。
			 *
			 * 从任务结束时间开始计算。
			 */
			task.deadline =
					now + task.periodNanos;
		}

		addTask(task);
	}

	/**
	 * 任务异常处理。
	 *
	 * <p>
	 * 子类式扩展可以自行修改源码，
	 * 例如接入自己的日志系统。
	 * </p>
	 */
	private void handleTaskException(
			TimerTask task,
			Throwable throwable
	) {
		System.err.println(
				"[TimingWheel] Task execution failed:"
		);

		throwable.printStackTrace();
	}

	/**
	 * 取消全部任务。
	 */
	private void cancelAllTasks() {
		for (Bucket bucket : buckets) {

			TimerTask task = bucket.head;

			while (task != null) {

				TimerTask next = task.next;

				task.cancel();

				task = next;
			}
		}
	}

	/**
	 * 是否正在运行。
	 */
	public boolean isRunning() {
		return running.get();
	}

	/**
	 * 当前 Tick。
	 */
	public long getCurrentTick() {
		return tick.get();
	}

	/**
	 * 获取 Tick 时间。
	 */
	public long getTickDuration(
			TimeUnit unit
	) {
		Objects.requireNonNull(unit, "unit");

		return unit.convert(
				tickNanos,
				TimeUnit.NANOSECONDS
		);
	}

	/**
	 * 获取时间轮槽位数量。
	 */
	public int getWheelSize() {
		return wheelSize;
	}

	/**
	 * 关闭时间轮。
	 */
	public void shutdown() {
		if (!running.compareAndSet(true, false)) {
			return;
		}

		LockSupport.unpark(workerThread);
	}

	/**
	 * 关闭时间轮。
	 */
	@Override
	public void close() {
		shutdown();
	}

	/**
	 * 时间轮槽位。
	 *
	 * <p>
	 * 使用双向链表，
	 * O(1) 添加和删除。
	 * </p>
	 */
	private static final class Bucket {

		private TimerTask head;

		private TimerTask tail;

		/**
		 * 添加任务。
		 */
		void add(TimerTask task) {

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

		/**
		 * 删除任务。
		 */
		void remove(TimerTask task) {
			if (task.bucket != this) {
				return;
			}

			TimerTask prev = task.prev;
			TimerTask next = task.next;

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
			task.bucket = null;
		}
	}

	/**
	 * 定时任务句柄。
	 */
	public static final class TimerTask {

		/**
		 * 所属时间轮。
		 */
		private final $_TimingWheel wheel;

		/**
		 * 实际任务。
		 */
		private final Runnable task;

		/**
		 * 是否重复。
		 */
		private final boolean repeat;

		/**
		 * 周期。
		 */
		private final long periodNanos;

		/**
		 * 是否固定频率。
		 */
		private final boolean fixedRate;

		/**
		 * 是否取消。
		 */
		private final AtomicBoolean cancelled =
				new AtomicBoolean(false);

		/**
		 * 是否完成。
		 */
		private final AtomicBoolean done =
				new AtomicBoolean(false);

		/**
		 * 是否正在执行。
		 */
		private final AtomicBoolean runningTask =
				new AtomicBoolean(false);

		/**
		 * 执行时间。
		 */
		private volatile long deadline;

		/**
		 * 还需要经过多少完整时间轮。
		 */
		private volatile long remainingRounds;

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
				$_TimingWheel wheel,
				Runnable task,
				boolean repeat,
				long periodNanos,
				boolean fixedRate
		) {
			this.wheel = wheel;
			this.task = task;
			this.repeat = repeat;
			this.periodNanos = periodNanos;
			this.fixedRate = fixedRate;
		}

		/**
		 * 取消任务。
		 *
		 * @return 是否成功取消
		 */
		public boolean cancel() {

			if (!cancelled.compareAndSet(
					false,
					true
			)) {
				return false;
			}

			Bucket currentBucket =
					bucket;

			if (currentBucket != null) {
				currentBucket.remove(this);
			}

			done.set(true);

			return true;
		}

		/**
		 * 是否已取消。
		 */
		public boolean isCancelled() {
			return cancelled.get();
		}

		/**
		 * 是否完成。
		 */
		public boolean isDone() {
			return done.get();
		}

		/**
		 * 是否正在执行。
		 */
		public boolean isRunning() {
			return runningTask.get();
		}

		/**
		 * 获取剩余延迟。
		 */
		public long getDelay(
				TimeUnit unit
		) {
			Objects.requireNonNull(unit, "unit");

			long remaining =
					deadline - System.nanoTime();

			if (remaining < 0) {
				remaining = 0;
			}

			return unit.convert(
					remaining,
					TimeUnit.NANOSECONDS
			);
		}

		/**
		 * 获取下一次执行时间。
		 */
		public long getDeadlineNanos() {
			return deadline;
		}

		/**
		 * 获取任务所属时间轮。
		 */
		public $_TimingWheel getWheel() {
			return wheel;
		}
	}
}