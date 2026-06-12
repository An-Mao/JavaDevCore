package dev.anye.core.color.scheme;

import java.util.HashMap;

public interface _ColorSchemeInterface {
	HashMap<String, _ColorScheme.Color> getColors();

	default void addColor(String index, _ColorScheme.Color color) {
		getColors().put(index, color);
	}

	default void removeColor(String index) {
		getColors().remove(index);
	}

	_ColorScheme.Color getColor(String index);

	default int getUsualColor(String index) {
		return getColor(index).UsualColor();
	}

	default int getHoverColor(String index) {
		return getColor(index).HoverColor();
	}

	default int getSelectColor(String index) {
		return getColor(index).SelectColor();
	}
}
