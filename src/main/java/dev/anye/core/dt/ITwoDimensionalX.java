package dev.anye.core.dt;

/**
 * 二维结构
 */
public interface ITwoDimensionalX<T> extends IOneDimensionalX<T> {
	T y();
	T h();
	default T height(){return h();}
}
