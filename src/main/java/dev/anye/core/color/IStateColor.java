package dev.anye.core.color;

public interface IStateColor {
	IFadeColor normal();
	default IFadeColor hover(){return normal();}
	default IFadeColor selected(){return normal();}
}
