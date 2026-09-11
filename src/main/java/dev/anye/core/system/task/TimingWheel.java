package dev.anye.core.system.task;

import dev.anye.core.system._Log;

import java.util.Objects;
import java.util.concurrent.*;
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
 */
public final class TimingWheel implements AutoCloseable {
	private static final _Log LOG = new _Log(TimingWheel.class);
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
	public static final TimeUnit DEFAULT_TICK_UNIT = TimeUnit.MILLISECONDS;
	public static final int DEFAULT_WHEEL_SIZE = 512;
	private static final int LEVEL_COUNT = 4;
	private static final int MAX_DRAIN_PER_ROUND = 8192;
	private static final long MAX_CATCH_UP_TICKS = 100_000;

	/* ============================================================
	 * Configuration
	 * ============================================================ */

	private final long tickNanos;
	private final int wheelSize;
	private final int mask;
	private final long startTime;
	private final Executor executor;
	private final BiConsumer<TimerTask, Throwable> exceptionHandler;
	private final WheelLevel[] levels;

	private final ConcurrentLinkedQueue<TimerTask> pending = new ConcurrentLinkedQueue<>();
	// 【修复1】：引入独立的取消队列，防止被取消的任务一直存留在 Bucket 中引发内存泄露
	private final ConcurrentLinkedQueue<TimerTask> cancelledTasks = new ConcurrentLinkedQueue<>();
	private final ConcurrentHashMap<Object, TimerTask> keyedTasks = new ConcurrentHashMap<>();

	private final AtomicBoolean running = new AtomicBoolean(true);
	private final AtomicBoolean parked = new AtomicBoolean(false);
	private final AtomicInteger taskCount = new AtomicInteger();
	private final AtomicLong taskId = new AtomicLong();
	private final Metrics metrics = new Metrics();

	private final Thread worker;
	private long currentTick;

	/* ============================================================
	 * Constructor
	 * ============================================================ */

	public TimingWheel() {
		this(builder());
	}

	public TimingWheel(long tick, TimeUnit unit, int wheelSize, Executor executor) {
		this(builder().tick(tick, unit).wheelSize(wheelSize).executor(executor));
	}

	private TimingWheel(Builder builder) {
		Objects.requireNonNull(builder.unit, "unit");
		Objects.requireNonNull(builder.executor, "executor");
		Objects.requireNonNull(builder.threadName, "threadName");
		Objects.requireNonNull(builder.exceptionHandler, "exceptionHandler");

		if (builder.tick <= 0) throw new IllegalArgumentException("tick <= 0");
		if (builder.wheelSize < 2 || (builder.wheelSize & (builder.wheelSize - 1)) != 0) {
			throw new IllegalArgumentException("wheelSize must be a power of 2");
		}

		long nanos = builder.unit.toNanos(builder.tick);
		if (nanos <= 0) throw new IllegalArgumentException("tick is too small");

		this.tickNanos = nanos;
		this.wheelSize = builder.wheelSize;
		this.mask = builder.wheelSize - 1;
		this.executor = builder.executor;
		this.exceptionHandler = builder.exceptionHandler;
		this.startTime = System.nanoTime();

		this.levels = new WheelLevel[LEVEL_COUNT];
		long duration = tickNanos;
		long scale = 1;

		for (int i = 0; i < LEVEL_COUNT; i++) {
			levels[i] = new WheelLevel(duration, scale, wheelSize);
			if (i + 1 < LEVEL_COUNT) {
				duration = safeMultiply(duration, wheelSize);
				scale = safeMultiply(scale, wheelSize);
			}
		}

		currentTick = 0;
		worker = new Thread(this::workerLoop, builder.threadName);
		worker.setDaemon(builder.daemon);
		worker.start();
	}

	/* ============================================================
	 * Builder
	 * ============================================================ */

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private long tick = DEFAULT_TICK;
		private TimeUnit unit = DEFAULT_TICK_UNIT;
		private int wheelSize = DEFAULT_WHEEL_SIZE;
		private Executor executor = ForkJoinPool.commonPool();
		private String threadName = "TimingWheel-Worker";
		private boolean daemon = true;
		private BiConsumer<TimerTask, Throwable> exceptionHandler = (task, throwable) -> LOG.error(throwable.getMessage());

