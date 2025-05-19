package dev.anye.core.javascript;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;

public class _EasyJS {
    private final Context context;
    private final Value bindings;


    public static _EasyJS creat(){
        return new _EasyJS();
    }
    public static _EasyJS NotSafe(){
        return new _EasyJS( Context.newBuilder("js")
                .allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false")
                .build());
    }
    public _EasyJS(Context context,Value bindings){
        this.context = context;
        this.bindings = bindings;
    }
    public _EasyJS(Context context){
        this.context = context;
        this.bindings = context.getBindings("js");
    }
    public _EasyJS(){
        context = Context.create("js");
        bindings = context.getBindings("js");
    }
    public Context getEngine() {
        return context;
    }
    public _EasyJS addParameter(String name , Object value){
        this.bindings.putMember(name,value);
        return this;
    }
    public _EasyJS setParameter(Map<String,Object> map){
        map.forEach(bindings::putMember);
        return this;
    }
    public Object runCode(String code){
        return context.eval("js",code);
    }
    public Object runFile(String file){
        try {
            return context.eval(Source.newBuilder("js",new FileReader(file),"").build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public Object runFile(Reader file){
        try {
            return context.eval(Source.newBuilder("js",file,"").build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
