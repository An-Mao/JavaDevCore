package dev.anye.core.exception;

public class _TargetException extends RuntimeException {
	public _TargetException() {
		super();
	}

	public _TargetException(String target) {
		super("Target error ==> " + target);
	}

	public _TargetException(String target, Throwable e) {
		super("Target error ==> " + target, e);
	}

	public _TargetException(Throwable e) {
		super(e);
	}

}
