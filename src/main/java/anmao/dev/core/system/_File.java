package anmao.dev.core.system;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class _File {
    /**
     *
     * @param strings a ,b ,c
     * @return "/"+"a"+"/"+"b"
     */
    public static String getFilePath(String... strings){
        StringBuilder path = new StringBuilder();
        for (String s : strings){
            path.append("/").append(s);
        }
        return path.toString();
    }
    public static String getFileFullPathWithRun(String... strings){
        return System.getProperty("user.dir") + getFilePath(strings);
    }
    public static boolean getFileFullPathWithRunAndCheck(String... strings){
        return checkAndCreateDir(System.getProperty("user.dir") + getFilePath(strings));
    }
    public static boolean checkAndCreateDir(String dir){
        File folder = new File(dir);
        if (!folder.exists()) {
            return folder.mkdirs();
        }
        return true;
    }
    public static List<File> findFiles(File folder) {
        List<File> allFiles = new ArrayList<>();
        if (!folder.isDirectory()) {
            return allFiles;
        }
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".json")) {
                    allFiles.add(file);
                } else if (file.isDirectory()) {
                    allFiles.addAll(findFiles(file));
                }
            }
        }
        return allFiles;
    }
}
