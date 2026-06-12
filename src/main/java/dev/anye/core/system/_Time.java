package dev.anye.core.system;

public class _Time {
	public static int getSysSec() {
		return (int) (System.currentTimeMillis() / 1000);
	}
}
