package example.model;

import example.IdAware;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import io.github.m4gshm.meta.Meta;
import io.github.m4gshm.meta.Meta.Params;

import javax.persistence.Column;
import javax.persistence.Id;
import java.io.Serializable;

import static io.github.m4gshm.meta.Meta.Content.FULL;
import static io.github.m4gshm.meta.Meta.Content.NONE;

@Data
@SuperBuilder
@Meta(properties = @Meta.Props(value = NONE), params = @Params(FULL))
abstract class Entity<ID extends Serializable & Comparable<ID>> implements IdAware<ID> {
    @Id
    @Column(name = "ID")
    public ID id;
}
