package dev.anye.core.system;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class _Log {
    // ANSI color codes
    public static final String RESET = "\u001B[0m";
    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";

    public boolean Debug = false;
    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    private String logFile;
    private final boolean ENABLE_COLOR;
    private final Thread logThread = new Thread(() -> {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            while (true) {
                String log = logQueue.take();
                writer.write(log);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException | InterruptedException e) {
            System.err.println(RED + "[Log Error] log write error：" + e.getMessage() + RESET);
        }
    });


    public _Log() {
        this("");
    }

    public _Log(String logFile) {
        this(logFile,isAnsiSupported());
    }

    public _Log(String logFile,boolean color) {
        this.logFile = logFile;
        ENABLE_COLOR = color;
        if (!this.logFile.isEmpty()) setLogFile(logFile);
    }

    public void setLogFile(String logFile) {
        if (logFile.isEmpty()) logThread.interrupt();

        this.logFile = logFile;
        if (!logFile.isEmpty() && !logThread.isAlive()) {
            logThread.setDaemon(true);
            logThread.start();
        }
    }

    public void info(String... msg) {
        log(ENABLE_COLOR ? GREEN : "", "Info", msg);
    }

    public void error(String... msg) {
        log(ENABLE_COLOR ? RED : "", "Error", msg);
    }

    public void warn(String... msg) {
        log(ENABLE_COLOR ? YELLOW : "", "Warn", msg);
    }

    public void debug(String... msg) {
        if (!Debug) return;
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        String className = "UnknownClass";
        String methodName = "UnknownMethod";
        if (stackTraceElements.length >= 3) {
            className = stackTraceElements[2].getClassName();
            methodName = stackTraceElements[2].getMethodName();
        }
        log(ENABLE_COLOR ? CYAN : "", "Debug(" + className +":"+methodName+ ")", msg);
    }

    private void log(String color, String level, String... msg) {
        for (String s : msg) {
            String formattedLog = String.format("%s[%s][%s]%s", color, getTime(), level, s);
            String plainLog = String.format("[%s][%s]%s", getTime(), level, s);

            System.out.println(ENABLE_COLOR ? formattedLog + RESET : plainLog);

            if (!this.logFile.isEmpty()) logQueue.offer(plainLog);
        }
    }

    private static String getTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(new Date());
    }

    private static boolean isWindowsAnsiSupported() {
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
    private static boolean testAnsiColorSupport() {
        try {

            System.out.print(PURPLE);
            System.out.print("Check ANSI color support");
            System.out.print("\u001B[0m");
            System.out.println();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isAnsiSupported() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return isWindowsAnsiSupported() && testAnsiColorSupport();
        }
        String term = System.getenv("TERM");
        return term != null && !term.equals("dumb");
    }

}
