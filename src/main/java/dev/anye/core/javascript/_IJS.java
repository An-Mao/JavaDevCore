package dev.anye.core.javascript;

import java.io.Reader;

public interface _IJS {
    Object runCode(String code);
    Object runFile(String file);
    Object runFile(Reader file);
}
