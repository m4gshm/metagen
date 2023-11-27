package example;

import meta.Meta;

import java.io.Serializable;

@Meta
public interface IdAware<ID extends Serializable> {

    ID getId();
}
