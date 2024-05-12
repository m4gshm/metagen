package meta.jpa;

import meta.util.Typed;

/**
 * JPA column metadata interface.
 *
 * @param <T> bean type
 */
public interface Column<T> extends Typed<T> {

    boolean pk();

}
