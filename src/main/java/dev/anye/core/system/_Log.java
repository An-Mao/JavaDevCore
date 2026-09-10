package dev.anye.core.system;

import dev.anye.core.time.FastDateTime;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class _Log implements _LogCore {

    /**
     * 日志记录。
     * timestamp:
     *     日志产生时的时间戳，而不是文件线程实际写入的时间。
     * level:
     *     Info / Warn / Error / Debug
     * message:
     *     已经完成 {} 参数替换后的消息。
     * threadName:
     *     产生这条日志的线程名称。
     */
    public record LogEntry(
            long timestamp,
            String level,
            String message,
            String threadName
    ) {

        /**
         * 转换成普通日志格式。
         * 例如：
         * [17:20:31][Info]Hello
         */
        public String format(FastDateTime time) {

            time.setEpochMillis(timestamp);

            return String.format(
                    "[%s][%s]%s",
                    time.toTimeString(":"),
                    level,
                    message
            );
        }
    }


    /**
     * 每批写多少条日志后 flush。
     */
    protected static final int FLUSH_BATCH_SIZE = 100;

    /**
     * 最长多少毫秒 flush 一次。
     */
    protected static final long FLUSH_INTERVAL_MS = 1000L;

    /**
     * close() 默认最多等待多少毫秒。
     * 正常情况下日志线程应该远早于这个时间退出。
     */
    protected static final long CLOSE_TIMEOUT_MS = 5000L;


    /**
     * ANSI 颜色只用于控制台。
     */
    protected final boolean color;

    protected final String infoColor;
    protected final String warnColor;
    protected final String errorColor;
    protected final String debugColor;


    /**
     * 日志队列。
     */
    protected final BlockingQueue<LogEntry> logQueue;


    /**
     * 文件写入线程。
     */
    protected final Thread logThread;


    /**
     * 是否启用 Debug。
     */
    protected volatile boolean debug = false;


    /**
     * 是否已经请求关闭。
     */
    protected volatile boolean closed = false;


    /**
     * 文件写入是否发生异常。
     */
    protected volatile Throwable writeError;


    /**
     * 日志线程是否已经正常完成。
     */
    protected volatile boolean writerStopped = false;


    /**
     * 文件日志专用时间对象。
     * 不再使用 static FastDateTime。
     * 只有日志写入线程访问它，因此不需要额外同步。
     */
    protected final FastDateTime fileTime = new FastDateTime();


    public _Log() {
        this("");
    }


    public _Log(String logFile) {
        this(logFile, isAnsiSupported());
    }


    public _Log(String logFile, boolean color) {
        this(
                logFile,
                color,
                _LogColor.GREEN,
                _LogColor.YELLOW,
                _LogColor.RED,
                _LogColor.CYAN
        );
    }


    public _Log(
            String logFile,
            String infoColor,
            String warnColor,
            String errorColor,
            String debugColor
    ) {
        this(
                logFile,
                true,
                infoColor,
                warnColor,
                errorColor,
                debugColor
        );
    }


    public _Log(
            String logFile,
            boolean color,
            String infoColor,
            String warnColor,
            String errorColor,
            String debugColor
    ) {

        this.color = color;

        this.infoColor = infoColor;
        this.warnColor = warnColor;
        this.errorColor = errorColor;
        this.debugColor = debugColor;

        this.logQueue = new LinkedBlockingQueue<>();


        /*
         * 没有指定日志文件：
         *
         * 仍然正常输出控制台，
         * 但不启动文件线程。
         */
        if (logFile == null || logFile.isEmpty()) {

            this.logThread = null;

            return;
        }


        this.logThread = new Thread(
                () -> runLogWriter(logFile),
                "Anye-Log-Writer"
        );

        /*
         * 日志线程为 daemon。
         *
         * 即使用户忘记 close()，
         * JVM 也不会因为日志线程而无法退出。
         */
        this.logThread.setDaemon(true);

        this.logThread.start();
    }


    /**
     * 日志文件写入线程。
     */
    protected void runLogWriter(String logFile) {

        try (
                Writer output = new OutputStreamWriter(
                        new FileOutputStream(logFile, true),
                        StandardCharsets.UTF_8
                );

                BufferedWriter writer = new BufferedWriter(output)
        ) {

            int pendingCount = 0;

            long lastFlushTime = System.nanoTime();


            while (true) {
                /*
                 * poll 而不是无限 take。
                 *
                 * 这样可以定期执行 flush。
                 */
                LogEntry entry = logQueue.poll(
                        FLUSH_INTERVAL_MS,
                        TimeUnit.MILLISECONDS
                );
                if (entry != null) {

                    /*
                     * 写入日志。
                     */
                    writer.write(
                            entry.format(fileTime)
                    );

                    writer.newLine();

                    pendingCount++;
                }


                /*
                 * flush 条件：
                 *
                 * 1. 达到批量数量
                 * 2. 达到时间间隔
                 */
                long now = System.nanoTime();

                if (
                        pendingCount >= FLUSH_BATCH_SIZE
                                || now - lastFlushTime
                                >= TimeUnit.MILLISECONDS.toNanos(
                                        FLUSH_INTERVAL_MS
                                )
                ) {

                    if (pendingCount > 0) {

                        writer.flush();

                        pendingCount = 0;
                    }

                    lastFlushTime = now;
                }


                /*
                 * close() 已经请求关闭，
                 * 并且队列已经为空。
                 *
                 * 此时可以退出。
                 *
                 * 注意：
                 *
                 * 不使用特殊 POISON LogEntry，
                 * 避免把控制信号混进真正的日志数据。
                 */
                if (closed && logQueue.isEmpty()) {

                    /*
                     * 最后一批日志必须 flush。
                     */
                    if (pendingCount > 0) {

                        writer.flush();

                        pendingCount = 0;
                    }

                    break;
                }
            }


            /*
             * 最终 flush。
             */
            writer.flush();


        } catch (InterruptedException e) {

            /*
             * interrupt 是 close() 超时后的兜底机制。
             *
             * 此时不要再继续阻塞等待。
             */
            Thread.currentThread().interrupt();

            writeError = e;

            _error(
                    "[Log Error] log writer interrupted: "
                            + e.getMessage()
            );


        } catch (IOException e) {

            /*
             * 后台线程不能直接 throw，
             * 否则只会导致日志线程死亡。
             */
            writeError = e;

            _error(
                    "[Log Error] log file write failed: "
                            + e.getMessage()
            );


        } finally {

            writerStopped = true;
        }
    }


    /**
     * 设置 Debug 开关。
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }


    /**
     * 判断日志系统是否已经关闭。
     */
    public boolean isClosed() {
        return closed;
    }


    /**
     * 获取文件写入异常。
     * null 表示目前没有发生异常。
     */
    public Throwable getWriteError() {
        return writeError;
    }


    /**
     * 优雅关闭。
     * 设计目标：
     * 1. 不主动 interrupt 正常日志线程。
     * 2. 允许日志线程把 Queue 中已有日志全部写完。
     * 3. 最多等待 CLOSE_TIMEOUT_MS。
     * 4. 如果超时，再 interrupt。
     * 因此：
     * 正常情况下：
     * close()
     *     ↓
     * closed = true
     *     ↓
     * 日志线程继续消费 Queue
     *     ↓
     * Queue 为空
     *     ↓
     * flush
     *     ↓
     * exit
     * 异常情况下：
     * close()
     *     ↓
     * 等待 5 秒
     *     ↓
     * interrupt()
     *     ↓
     * 强制结束
     */
    public void close() {

        /*
         * 没有文件线程。
         */
        if (logThread == null) {

            closed = true;

            return;
        }


        /*
         * 已经关闭。
         */
        if (closed) {
            return;
        }


        /*
         * 防止日志线程自己 join 自己。
         */
        if (Thread.currentThread() == logThread) {

            closed = true;

            return;
        }


        /*
         * closed 与 writeLog() 使用相同锁。
         *
         * 这样可以保证：
         *
         * writeLog:
         *     判断 closed
         *     ↓
         *     加入 Queue
         *
         * close:
         *     closed = true
         *
         * 两者不会交叉产生竞态。
         */
        synchronized (this) {

            if (closed) {
                return;
            }

            closed = true;
        }


        /*
         * 第一阶段：
         *
         * 等待日志线程正常处理完队列。
         */
        try {

            logThread.join(CLOSE_TIMEOUT_MS);

        } catch (InterruptedException e) {

            /*
             * 调用 close() 的线程被 interrupt。
             */
            Thread.currentThread().interrupt();

            _error(
                    "[Log Error] interrupted while closing logger: "
                            + e.getMessage()
            );

            return;
        }


        /*
         * 如果正常退出，则结束。
         */
        if (!logThread.isAlive()) {
            return;
        }


        /*
         * 第二阶段：
         *
         * 日志线程在规定时间内没有结束。
         *
         * 可能是：
         *
         * - 文件系统阻塞
         * - 网络文件系统异常
         * - IO 长时间卡住
         * - 其他不可预期情况
         *
         * 此时使用 interrupt 作为兜底。
         */
        _error(
                "[Log Warning] log writer did not stop within "
                        + CLOSE_TIMEOUT_MS
                        + " ms, interrupting."
        );

        logThread.interrupt();


        /*
         * 给 interrupt 一个短暂的退出时间。
         */
        try {

            logThread.join(1000L);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            _error(
                    "[Log Error] interrupted while forcing logger close: "
                            + e.getMessage()
            );
        }
    }


    /**
     * 将日志加入异步写入队列。
     * 不负责文件 IO。
     */
    public void writeLog(LogEntry entry) {

        if (entry == null) {
            return;
        }


        /*
         * 没有文件线程。
         */
        if (logThread == null) {
            return;
        }


        /*
         * close 与写入必须同步。
         */
        synchronized (this) {

            /*
             * close() 后的新日志不再接受。
             */
            if (closed) {
                return;
            }


            /*
             * LinkedBlockingQueue 是无界队列，
             * offer 正常情况下不会失败。
             */
            logQueue.offer(entry);
        }
    }


    /**
     * 保留原来的 writeLog(String) API。
     * 如果外部代码原来直接调用 writeLog(String)，
     * 仍然可以继续使用。
     */
    public void writeLog(String log) {

        if (log == null) {
            return;
        }

        writeLog(
                new LogEntry(
                        System.currentTimeMillis(),
                        "Log",
                        log,
                        Thread.currentThread().getName()
                )
        );
    }


    /**
     * 判断文件线程是否存活。
     */
    public boolean threadIsAlive() {

        return logThread != null
                && logThread.isAlive();
    }


    /**
     * 输出错误。
     */
    public static void _error(String msg) {

        System.err.println(msg);
    }


    /**
     * 输出日志。
     *
     * 文件保存纯文本。
     * 控制台根据 color 决定是否添加 ANSI。
     */
    protected void log(
            String logColor,
            String level,
            String msg
    ) {

        /*
         * 在业务线程生成 LogEntry。
         *
         * 时间、线程、消息都在这里确定。
         */
        LogEntry entry = new LogEntry(
                System.currentTimeMillis(),
                level,
                msg,
                Thread.currentThread().getName()
        );


        /*
         * 控制台显示。
         *
         * 使用当前线程生成的时间，
         * 避免等待文件线程导致显示时间变化。
         */
        String consoleLog = formatConsoleLog(entry);


        if (color) {

            System.out.println(
                    logColor
                            + consoleLog
                            + _LogColor.RESET
            );

        } else {

            System.out.println(consoleLog);
        }


        /*
         * 文件异步写入。
         */
        writeLog(entry);
    }


    /**
     * 保留原来的 protected log(level,msg)。
     */
    protected void log(
            String level,
            String msg
    ) {

        LogEntry entry = new LogEntry(
                System.currentTimeMillis(),
                level,
                msg,
                Thread.currentThread().getName()
        );

        System.out.println(
                formatConsoleLog(entry)
        );

        writeLog(entry);
    }


    /**
     * 格式化控制台日志。
     */
    protected String formatConsoleLog(LogEntry entry) {

        /*
         * 控制台时间单独创建。
         *
         * 这个 FastDateTime 只在当前调用线程中使用。
         */
        FastDateTime time = new FastDateTime();

        time.setEpochMillis(entry.timestamp());

        return String.format(
                "[%s][%s]%s",
                time.toTimeString(":"),
                entry.level(),
                entry.message()
        );
    }


    /**
     * 获取当前时间。
     *
     * 保留原 API。
     */
    protected String getTime() {

        FastDateTime time = new FastDateTime();

        return time.update().toTimeString(":");
    }


    /**
     * Windows ANSI 支持判断。
     */
    public static boolean isWindowsAnsiSupported() {

        String os = System.getProperty(
                "os.name",
                ""
        ).toLowerCase();

        if (!os.contains("win")) {
            return false;
        }


        String version = System.getProperty(
                "os.version",
                ""
        );

        String[] parts = version.split("\\.");

        if (parts.length == 0) {
            return false;
        }


        try {

            int major = Integer.parseInt(parts[0]);

            return major >= 10;

        } catch (NumberFormatException e) {

            return false;
        }
    }


    /**
     * 测试 ANSI。
     */
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


    /**
     * 判断 ANSI 支持。
     */
    public static boolean isAnsiSupported() {

        String os = System.getProperty(
                "os.name",
                ""
        ).toLowerCase();


        if (os.contains("win")) {

            return isWindowsAnsiSupported()
                    && testAnsiColorSupport();
        }


        String term = System.getenv("TERM");

        return term != null
                && !term.equalsIgnoreCase("dumb");
    }


    /**
     * {} 参数格式化。
     *
     * 示例：
     *
     * format("Hello {}", "World")
     *
     * -> Hello World
     *
     * format("{} + {} = {}", 1, 2, 3)
     *
     * -> 1 + 2 = 3
     */
    public String format(
            String msg,
            Object... param
    ) {

        if (msg == null) {
            return null;
        }


        if (param == null || param.length == 0) {
            return msg;
        }


        if (!msg.contains("{}")) {
            return msg;
        }


        StringBuilder builder = new StringBuilder(
                msg.length() + param.length * 8
        );


        int start = 0;
        int paramIndex = 0;


        while (paramIndex < param.length) {

            int index = msg.indexOf(
                    "{}",
                    start
            );


            if (index < 0) {
                break;
            }


            builder.append(
                    msg,
                    start,
                    index
            );


            builder.append(
                    param[paramIndex++]
            );


            start = index + 2;
        }


        /*
         * 剩余字符串。
         */
        builder.append(
                msg,
                start,
                msg.length()
        );


        return builder.toString();
    }


    @Override
    public void info(
            String msg,
            Object... param
    ) {

        log(
                infoColor,
                "Info",
                format(msg, param)
        );
    }


    @Override
    public void warn(
            String msg,
            Object... param
    ) {

        log(
                warnColor,
                "Warn",
                format(msg, param)
        );
    }


    @Override
    public void error(
            String msg,
            Object... param
    ) {

        log(
                errorColor,
                "Error",
                format(msg, param)
        );
    }


    @Override
    public void debug(
            String msg,
            Object... param
    ) {

        if (!debug) {
            return;
        }


        /*
         * StackWalker 返回的是 StackFrame，
         * 不是 StackTraceElement。
         *
         * 所以这里必须：
         *
         * .map(StackWalker.StackFrame::toStackTraceElement)
         */
        StackTraceElement caller = StackWalker
                .getInstance(
                        StackWalker.Option.RETAIN_CLASS_REFERENCE
                )
                .walk(stream ->
                        stream
                                .skip(1)
                                .map(
                                        StackWalker.StackFrame
                                                ::toStackTraceElement
                                )
                                .findFirst()
                                .orElse(null)
                );


        String className = "UnknownClass";
        String methodName = "UnknownMethod";


        if (caller != null) {

            className = caller.getClassName();
            methodName = caller.getMethodName();
        }


        log(
                debugColor,
                "Debug",
                "("
                        + className
                        + ":"
                        + methodName
                        + ")"
                        + format(msg, param)
        );
    }
}