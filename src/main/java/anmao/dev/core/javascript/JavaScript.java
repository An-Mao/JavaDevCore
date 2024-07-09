package anmao.dev.core.javascript;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import java.util.Map;

public class JavaScript {

    public static Object run(String code, Map<String,Object> map){
        ScriptEngineFactory sef = new NashornScriptEngineFactory();
        ScriptEngine engine = sef.getScriptEngine();
        try {
            map.forEach(engine::put);
            return engine.eval(code);
        } catch (ScriptException e) {
            throw new RuntimeException (e.getMessage());
        }
    }
}
