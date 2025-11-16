package example;

import io.github.m4gshm.meta.Meta;

import java.io.Serializable;

@Meta
public interface IdAware<ID extends Serializable> {

    ID getId();
}
