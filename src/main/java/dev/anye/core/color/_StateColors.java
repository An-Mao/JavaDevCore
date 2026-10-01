package dev.anye.core.color;


public record _StateColors(FadeColorData normal, FadeColorData hover, FadeColorData selected) implements IStateColor {
	public _StateColors() {
		this(FadeColorData.EMPTY);
	}
	public _StateColors(FadeColorData usualColor) {
		this(usualColor, usualColor);
	}
	public _StateColors(FadeColorData usualColor, FadeColorData hoverColor) {
		this(usualColor, hoverColor, usualColor);
	}
	public _StateColors(int usualColor) {
		this(usualColor, usualColor);
	}
	public _StateColors(int usualColor, int hoverColor) {
		this(FadeColorData.create(usualColor), FadeColorData.create(hoverColor), FadeColorData.create(usualColor));
	}
}