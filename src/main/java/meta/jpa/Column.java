package meta.jpa;

import meta.Typed;

/**
 * TODO
 * @param <T>
 */
public interface Column<T> extends Typed<T> {

    boolean pk();

}
