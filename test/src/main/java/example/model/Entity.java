package example.model;

import example.IdAware;
import lombok.Data;
import matador.Meta;

import java.io.Serializable;

@Data
@Meta(fields = @Meta.Fields(enumerate = false))
abstract class Entity<ID extends Serializable & Comparable<ID>> implements IdAware<ID> {
    public ID id;
}
