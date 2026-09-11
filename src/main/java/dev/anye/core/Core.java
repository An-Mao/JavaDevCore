package dev.anye.core;


import dev.anye.core.system.task.HashedTimingWheelOld;
import dev.anye.core.system.task.$_TimingWheel;

import java.util.concurrent.TimeUnit;

public class Core {
	public static final String VERSION = "2.0.6";
	//private static final Logger LOGGER = Logger.getLogger(Core.class.getName());
	public static void main(String[] args) {
		//run();
	}

	public static void run() {
		HashedTimingWheelOld wheel = new HashedTimingWheelOld();

		wheel.schedule(
				() -> System.out.println("3 秒后执行"),
				3,
				TimeUnit.SECONDS
		);
		wheel.scheduleWithFixedDelay(
				() -> {
					System.out.println("执行任务");
				},
				0,
				1,
				TimeUnit.SECONDS
		);
		wheel.scheduleAtFixedRate(
				() -> {
					System.out.println("Tick");
				},
				0,
				100,
				TimeUnit.MILLISECONDS
		);
		HashedTimingWheelOld.TimerTask task =
				wheel.schedule(
						() -> System.out.println("不会执行"),
						10,
						TimeUnit.SECONDS
				);

		task.cancel();


		$_TimingWheel wheel = new $_TimingWheel(
				512,
				10,
				TimeUnit.MILLISECONDS
		);

		wheel.schedule(
				() -> System.out.println("3 秒后执行"),
				3,
				TimeUnit.SECONDS
		);
		wheel.scheduleWithFixedDelay(
				() -> {
					System.out.println("执行任务");
				},
				0,
				1,
				TimeUnit.SECONDS
		);
	}
}