package example.model;

import example.IdAware;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import matador.Meta;

import javax.persistence.Column;
import javax.persistence.Id;
import java.io.Serializable;

@Data
@SuperBuilder
@Meta(properties = @Meta.Properties(enumerate = false))
abstract class Entity<ID extends Serializable & Comparable<ID>> implements IdAware<ID> {
    @Id
    @Column(name = "ID")
    public ID id;
}
