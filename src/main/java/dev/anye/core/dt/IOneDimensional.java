package dev.anye.core.dt;

/**
 * 一维结构
 */
public interface IOneDimensional {
	float x();
	float w();
	default float width(){return w();}
}
