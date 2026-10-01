package dev.anye.core.dt;

/**
 * 二维结构
 */
public interface ITwoDimensionalI extends IOneDimensionalI {
	int y();
	int h();
	default int height(){return h();}
}
