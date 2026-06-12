package dev.anye.core.map;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.BiConsumer;

public class _PriorityMap<K, V> {
	private final Map<K, PriorityQueue<V>> map = new HashMap<>();
	private final Comparator<V> comparator;

	public _PriorityMap(Comparator<V> comparator) {
		this.comparator = comparator;
	}

	public void put(K key, V value) {
		if (!containsKey(key)) map.put(key, new PriorityQueue<>(comparator));
		map.get(key).add(value);
	}

	public PriorityQueue<V> get(K key) {
		return map.get(key);
	}

	public int size() {
		return map.size();
	}

	public void forEach(BiConsumer<? super K, ? super PriorityQueue<V>> action) {
		map.forEach(action);
	}

	public boolean containsKey(K key) {
		return map.containsKey(key);
	}

	public Map<K, PriorityQueue<V>> getMap() {
		return map;
	}
}
