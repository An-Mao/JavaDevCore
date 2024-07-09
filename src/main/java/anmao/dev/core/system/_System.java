package anmao.dev.core.system;

public class _System {
    public static final boolean isWindows;
    static {
        String os = System.getProperty("os.name").toLowerCase();

        isWindows = os.contains("windows");
    }
    public static boolean isModLoaded(String modId) {
        try {
            Class.forName(modId);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
