package example;

import metagen.Meta;

import java.io.Serializable;

@Meta
public interface IdAware<T extends Serializable> {

    T getId();
}
