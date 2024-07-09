package anmao.dev.core.format;

import java.util.Objects;

public class _Format {
    public static int BooleanToInt(boolean b){
        if (b){
            return 1;
        }
        return 0;
    }
    public static boolean IntToBoolean(int i){
        return i != 0;
    }
    public static boolean StringToBoolean(String s){
        return Objects.equals(s, "enable") || Objects.equals(s, "1") || Objects.equals(s, "open");
    }
    public static int StringToInt(String s){
        return Integer.parseInt(s);
    }
}
