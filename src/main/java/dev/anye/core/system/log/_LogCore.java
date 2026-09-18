package dev.anye.core.system.log;

public interface  _LogCore {
	void info(String msg, Object... param);
	void warn(String msg, Object... param);
	void error(String msg, Object... param);
	void debug(String msg, Object... param);
}
