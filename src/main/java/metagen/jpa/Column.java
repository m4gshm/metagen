package metagen.jpa;

import metagen.Typed;

public interface Column<T> extends Typed<T> {

    boolean pk();

}
