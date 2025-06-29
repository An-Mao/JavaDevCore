package dev.anye.core.reg;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class _Reg<T,A extends Annotation> {
    private final Map<String, T> datas = new HashMap<>();
    private final Class<A> type;
    private final Consumer<A> consumer;
    private final Create<T,A> create;
    //private final Method nameMethod; // <-- 这里！声明为类的成员变量
    public _Reg(Class<A> type, Consumer<A> consumer,Create<T,A> create){
        try {
            type.getMethod("name");
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("The annotation type " + type.getName() + " must define a 'String name()' method to be used with _Reg.", e);
        }
        this.type = type;
        this.consumer = consumer;
        this.create = create;
    }
    public Map<String, T> getTable() {
        return datas;
    }
    public T get(String key){
        return datas.get(key);
    }
    public T getOrDefault(String key,T t){
        return datas.getOrDefault(key,t);
    }
    public boolean add(String key,T t){
        if (!datas.containsKey(key)){
            datas.put(key,t);
            return true;
        }
        return false;
    }

    public void register(String packageName) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = packageName.replace('.', '/');
            Enumeration<URL> resources = classLoader.getResources(path);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                if (resource.getProtocol().equals("jar")) {
                    String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
                    try (JarFile jarFile = new JarFile(new File(jarPath))) {
                        Enumeration<JarEntry> entries = jarFile.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            if (entry.getName().endsWith(".class") && entry.getName().startsWith(path)) {
                                String className = entry.getName().replace("/", ".").replace(".class", "");
                                if (!className.contains("$")) reg(className);
                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read JAR file: " + jarPath, e);
                    }
                }else {
                    File dir = new File(resource.getFile());
                    if (dir.exists()) {
                        processDirectory(dir, packageName);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error during package scanning for: " + packageName, e);
        }

    }
    private void processDirectory(File directory, String packageName) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    String subPackageName = packageName + "." + file.getName();
                    processDirectory(file, subPackageName);
                } else if (file.getName().endsWith(".class")) {
                    String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                    if (!className.contains("$")) {
                        System.out.println("Registered: " + className);
                        reg(className);
                    }
                }
            }
        }
    }

    public void reg(String className){
        try {
            Class<?> clazz = Class.forName(className);
            if (clazz.isAnnotationPresent(type) && !Modifier.isAbstract(clazz.getModifiers()) && !Modifier.isInterface(clazz.getModifiers())) {
                A annotation = clazz.getAnnotation(type);
                if (annotation != null) {
                    consumer.accept(annotation);
                    _RegCoreAnnotation coreAnnotation = (_RegCoreAnnotation) annotation;
                    String name = coreAnnotation.name();
                    if (name == null || name.isEmpty()) name = clazz.getSimpleName().toLowerCase();
                    datas.put(name, create.create(clazz,annotation));
                }
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load or process class: " + className, e);
        }
    }

    /*
    public void reg(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (clazz.isAnnotationPresent(type) && !Modifier.isAbstract(clazz.getModifiers()) && !Modifier.isInterface(clazz.getModifiers())) {
                A annotation = clazz.getAnnotation(type);
                if (annotation != null) {
                    consumer.accept(annotation);
                    String name = (String) nameMethod.invoke(annotation);
                    if (name == null || name.isEmpty()) {
                        name = clazz.getSimpleName().toLowerCase();
                    }
                    datas.put(name, create.create(clazz, annotation));
                }
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load or process class: " + className, e); // 暂时注释，避免中断
        } catch (Exception e) { // 捕获所有其他潜在的运行时异常，例如 InvocationTargetException
            System.err.println("  Error processing class " + className + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace(); // 打印堆栈跟踪以获取详细信息
        }
    }

     */
    public interface Create<T,A extends Annotation> {
        T create(Class<?> c,A annotation);
    }
}
