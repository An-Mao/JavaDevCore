package dev.anye.core.other;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public record _TransferRecord<T>(Optional<T> transfer) {

	public T get() {
		return transfer.get();
	}

	public T orElse(T other) {
		return transfer.orElse(other);
	}

	public boolean isPresent() {
		return transfer.isPresent();
	}

	public <U> Optional<U> map(Function<? super T, ? extends U> mapper) {
		return transfer.map(mapper);
	}

	public T orElseThrow() {
		return transfer.orElseThrow();
	}

	public boolean isEmpty() {
		return transfer.isEmpty();
	}

	public Optional<T> filter(Predicate<? super T> predicate) {
		return transfer.filter(predicate);
	}

	public <U> Optional<U> flatMap(Function<? super T, Optional<? extends U>> mapper) {
		return transfer.flatMap(mapper);
	}

	public void ifPresentOrElse(Consumer<? super T> action, Runnable emptyAction) {
		transfer.ifPresentOrElse(action, emptyAction);
	}

	public T orElseGet(Supplier<? extends T> supplier) {
		return transfer.orElseGet(supplier);
	}

	public void ifPresent(Consumer<T> consumer) {
		transfer.ifPresent(consumer);
	}
}
