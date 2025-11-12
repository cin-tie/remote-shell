package csdev.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * <p>Utility class for logging
 * @author cin-tie
 * @version 1.0
 */
public class Logger {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static boolean debugEnabled = false;

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled =  enabled;
    }

    public static void logInfo(String message) {
        log("INFO", message);
    }

    public static void logError(String message) {
        log("ERROR", message);
    }

    public static void logWarning(String message) {
        log("WARN", message);
    }

    public static void logDebug(String message) {
        if (debugEnabled) {
            log("DEBUG", message);
        }
    }

    public static void logServer(String message) {
        log("SERVER", message);
    }

    public static void logClient(String message) {
        log("CLIENT", message);
    }

    private static void log(String level, String message) {
        String timestamp = dateFormat.format(new Date());
        String threadName = Thread.currentThread().getName();
        System.out.println(String.format("[%s] [%s] [%s] [%s] %s",
                timestamp, level, threadName, getCallerClassName(), message));
    }

    private static String getCallerClassName() {
        StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
        for (int i = 1; i < stElements.length; i++) {
            StackTraceElement ste = stElements[i];
            if (!ste.getClassName().equals(Logger.class.getName()) &&
                    !ste.getClassName().equals(Thread.class.getName())) {
                String className = ste.getClassName();
                return className.substring(className.lastIndexOf('.') + 1);
            }
        }
        return "Unknown";
    }
}
