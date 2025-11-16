package io.github.m4gshm.meta;

/**
 * Metadata element interface.
 * @param <T> the element type.
 */
public interface Typed<T> {

    String name();

    Class<T> type();
}
