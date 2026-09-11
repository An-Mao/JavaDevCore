package dev.anye.core.system.task;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler.fixedRate(
 *     () -> System.out.println("tick"),
 *     0,
 *     1,
 *     TimeUnit.SECONDS
 * );
 */
public final class Scheduler {



	private static final ScheduledThreadPoolExecutor EXECUTOR =
			new ScheduledThreadPoolExecutor(
					1,
					r -> {
						Thread t = new Thread(r, "Core-Scheduler");
						t.setDaemon(true);
						return t;
					}
			);

	private Scheduler() {}

	public static ScheduledFuture<?> once(
			Runnable task,
			long delay,
			TimeUnit unit
	) {
		return EXECUTOR.schedule(task, delay, unit);
	}

	public static ScheduledFuture<?> fixedRate(
			Runnable task,
			long initialDelay,
			long period,
			TimeUnit unit
	) {
		return EXECUTOR.scheduleAtFixedRate(
				task,
				initialDelay,
				period,
				unit
		);
	}

	public static ScheduledFuture<?> fixedDelay(
			Runnable task,
			long initialDelay,
			long delay,
			TimeUnit unit
	) {
		return EXECUTOR.scheduleWithFixedDelay(
				task,
				initialDelay,
				delay,
				unit
		);
	}
}