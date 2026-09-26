package dev.anye.core.dt;

/**
 * 二维结构
 */
public interface ITwoDimensionalD extends IOneDimensional {
	double y();
	double h();
	default double height(){return h();}
}
