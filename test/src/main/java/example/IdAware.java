package example;

import metagen.Meta;

@Meta
public interface IdAware<T> {

    T getId();
}
