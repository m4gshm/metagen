package matador.jpa;

import matador.Typed;

public interface Column<T> extends Typed<T> {

    boolean pk();

    String path();
}
