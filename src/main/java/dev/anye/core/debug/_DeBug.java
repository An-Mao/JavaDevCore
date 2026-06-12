package dev.anye.core.debug;

public class _DeBug {
	public static void ThrowError(String msg) {
		throw new IllegalArgumentException(msg);
	}

	public static void ThrowError() {
		throw new IllegalArgumentException();
	}
}
