package dev.anye.core.reg;

import dev.anye.core.exception._ClassNotFoundException;
import dev.anye.core.exception._IOException;
import dev.anye.core.exception._TargetException;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class _Reg<T, A extends Annotation> {
	public static final String CLASS_SUFFIX = ".class";
	private final Map<String, T> dataMap = new HashMap<>();
	private final Class<A> type;
	private final Consumer<A> consumer;
	private final Create<T, A> create;
	private final Method nameMethod;

	public _Reg(Class<A> type, Consumer<A> consumer, Create<T, A> create) {
		try {
			nameMethod = type.getMethod("name");
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException("The annotation type " + type.getName() + " must define a 'String name()' method to be used with _Reg.", e);
		}
		this.type = type;
		this.consumer = consumer;
		this.create = create;
	}

	public Map<String, T> getTable() {
		return dataMap;
	}

	public T get(String key) {
		return dataMap.get(key);
	}

	public T getOrDefault(String key, T t) {
		return dataMap.getOrDefault(key, t);
	}

	public boolean addAndCheck(String key, T t) {
		if (dataMap.containsKey(key)) return false;
		dataMap.put(key, t);
		return true;
	}

	public void add(String key, T t) {
		dataMap.computeIfAbsent(key, k -> t);
	}

	public void register(String packageName) {
		try {
			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
			String path = packageName.replace('.', '/');
			Enumeration<URL> resources = classLoader.getResources(path);
			while (resources.hasMoreElements()) {
				URL resource = resources.nextElement();
				if (resource.getProtocol().equals("jar")) {
					analysisFile(resource.getPath().substring(5, resource.getPath().indexOf("!")), path);
				} else {
					File dir = new File(resource.getFile());
					if (dir.exists()) {
						processDirectory(dir, packageName);
					}
				}
			}
		} catch (IOException e) {
			throw new _IOException(packageName, e);
		}
	}

	public void analysisFile(String jarPath, String path) {
		try (JarFile jarFile = new JarFile(new File(jarPath))) {
			Enumeration<JarEntry> entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				if (entry.getName().endsWith(CLASS_SUFFIX) && entry.getName().startsWith(path)) {
					String className = entry.getName().replace("/", ".").replace(CLASS_SUFFIX, "");
					if (!className.contains("$")) reg(className);
				}
			}
		} catch (IOException e) {
			throw new _IOException(jarPath, e);
		}
	}

	private void processDirectory(File directory, String packageName) {
		File[] files = directory.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isDirectory()) {
					String subPackageName = packageName + "." + file.getName();
					processDirectory(file, subPackageName);
				} else if (file.getName().endsWith(CLASS_SUFFIX)) {
					String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
					if (!className.contains("$")) {
						System.out.println("Registered: " + className);
						reg(className);
					}
				}
			}
		}
	}

	public void reg(String className) {
		try {
			Class<?> clazz = Class.forName(className);
			if (clazz.isAnnotationPresent(type) && !Modifier.isAbstract(clazz.getModifiers()) && !Modifier.isInterface(clazz.getModifiers())) {
				A annotation = clazz.getAnnotation(type);
				if (annotation != null) {
					consumer.accept(annotation);
					addClass(className, annotation, clazz);
				}
			}
		} catch (ClassNotFoundException e) {
			throw new _ClassNotFoundException(className, e.getException());
		}
	}

	public void addClass(String className, A annotation, Class<?> clazz) {
		try {
			String name = (String) nameMethod.invoke(annotation);
			if (name == null || name.isEmpty()) name = clazz.getSimpleName().toLowerCase();
			dataMap.put(name, create.create(clazz, annotation));
		} catch (IllegalAccessException | InvocationTargetException | SecurityException e) {
			throw new _TargetException(className, e);
		}
	}


	public interface Create<T, A extends Annotation> {
		T create(Class<?> c, A annotation);
	}
}
