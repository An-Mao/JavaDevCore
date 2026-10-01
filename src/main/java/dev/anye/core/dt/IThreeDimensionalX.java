package dev.anye.core.dt;

/**
 * 三维结构
 * x,y,z
 * w,h,l
 *
 */
public interface IThreeDimensionalX<T> extends ITwoDimensionalX<T>{
	T z();
	T l();
	default T length(){return l();}
}
