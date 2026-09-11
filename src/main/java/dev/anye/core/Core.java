package dev.anye.core;

import dev.anye.core.system.task.TimingWheel;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class Core {
	public static final String VERSION = "2.0.6";
	//private static final Logger LOGGER = Logger.getLogger(Core.class.getName());
	public static void main(String[] args) {
		run();
	}

	public static void run() {
		// //普通定时任务
		// TimingWheel wheel = TimingWheel.builder()
		// 		.tick(10, TimeUnit.MILLISECONDS)
		// 		.wheelSize(512)
		// 		.executor(ForkJoinPool.commonPool())
		// 		.threadName("Core-Timer")
		// 		.daemon(true)
		// 		.build();

		// wheel.schedule(
		// 		() -> System.out.println("Hello"),
		// 		1,
		// 		TimeUnit.SECONDS);

		// //周期任务
		// wheel.scheduleAtFixedRate(
		// 		() -> {
		// 			System.out.println("tick");
		// 		},
		// 		0,
		// 		1,
		// 		TimeUnit.SECONDS);

		// //取消
		// TimingWheel.TimerTask task = wheel.schedule(
		// 		() -> System.out.println("test"),
		// 		10,
		// 		TimeUnit.SECONDS);

		// task.cancel();
	}
}