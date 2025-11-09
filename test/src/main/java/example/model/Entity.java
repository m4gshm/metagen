package example.model;

import example.IdAware;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import meta.Meta;
import meta.Meta.Params;
import meta.Meta.Props;

import javax.persistence.Column;
import javax.persistence.Id;
import java.io.Serializable;

import static meta.Meta.Content.FULL;
import static meta.Meta.Content.NONE;

@Data
@SuperBuilder
@Meta(properties = @Props(value = NONE), params = @Params(FULL))
abstract class Entity<ID extends Serializable & Comparable<ID>> implements IdAware<ID> {
    @Id
    @Column(name = "ID")
    public ID id;
}
