package dev.anye.core.pack;

public final class _Class {
	private _Class(){
	}
	public static ClassLoader getClassLoader(){
		return Thread.currentThread().getContextClassLoader();
	}
}
