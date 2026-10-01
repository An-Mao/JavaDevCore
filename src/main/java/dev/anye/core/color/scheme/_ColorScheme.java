package dev.anye.core.color.scheme;

import dev.anye.core.color.IStateColor;
import dev.anye.core.color._StateColors;

public class _ColorScheme implements IColorScheme{
	public static final _ColorScheme DEFAULT = new _ColorScheme(
					new _StateColors(0xFF000000, 0xFF000000),
					new _StateColors(0xFFFFFFFF, 0xFF0000FF),
					new _StateColors(0x77000000, 0x77000000),
					new _StateColors(0xFF000000, 0xFF000000),
					new _StateColors(0xFFFFFFFF, 0xFF0000FF),
					new _StateColors(0x77000000, 0x77000000));
	final _StateColors border;
	final _StateColors text;
	final _StateColors background;
	final _StateColors elementBorder;
	final _StateColors elementText;
	final _StateColors elementBackground;

	public _ColorScheme(
			_StateColors border,_StateColors text, _StateColors background,
			_StateColors elementBorder,_StateColors elementText,_StateColors elementBackground
	){
		this.border = border;
		this.text = text;
		this.background = background;
		this.elementBorder = elementBorder;
		this.elementText = elementText;
		this.elementBackground = elementBackground;
	}
	public _ColorScheme(
			_StateColors border,_StateColors text, _StateColors background
	){
		this(border,text,background,border,text,background);
	}

	@Override
	public IStateColor border() {
		return border;
	}

	@Override
	public IStateColor text() {
		return text;
	}

	@Override
	public IStateColor background() {
		return background;
	}

	@Override
	public IStateColor elementBorder() {
		return elementBorder;
	}

	@Override
	public IStateColor elementText() {
		return elementText;
	}

	@Override
	public IStateColor elementBackground() {
		return elementBackground;
	}

}
