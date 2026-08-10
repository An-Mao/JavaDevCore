package dev.anye.core.array;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

public class _Array3D<K, A, B> implements Iterable<_Array3D<K, A, B>.Entry> {
	private final List<K> keys;
	private final List<A> as;
	private final List<B> bs;
	private int size = 0;

	public _Array3D() {
		keys = new ArrayList<>();
		as = new ArrayList<>();
		bs = new ArrayList<>();
		size = 0;
	}

	public _Array3D(List<K> keys, List<A> as, List<B> bs) {
		this.keys = keys;
		this.as = as;
		this.bs = bs;
		if (keys.size() != as.size() || keys.size() != bs.size()) throw new IllegalArgumentException();
		size = keys.size();
	}

	public _Array3D(_Array3D<K, A, B> array3D) {
		this.keys = array3D.keys;
		this.as = array3D.as;
		this.bs = array3D.bs;
		size = array3D.size;
	}

	public int getSize() {
		return size;
	}

	public int getIndex(K key) {
		return keys.indexOf(key);
	}

	public A getA(int i) {
		return as.get(i);
	}

	public B getB(int i) {
		return bs.get(i);
	}

	public K getKey(int i) {
		return keys.get(i);
	}

	public A getA(K key) {
		return getA(getIndex(key));
	}

	public B getB(K key) {
		return getB(getIndex(key));
	}

	public List<A> getAs() {
		return as;
	}

	public List<B> getBs() {
		return bs;
	}

	public List<K> getKeys() {
		return keys;
	}

	public _Array3D<K, A, B> copy() {
		return new _Array3D<>(this);
	}

	public int add(K key, A a, B b) {
		if (keys.contains(key)) {
			int i = keys.indexOf(key);
			as.set(i, a);
			bs.set(i, b);
			return i;
		} else {
			keys.add(key);
			int i = keys.indexOf(key);
			if (as.size() == i) as.add(a);
			else throw new IllegalArgumentException();
			if (bs.size() == i) bs.add(b);
			else throw new IllegalArgumentException();
			size = keys.size();
			return i;
		}
	}

	public void remove(K key) {
		int i = keys.indexOf(key);
		keys.remove(i);
		as.remove(i);
		bs.remove(i);
		size--;
	}

	@Override
	public Iterator<Entry> iterator() {
		return new Array3DIterator();
	}

	public class Entry {
		public final K key;
		public final A a;
		public final B b;

		public Entry(K key, A a, B b) {
			this.key = key;
			this.a = a;
			this.b = b;
		}
	}

	private class Array3DIterator implements Iterator<Entry> {
		private int currentIndex = 0;

		@Override
		public boolean hasNext() {
			return currentIndex < size;
		}

		@Override
		public Entry next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			Entry entry = new Entry(keys.get(currentIndex), as.get(currentIndex), bs.get(currentIndex));
			currentIndex++;
			return entry;
		}
	}

	@Override
	public void forEach(Consumer<? super Entry> action) {
		for (Entry entry : this) {
			action.accept(entry);
		}
	}


	public void clear() {
		keys.clear();
		as.clear();
		bs.clear();
		size = 0;
	}
}
