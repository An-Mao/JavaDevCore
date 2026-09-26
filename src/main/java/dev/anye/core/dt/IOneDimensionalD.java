package dev.anye.core.dt;

/**
 * 一维结构
 */
public interface IOneDimensionalD {
	double x();
	double w();
	default double width(){return w();}
}
