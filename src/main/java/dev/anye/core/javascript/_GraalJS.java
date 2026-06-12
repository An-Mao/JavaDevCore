package dev.anye.core.javascript;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.Reader;
import java.util.Map;

public class _GraalJS extends _JavaScript<_GraalJS, Source> {
	private final Context context;
	private final Value bindings;

	public static _GraalJS NotSafe(boolean cache) {
		return new _GraalJS(Context.newBuilder("js")
				.allowAllAccess(true)
				.option("engine.WarnInterpreterOnly", "false")
				.build(), cache);
	}

	public _GraalJS(Context context, Value bindings, boolean cache) {
		super(cache);
		this.context = context;
		this.bindings = bindings;
	}

	public _GraalJS(Context context, boolean cache) {
		this(context, context.getBindings("js"), cache);
	}

	public _GraalJS(boolean cache) {
		super(cache);
		context = Context.create("js");
		bindings = context.getBindings("js");
	}

	public Context getEngine() {
		return context;
	}

	@Override
	public _GraalJS addParameter(String name, Object value) {
		this.bindings.putMember(name, value);
		return this;
	}

	@Override
	public _GraalJS setParameter(Map<String, Object> map) {
		map.forEach(bindings::putMember);
		return this;
	}

	@Override
	public Source getJsData(Reader reader) {
		try {
			return Source.newBuilder("js", reader, "").build();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Source getJsData(String code) {
		return Source.create("js", code);
	}

	@Override
	public Object runCode(Source code) {
		return context.eval(code);
	}

	;

}
