package dev.anye.core.array;

public class _StaticArray3D<K, A, B> {
	private final K[] keys;
	private final A[] as;
	private final B[] bs;

	public _StaticArray3D(K[] keys, A[] as, B[] bs) {
		if (keys.length != as.length || keys.length != bs.length) throw new IllegalArgumentException();
		this.keys = keys;
		this.as = as;
		this.bs = bs;
	}

	public _StaticArray3D(_StaticArray3D<K, A, B> array3D) {
		this.keys = array3D.keys;
		this.as = array3D.as;
		this.bs = array3D.bs;
	}
}
