package io.github.m4gshm.meta;

/**
 * Write property accessor.
 * @param <T> bean type
 * @param <V> property type
 */
public interface Write<T, V> {
    void set(T bean, V value);
}
