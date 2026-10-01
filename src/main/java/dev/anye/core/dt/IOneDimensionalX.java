package dev.anye.core.dt;

/**
 * 一维结构
 */
public interface IOneDimensionalX<T> {
	T x();
	T w();
	default T width(){return w();}
}