		public Builder tick(long tick, TimeUnit unit) {
			this.tick = tick;
			this.unit = Objects.requireNonNull(unit);
			return this;
		}

		public Builder wheelSize(int wheelSize) {
			this.wheelSize = wheelSize;
			return this;
		}

		public Builder executor(Executor executor) {
			this.executor = Objects.requireNonNull(executor);
			return this;
		}

		public Builder threadName(String name) {
			this.threadName = Objects.requireNonNull(name);
			return this;
		}

		public Builder daemon(boolean daemon) {
			this.daemon = daemon;
			return this;
		}

		public Builder exceptionHandler(BiConsumer<TimerTask, Throwable> handler) {
			this.exceptionHandler = Objects.requireNonNull(handler);
			return this;
		}

		public TimingWheel build() {
			return new TimingWheel(this);
		}
	}

	/* ============================================================
	 * Schedule
	 * ============================================================ */

	public TimerTask schedule(Runnable runnable, long delay, TimeUnit unit) {
		Objects.requireNonNull(runnable);
		Objects.requireNonNull(unit);
		checkRunning();

		long delayNanos = Math.max(0, unit.toNanos(delay));
		long deadline = safeAdd(System.nanoTime(), delayNanos);

		TimerTask task = new TimerTask(taskId.incrementAndGet(), runnable, deadline, 0, false, null);
		task.state.set(SCHEDULED);
		taskCount.incrementAndGet();
		metrics.scheduled.incrementAndGet();

		submit(task);
		return task;
	}

	public TimerTask scheduleAtFixedRate(Runnable runnable, long initialDelay, long period, TimeUnit unit) {
		return schedulePeriodic(runnable, initialDelay, period, unit, true);
	}

	public TimerTask scheduleWithFixedDelay(Runnable runnable, long initialDelay, long delay, TimeUnit unit) {
		return schedulePeriodic(runnable, initialDelay, delay, unit, false);
	}

	private TimerTask schedulePeriodic(Runnable runnable, long initialDelay, long period, TimeUnit unit, boolean fixedRate) {
		Objects.requireNonNull(runnable);
		Objects.requireNonNull(unit);
		if (period <= 0) throw new IllegalArgumentException("period <= 0");
		checkRunning();

		long initialNanos = Math.max(0, unit.toNanos(initialDelay));
		long periodNanos = unit.toNanos(period);
		if (periodNanos <= 0) throw new IllegalArgumentException("period is too small");

		long deadline = safeAdd(System.nanoTime(), initialNanos);
		TimerTask task = new TimerTask(taskId.incrementAndGet(), runnable, deadline, periodNanos, fixedRate, null);
		task.state.set(SCHEDULED);
		taskCount.incrementAndGet();
		metrics.scheduled.incrementAndGet();

		submit(task);
		return task;
	}

	/* ============================================================
	 * Debounce
	 * ============================================================ */

	public TimerTask scheduleDebounce(Object key, Runnable runnable, long delay, TimeUnit unit) {
		Objects.requireNonNull(key);
		Objects.requireNonNull(runnable);
		Objects.requireNonNull(unit);
		checkRunning();

		long delayNanos = Math.max(0, unit.toNanos(delay));
		long deadline = safeAdd(System.nanoTime(), delayNanos);

		TimerTask task = new TimerTask(taskId.incrementAndGet(), runnable, deadline, 0, false, key);
		task.state.set(SCHEDULED);
		taskCount.incrementAndGet();
		metrics.scheduled.incrementAndGet();

		TimerTask old = keyedTasks.put(key, task);
		if (old != null) {
			old.cancel();
		}

		submit(task);
		return task;
	}

	public boolean cancelDebounce(Object key) {
		if (key == null) return false;
		TimerTask task = keyedTasks.remove(key);
		if (task == null) return false;
		return task.cancel();
	}

	/* ============================================================
	 * Submit
	 * ============================================================ */

	private void submit(TimerTask task) {
		pending.offer(task);
		if (parked.get()) {
			LockSupport.unpark(worker);
		}
	}

	/* ============================================================
	 * Worker
	 * ============================================================ */

	private void workerLoop() {
		// 【修复3】：包裹 try-catch 避免异常导致调度器悄悄死亡
		try {
			while (running.get()) {
				drainPending();
				drainCancelled(); // 马上清理被取消任务占用的内存

				long now = System.nanoTime();
				long targetTick = elapsedToTick(now);
				long delta = targetTick - currentTick;

				if (delta > MAX_CATCH_UP_TICKS) {
					// 【修复2】：先更新 currentTick 再重新分配！避免恢复时任务错位挂起
					currentTick = targetTick;
					recoverAfterLongPause(now);
				} else {
					while (currentTick < targetTick && running.get()) {
						currentTick++;
						advance(currentTick, now);
					}
				}

				if (!running.get()) break;

				// 检查是否又有了新任务或取消事件
				if (!pending.isEmpty() || !cancelledTasks.isEmpty()) {
					continue;
				}

				long nextTickTime = tickDeadline(currentTick + 1);
				long wait = nextTickTime - System.nanoTime();

				if (wait <= 0) continue;

				parked.set(true);

				// 最后一道防线，如果在设为 true 后瞬间有任务进来
				if (!pending.isEmpty() || !cancelledTasks.isEmpty()) {
					parked.set(false);
					continue;
				}

				LockSupport.parkNanos(this, wait);
				parked.set(false);
			}
		} catch (Throwable t) {
			LOG.error("[TimingWheel] Fatal error: Worker thread died unexpectedly!");
			LOG.error(t.getMessage());
		} finally {
			cleanup();
		}
	}

	/* ============================================================
	 * Pending / Cancelled Drain
	 * ============================================================ */

	private void drainPending() {
		int count = 0;
		while (count++ < MAX_DRAIN_PER_ROUND) {
			TimerTask task = pending.poll();
			if (task == null) break;

			int state = task.state.get();
			if (state == CANCELLED) {
				finishCancelled(task);
				continue;
			}

			if (!running.get()) {
				cancelInternal(task);
				continue;
			}
			placeTask(task, System.nanoTime());
		}
	}

	// 【修复1】：清除任务的底层 Bucket 链接，真正完成内存释放
	private void drainCancelled() {
		TimerTask task;
		while ((task = cancelledTasks.poll()) != null) {
			Bucket b = task.bucket;
			if (b != null) {
				b.remove(task);
				task.bucket = null;
			}
		}
	}

	/* ============================================================
	 * Advance
	 * ============================================================ */

	private void advance(long tick, long now) {
		expireBucket(levels[0].buckets[(int) (tick & mask)], now);
		if ((tick & mask) == 0) {
			cascade(1, now);
		}
	}

	private void cascade(int level, long now) {
		if (level >= LEVEL_COUNT) return;

		long levelTick = currentLevelTick(level);
		Bucket bucket = levels[level].buckets[(int) (levelTick & mask)];
		TimerTask task = bucket.head;

		while (task != null) {
			TimerTask next = task.next;
			bucket.remove(task);
			task.bucket = null;

			if (task.state.get() == CANCELLED) {
				finishCancelled(task);
			} else {
				placeTask(task, now);
			}
			task = next;
		}

		if ((levelTick & mask) == 0) {
			cascade(level + 1, now);
		}
	}

	/* ============================================================
	 * Place
	 * ============================================================ */

	private void placeTask(TimerTask task, long now) {
		if (task.state.get() == CANCELLED) {
			finishCancelled(task);
			return;
		}

		long delay = task.deadline - now;
		if (delay <= 0) {
			executeTask(task);
			return;
		}

		long elapsed = task.deadline - startTime;
		if (elapsed <= 0) {
			executeTask(task);
			return;
		}

		long deadlineTick = ceilDivPositive(elapsed, tickNanos);
		if (deadlineTick <= currentTick) {
			executeTask(task);
			return;
		}

		int level = selectLevel(delay);
		long targetLevelTick = deadlineTick / levels[level].scale;
		long currentLevel = currentLevelTick(level);

		if (level > 0 && targetLevelTick <= currentLevel) {
			level = 0;
			targetLevelTick = deadlineTick;
			currentLevel = currentTick;
		}

		if (targetLevelTick <= currentLevel) {
			targetLevelTick = currentLevel + 1;
		}

		Bucket bucket = levels[level].buckets[(int) (targetLevelTick & mask)];
		bucket.add(task);
		task.bucket = bucket;
		task.level = level;
	}

	private int selectLevel(long delay) {
		for (int i = 0; i < LEVEL_COUNT; i++) {
			long range = safeMultiply(levels[i].duration, wheelSize);
			if (delay < range) return i;
		}
		return LEVEL_COUNT - 1;
	}

	/* ============================================================
	 * Expire
	 * ============================================================ */

	private void expireBucket(Bucket bucket, long now) {
		TimerTask task = bucket.head;
		while (task != null) {
			TimerTask next = task.next;
			bucket.remove(task);
			task.bucket = null;

			if (task.state.get() == CANCELLED) {
				finishCancelled(task);
			} else if (task.deadline <= now) {
				executeTask(task);
			} else {
				placeTask(task, now);
			}
			task = next;
		}
	}

	/* ============================================================
	 * Long Pause Recovery
	 * ============================================================ */

	private void recoverAfterLongPause(long now) {
		for (WheelLevel level : levels) {
			for (Bucket bucket : level.buckets) {
				TimerTask task = bucket.head;
				while (task != null) {
					TimerTask next = task.next;
					bucket.remove(task);
					task.bucket = null;

					if (task.state.get() == CANCELLED) {
						finishCancelled(task);
					} else if (task.deadline <= now) {
						executeTask(task);
					} else {
						placeTask(task, now);
					}
					task = next;
				}
			}
		}
	}

	/* ============================================================
	 * Execute
	 * ============================================================ */

	private void executeTask(TimerTask task) {
		if (!task.state.compareAndSet(SCHEDULED, RUNNING)) {
			if (task.state.get() == CANCELLED) {
				finishCancelled(task);
			}
			return;
		}

		try {
			executor.execute(() -> runTask(task));
		} catch (RejectedExecutionException e) {
			metrics.rejected.incrementAndGet();
			if (task.state.compareAndSet(RUNNING, CANCELLED)) {
				finishCancelled(task);
			}
			handleException(task, e);
		} catch (Throwable e) {
			if (task.state.compareAndSet(RUNNING, CANCELLED)) {
				finishCancelled(task);
			}
			handleException(task, e);
		}
	}

	/* ============================================================
	 * Run
	 * ============================================================ */

	private void runTask(TimerTask task) {
		if (task.state.get() != RUNNING) {
			finishCancelled(task);
			return;
		}

		try {
			task.runnable.run();
			metrics.executed.incrementAndGet();
			if (task.periodNanos > 0) {
				metrics.periodicExecuted.incrementAndGet();
			}
		} catch (Throwable e) {
			metrics.failed.incrementAndGet();
			handleException(task, e);

			if (task.periodNanos > 0 && task.cancelOnException) {
				task.cancel();
			}
		} finally {
			afterRun(task);
		}
	}

	/* ============================================================
	 * After Run
	 * ============================================================ */

	private void afterRun(TimerTask task) {
		if (task.state.get() == CANCELLED) {
			finishCancelled(task);
			return;
		}

		if (task.periodNanos > 0 && running.get()) {
			if (task.state.compareAndSet(RUNNING, SCHEDULED)) {
				long now = System.nanoTime();
				if (task.fixedRate) {
					long next = safeAdd(task.deadline, task.periodNanos);
					if (next <= now) {
						long behind = now - next;
						long missed = behind / task.periodNanos + 1;
						next = safeAdd(next, safeMultiply(missed, task.periodNanos));
					}
					task.deadline = next;
				} else {
					task.deadline = safeAdd(now, task.periodNanos);
				}
				submit(task);
				return;
			}
		}

		if (task.state.compareAndSet(RUNNING, DONE)) {
			removeKey(task);
			decrementTaskCount(task);
		} else {
			finishCancelled(task);
		}
	}

	/* ============================================================
	 * Cancel
	 * ============================================================ */

	// 【修复1】：整理归并安全的统一取消逻辑
	private boolean cancel(TimerTask task) {
		while (true) {
			int state = task.state.get();
			if (state == DONE || state == CANCELLED) return false;

			if (task.state.compareAndSet(state, CANCELLED)) {
				handleTaskCancellation(task);
				return true;
			}
		}
	}

	private void cancelInternal(TimerTask task) {
		while (true) {
			int state = task.state.get();
			if (state == DONE || state == CANCELLED) return;

			if (task.state.compareAndSet(state, CANCELLED)) {
				handleTaskCancellation(task);
				return;
			}
		}
	}

	private void handleTaskCancellation(TimerTask task) {
		removeKey(task);
		metrics.cancelled.incrementAndGet();
		finishCancelled(task);

		if (task.bucket != null) {
			if (Thread.currentThread() == worker) {
				// 如果当前就是 worker 线程，直接断开引用无任何线程安全问题
				task.bucket.remove(task);
				task.bucket = null;
			} else {
				// 如果是用户/业务线程发起的取消，推送到取消队列由 Worker 来断开
				cancelledTasks.offer(task);
				if (parked.get()) {
					LockSupport.unpark(worker);
				}
			}
		}
	}

	private void finishCancelled(TimerTask task) {
		decrementTaskCount(task);
	}

	private void decrementTaskCount(TimerTask task) {
		if (task.counted.compareAndSet(true, false)) {
			taskCount.decrementAndGet();
		}
	}

	private void removeKey(TimerTask task) {
		if (task.key != null) {
			keyedTasks.remove(task.key, task);
		}
	}

	/* ============================================================
	 * Exception
	 * ============================================================ */

	private void handleException(TimerTask task, Throwable throwable) {
		try {
			exceptionHandler.accept(task, throwable);
		} catch (Throwable ignored) {
			// 不能杀死 Worker
		}
	}

	/* ============================================================
	 * Shutdown
	 * ============================================================ */

	public void shutdown() {
		if (running.compareAndSet(true, false)) {
			LockSupport.unpark(worker);
		}
	}

	public void shutdownAndWait() throws InterruptedException {
		shutdown();
		worker.join();
	}

	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		Objects.requireNonNull(unit);
		long millis = Math.max(0, unit.toMillis(timeout));
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
		TimerTask task;
		while ((task = pending.poll()) != null) {
			cancelInternal(task);
		}
		while ((task = cancelledTasks.poll()) != null) {
			Bucket b = task.bucket;
			if (b != null) {
				b.remove(task);
				task.bucket = null;
			}
		}
		for (WheelLevel level : levels) {
			for (Bucket bucket : level.buckets) {
				task = bucket.head;
				while (task != null) {
					TimerTask next = task.next;
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

	private long elapsedToTick(long now) {
		long elapsed = now - startTime;
		if (elapsed <= 0) return 0;
		return elapsed / tickNanos;
	}

	private long tickDeadline(long tick) {
		return safeAdd(startTime, safeMultiply(tick, tickNanos));
	}

	private long currentLevelTick(int level) {
		return currentTick / levels[level].scale;
	}

	private static long ceilDivPositive(long a, long b) {
		if (a <= 0) return 0;
		return (a - 1) / b + 1;
	}

	private static long safeAdd(long a, long b) {
		if (b > 0 && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
		if (b < 0 && a < Long.MIN_VALUE - b) return Long.MIN_VALUE;
		return a + b;
	}

	private static long safeMultiply(long a, long b) {
		if (a == 0 || b == 0) return 0;
		if (a > 0 && b > 0) {
			if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
		} else if (a < 0 && b < 0) {
			if (a < Long.MAX_VALUE / b) return Long.MAX_VALUE;
		} else if (a > 0) {
			if (b < Long.MIN_VALUE / a) return Long.MIN_VALUE;
		} else {
			if (a < Long.MIN_VALUE / b) return Long.MIN_VALUE;
		}
		return a * b;
	}

	private void checkRunning() {
		if (!running.get()) {
			throw new IllegalStateException("TimingWheel is shutdown");
		}
	}

	/* ============================================================
	 * Wheel Level
	 * ============================================================ */

	private static final class WheelLevel {
		final long duration;
		final long scale;
		final Bucket[] buckets;

		WheelLevel(long duration, long scale, int wheelSize) {
			this.duration = duration;
			this.scale = scale;
			this.buckets = new Bucket[wheelSize];
			for (int i = 0; i < wheelSize; i++) {
				buckets[i] = new Bucket();
			}
		}
	}

	/* ============================================================
	 * Bucket
	 * ============================================================ */

	private static final class Bucket {
		TimerTask head;
		TimerTask tail;

		void add(TimerTask task) {
			task.prev = tail;
			task.next = null;
			if (tail != null) {
				tail.next = task;
			} else {
				head = task;
			}
			tail = task;
		}

		void remove(TimerTask task) {
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
		}
	}

	/* ============================================================
	 * TimerTask
	 * ============================================================ */

	public final class TimerTask {
		private final long id;
		private final Runnable runnable;
		private final AtomicInteger state = new AtomicInteger(NEW);
		private final AtomicBoolean counted = new AtomicBoolean(true);
		private final long periodNanos;
		private final boolean fixedRate;
		private final Object key;

		private volatile long deadline;
		private volatile boolean cancelOnException;
		private volatile Bucket bucket;
		private volatile int level = -1;

		private TimerTask prev;
		private TimerTask next;

		private TimerTask(long id, Runnable runnable, long deadline, long periodNanos, boolean fixedRate, Object key) {
			this.id = id;
			this.runnable = runnable;
			this.deadline = deadline;
			this.periodNanos = periodNanos;
			this.fixedRate = fixedRate;
			this.key = key;
		}

		public boolean cancel() {
			return TimingWheel.this.cancel(this);
		}

		public boolean isCancelled() {
			return state.get() == CANCELLED;
		}

		public boolean isDone() {
			int state = this.state.get();
			return state == DONE || state == CANCELLED;
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

		public TimerTask cancelOnException(boolean value) {
			this.cancelOnException = value;
			return this;
		}

		@Override
		public String toString() {
			return "TimerTask{" + "id=" + id + ", state=" + state.get() + ", deadline=" + deadline +
					", period=" + periodNanos + ", fixedRate=" + fixedRate + ", level=" + level + '}';
		}
	}

	/* ============================================================
	 * Metrics
	 * ============================================================ */

	public static final class MetricsSnapshot {
		public final long scheduled, cancelled, executed, failed, rejected, periodicExecuted;

		private MetricsSnapshot(long scheduled, long cancelled, long executed, long failed, long rejected, long periodicExecuted) {
			this.scheduled = scheduled;
			this.cancelled = cancelled;
			this.executed = executed;
			this.failed = failed;
			this.rejected = rejected;
			this.periodicExecuted = periodicExecuted;
		}
	}

	private static final class Metrics {
		final AtomicLong scheduled = new AtomicLong();
		final AtomicLong cancelled = new AtomicLong();
		final AtomicLong executed = new AtomicLong();
		final AtomicLong failed = new AtomicLong();
		final AtomicLong rejected = new AtomicLong();
		final AtomicLong periodicExecuted = new AtomicLong();

		MetricsSnapshot snapshot() {
			return new MetricsSnapshot(scheduled.get(), cancelled.get(), executed.get(),
					failed.get(), rejected.get(), periodicExecuted.get());
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