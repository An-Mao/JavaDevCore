package dev.anye.core.system;

import dev.anye.core.exception._IOException;
import dev.anye.core.time.FastDateTime;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class _Log implements _LogCore{
	public static final FastDateTime TIME = new FastDateTime();

	protected final BlockingQueue<String> logQueue;

	protected final Thread logThread ;

	protected final boolean color;
	protected final String infoColor;
	protected final String warnColor;
	protected final String errorColor;
	protected final String debugColor;

	protected boolean debug = false;


	public _Log() {
		this("");
	}

	public _Log(String logFile) {
		this(logFile, isAnsiSupported());
	}
	public _Log(String logFile,boolean color) {
		this(logFile, color,_LogColor.GREEN,_LogColor.YELLOW,_LogColor.RED,_LogColor.CYAN);
	}
	public _Log(String logFile,String infoColor,String warnColor,String errorColor,String debugColor) {
		this(logFile,true,infoColor,warnColor,errorColor,debugColor);
	}

	public _Log(String logFile, boolean color,String infoColor,String warnColor,String errorColor,String debugColor) {
		this.color = color;
		this.infoColor = infoColor;
		this.warnColor = warnColor;
		this.errorColor = errorColor;
		this.debugColor = debugColor;
		this.logQueue = new LinkedBlockingQueue<>();


		if (logFile != null && !logFile.isEmpty()){
			logThread = new Thread(() -> {
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
					while (true) {
						String log = logQueue.take();
						writer.write(log);
						writer.newLine();
						writer.flush();
					}
				} catch (IOException e) {
					throw new _IOException(e);
				}catch (InterruptedException e){
					_error("[Log Error] log write error：" + e.getMessage());
					Thread.currentThread().interrupt();
				}
			});

			logThread.setDaemon(true);
			logThread.start();
		}else {
			logThread = null;
		}

	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public void close() {
		if (threadIsAlive()) logThread.interrupt();
	}

	public static void _error(String msg){
		System.err.println(msg);
	}

	public void writeLog(String log) {
		if (threadIsAlive() && !logQueue.offer(log)) {
			_error("[!log error! can't add log to queue.]");
		}

	}

	public boolean threadIsAlive(){
		return logThread != null && logThread.isAlive();
	}

	protected void log(String logColor, String level, String msg) {
		if (this.color){
			String formattedLog = String.format("%s[%s][%s]%s", logColor, getTime(), level, msg);
			System.out.println(formattedLog + _LogColor.RESET);

			writeLog(String.format("[%s][%s]%s", getTime(), level, msg));
		}else log(level,msg);
	}


	protected void log(String level, String msg) {
		String plainLog = String.format("[%s][%s]%s", getTime(), level, msg);
		System.out.println(plainLog);

		writeLog(plainLog);
	}


	protected String getTime() {
		/*SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		return sdf.format(new Date());*/
		return TIME.update().toTimeString(":");
	}


	public static boolean isWindowsAnsiSupported() {
		String os = System.getProperty("os.name").toLowerCase();
		if (!os.contains("win")) {
			return false;
		}

		String version = System.getProperty("os.version");
		String[] parts = version.split("\\.");
		if (parts.length >= 2) {
			try {
				int major = Integer.parseInt(parts[0]);
				int minor = Integer.parseInt(parts[1]);
				return major >= 10 && minor >= 0;
			} catch (NumberFormatException e) {
				return false;
			}
		}

		return false;
	}

	public static boolean testAnsiColorSupport() {
		try {
			System.out.print(_LogColor.PURPLE);
			System.out.print("Check ANSI color support");
			System.out.print(_LogColor.RESET);
			System.out.println();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static boolean isAnsiSupported() {
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")) {
			return isWindowsAnsiSupported() && testAnsiColorSupport();
		}
		String term = System.getenv("TERM");
		return term != null && !term.equals("dumb");
	}

	public String format(String msg, Object... param) {
		if (param.length > 0) {
			StringBuilder builder = new StringBuilder();
			for (Object p : param) {
				int i = msg.indexOf("{}");
				if (i != -1) {
					builder.append(msg.substring(0, i)).append(p.toString());
					msg = msg.substring(i + 2);
				} else {
					break;
				}
			}
			if (!msg.equals("")) {
				builder.append(msg);
			}
			return builder.toString();
		}
		return msg;
	}

	@Override
	public void info(String msg, Object... param) {
		log(infoColor, "Info", format(msg,param));
	}
	@Override
	public void warn(String msg, Object... param) {

		log(warnColor, "Warn", format(msg,param));
	}
	@Override
	public void error(String msg, Object... param) {
		log(errorColor, "Error", format(msg,param));
	}
	@Override
	public void debug(String msg, Object... param) {
		if (debug) {
			StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
			String className = "UnknownClass";
			String methodName = "UnknownMethod";
			if (stackTraceElements.length >= 3) {
				className = stackTraceElements[2].getClassName();
				methodName = stackTraceElements[2].getMethodName();
			}
			log(debugColor, "Debug", "(" + className + ":" + methodName + ")" + format(msg, param));
		}
	}
}
