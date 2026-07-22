package dev.anye.core.exception;

import java.io.FileNotFoundException;

public class _FileNotFoundException extends FileNotFoundException {
	public _FileNotFoundException() {
		super();
	}

	public _FileNotFoundException(String file) {
		super("file ==> " + file);
	}
}
