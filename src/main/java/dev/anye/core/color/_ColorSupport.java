package dev.anye.core.color;

public class _ColorSupport {
    public static int HexToColor(String s) {
        if (s == null) {
            return 0x00000000;
        }
        if (s.startsWith("0x")) {
            s = s.substring(2);
        }else if (s.startsWith("#")) {
            s = s.substring(1);
        }
        return (int) Long.parseLong(s, 16);
    }
    public static int getR(int color) {
        return (color >> 16) & 0xFF;
    }
    public static int getG(int color) {
        return (color >> 8) & 0xFF;
    }
    public static int getB(int color) {
        return color & 0xFF;
    }
    public static int getAlpha(int color) {
        if ((color & 0xFF000000) != 0) {
            return  (color >> 24) & 0xFF;
        } else {
            return  255;
        }
    }
    public static int[] extractRGBA(int color) {
        int r, g, b, a;
        if ((color & 0xFF000000) != 0) {
            // ARGB format
            a = (color >> 24) & 0xFF;
        } else {
            // RGB format, default to opaque
            a = 255;
        }
        r = (color >> 16) & 0xFF;
        g = (color >> 8) & 0xFF;
        b = color & 0xFF;

        return new int[]{r, g, b, a};
    }
    public static String intToHexColor(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        if ((color & 0xFF000000) == 0) {
            a = 255;
        }
        return String.format("0x%02X%02X%02X%02X", a, r, g, b);
    }
}
