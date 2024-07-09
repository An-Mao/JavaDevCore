package anmao.dev.core.bytes;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class Byte {
    public static UUID getUUID(String md5Hash){
        byte[] bytes = hexStringToByteArray(getMd5(md5Hash));
        return UUID.nameUUIDFromBytes(bytes);
    }
    public static byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i+1), 16));
        }
        return data;
    }
    public static String getMd5(String input){
        return getHash(input,"MD5");
    }
    public static String getSha1(String input){
        return getHash(input,"SHA-1");
    }
    public static String getHash(String input, String type) {
        try {
            MessageDigest digest = MessageDigest.getInstance(type);
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
