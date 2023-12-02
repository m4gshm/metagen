package example.model;

import lombok.Data;
import matador.Meta;

import java.io.Serializable;

@Data
@Meta
abstract class Entity<ID extends Serializable & Comparable<ID>> {
    public ID id;
}
