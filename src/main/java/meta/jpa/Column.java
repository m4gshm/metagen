package meta.jpa;

import meta.Typed;

public interface Column<T> extends Typed<T> {

    boolean pk();

}
