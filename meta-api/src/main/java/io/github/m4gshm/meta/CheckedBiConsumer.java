package io.github.m4gshm.meta;

/**
 * An operation that required two arguments and can throw an exception.
 * @param <T>
 * @param <U>
 * @param <E>
 */
@FunctionalInterface
public interface CheckedBiConsumer<T, U, E extends Throwable> {
    void accept(T t, U u) throws E;
}
