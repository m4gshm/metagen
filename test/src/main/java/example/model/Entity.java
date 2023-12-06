package example.model;

import example.IdAware;
import lombok.Data;
import matador.Meta;

import java.io.Serializable;

@Data
@Meta
abstract class Entity<ID extends Serializable & Comparable<ID>> implements IdAware<ID> {
    public ID id;
}
