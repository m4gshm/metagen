package example.model;

import matador.Meta;

@Meta
public interface IdAware<T> {

    T getId();
}
