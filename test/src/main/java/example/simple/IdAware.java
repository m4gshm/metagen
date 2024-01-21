package example.simple;

import metagen.Meta;

@Meta
public interface IdAware<ID> {

    ID getId();
}
