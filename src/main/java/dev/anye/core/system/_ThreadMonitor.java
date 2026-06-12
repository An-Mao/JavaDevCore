package dev.anye.core.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;

public class _ThreadMonitor {
	private final Runnable task;
	private Thread thread;
	private final long cooldownMillis;
	private final long s;
	private final Logger _log = LoggerFactory.getLogger(_ThreadMonitor.class);

	public _ThreadMonitor(Runnable task, long cooldownMillis) {
		this.task = task;
		this.cooldownMillis = cooldownMillis;
		this.s = cooldownMillis / 1000;
	}

	public void start() {
		thread = createThread(task);
		thread.start();
	}

	private Thread createThread(Runnable task) {
		Thread newThread = new Thread(task);
		newThread.setUncaughtExceptionHandler((t, e) -> {
			_log.error("Thread {} crashed! {}", t.getName(), e.getMessage());
			try {
				_log.info("waiting {} seconds...", LocalTime.ofSecondOfDay(s));
				Thread.sleep(cooldownMillis);
			} catch (InterruptedException ex) {
				_log.warn("cooldown interrupted: {}", ex.getMessage());
			}
			restart();
		});
		return newThread;
	}

	private void restart() {
		_log.info("restarting...");
		thread = createThread(task);
		thread.start();
	}
}
