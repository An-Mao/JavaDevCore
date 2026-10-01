package dev.anye.core.dt;

/**
 * 一维结构，（x） w  (x)
 */
public interface IOneDimensionalI {
	int x();
	int w();
	default int width(){return w();}
}
