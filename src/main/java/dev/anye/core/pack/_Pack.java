package dev.anye.core.pack;

import dev.anye.core.cdt._CDT;
import dev.anye.core.cdt._SuffixCDT;
import dev.anye.core.exception._IOException;
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
			try (InputStream inputStream = classLoader.getResourceAsStream(_File.getFilePath(packPath, name + suffix))) {
				if (inputStream == null)
					throw new FileNotFoundException("Resource not found: " + _File.getFilePath(packPath, name + suffix));
				Path outputPath = Paths.get(_File.getFilePath(putPath, name + suffix));
				Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new _IOException(e);
			}
		}
	}

	public static void writeFiles(String packPath, String putPath, String suffix, String... names) {
		writeFiles(packPath, putPath, suffix, false, names);
	}

	public static void writeJsonFiles(String packPath, String putPath, boolean replace, String... names) {
		writeFiles(packPath, putPath, _SuffixCDT.JSON_SUFFIX, replace, names);
	}

	public static void writeJsonFiles(String packPath, String putPath, String... names) {
		writeFiles(packPath, putPath, _SuffixCDT.JSON_SUFFIX, names);
	}
}
