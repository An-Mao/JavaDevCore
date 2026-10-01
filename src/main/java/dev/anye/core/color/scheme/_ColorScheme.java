package dev.anye.core.color.scheme;

import dev.anye.core.color._StateColors;

public record _ColorScheme(
		_StateColors border,
		_StateColors text,
		_StateColors background,
		_StateColors elementBorder,
		_StateColors elementText,
		_StateColors elementBackground) implements ISimpleColorScheme,IElementsSimpleColorScheme {

}
