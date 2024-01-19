package example.model;

import example.IdAware;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import metagen.Meta;

import javax.persistence.Column;
import javax.persistence.Id;
import java.io.Serializable;

import static metagen.Meta.EnumType.NONE;

@Data
@SuperBuilder
@Meta(properties = @Meta.Props(value = NONE))
abstract class Entity<ID extends Serializable & Comparable<ID>> implements IdAware<ID> {
    @Id
    @Column(name = "ID")
    public ID id;
}
