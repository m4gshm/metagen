package example.model;

import lombok.Data;
import matador.Meta;

@Data
@Meta
abstract class Entity<ID> {
    public ID id;
}
