package dev.anye.core.pack;

import dev.anye.core.system._File;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class _Pack {
	public static void writeFiles(String packPath, String putPath, String suffix, boolean replace, String... names) {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		for (String name : names) {
			if (replace && new File(_File.getFilePath(putPath, name + suffix)).exists()) continue;
			String filePath = _File.getFilePath(putPath, name + suffix);
			try (InputStream inputStream = classLoader.getResourceAsStream(filePath)) {
				if (inputStream == null)
					throw new FileNotFoundException("Resource not found: " + filePath);
				Path outputPath = Paths.get(filePath);
				Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static void writeFiles(String packPath, String putPath, String suffix, String... names) {
		writeFiles(packPath, putPath, suffix, false, names);
	}
}
