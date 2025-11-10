package io.github.m4gshm.meta.jpa;

import io.github.m4gshm.meta.Typed;

/**
 * JPA column metadata interface.
 *
 * @param <T> bean type
 */
public interface Column<T> extends Typed<T> {

    boolean pk();

}
