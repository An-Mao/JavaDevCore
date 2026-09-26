package dev.anye.core.dt;

/**
 * 二维结构
 */
public interface ITwoDimensional extends IOneDimensional {
	float y();
	float h();
	default float height(){return h();}
}
