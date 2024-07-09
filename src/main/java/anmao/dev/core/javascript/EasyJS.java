package anmao.dev.core.javascript;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.Map;

public class EasyJS {
    private final ScriptEngineFactory sef;
    private final ScriptEngine engine;


    public static EasyJS creat(){
        return new EasyJS();
    }
    public EasyJS(){
        sef = new NashornScriptEngineFactory();
        engine = sef.getScriptEngine();
    }
    public ScriptEngine getEngine() {
        return engine;
    }
    public EasyJS addParameter(String name , Object value){
        this.engine.put(name,value);
        return this;
    }
    public EasyJS setParameter(Map<String,Object> map){
        map.forEach(engine::put);
        return this;
    }
    public Object runCode(String code){
        try {
            return engine.eval(code);
        } catch (ScriptException e) {
            throw new RuntimeException(e);
        }
    }
    public Object runFile(String file){
        try {
            return engine.eval(new FileReader(file));
        } catch (FileNotFoundException | ScriptException e) {
            throw new RuntimeException(e);
        }
    }
    public Object runFile(Reader file){
        try {
            return engine.eval(file);
        } catch (ScriptException e) {
            throw new RuntimeException(e);
        }
    }

}
