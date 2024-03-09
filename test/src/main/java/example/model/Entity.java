package example.model;

import example.IdAware;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import meta.Meta;

import javax.persistence.Column;
import javax.persistence.Id;
import java.io.Serializable;

import static meta.Meta.Content.NONE;

@Data
@SuperBuilder
@Meta(properties = @Meta.Props(value = NONE))
abstract class Entity<ID extends Serializable & Comparable<ID>> implements IdAware<ID> {
    @Id
    @Column(name = "ID")
    public ID id;
}
