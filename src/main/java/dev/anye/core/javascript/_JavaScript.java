package dev.anye.core.javascript;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.Map;

public class _JavaScript {

    public static Object run(String code, Map<String,Object> map){
        try (Context context = Context.create("js")){
            // 获取 JavaScript 的绑定对象
            Value bindings = context.getBindings("js");
            map.forEach(bindings::putMember);
            return context.eval("js", code);
            //return engine.eval(code);
        }
    }
}
