package example;

import metagen.Meta;

import java.io.Serializable;

@Meta
public interface IdAware<ID extends Serializable> {

    ID getId();
}
