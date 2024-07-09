package anmao.dev.core.debug;

public class DeBug {
    public static void ThrowError(String msg){
        throw new IllegalArgumentException(msg);
    }
    public static void ThrowError(){
        throw new IllegalArgumentException();
    }
}
