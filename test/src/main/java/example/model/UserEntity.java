package example.model;


import example.IdAware;
import lombok.*;
import matador.Meta;

import static lombok.AccessLevel.NONE;

@Meta
@Data
@EqualsAndHashCode(callSuper = true)
public class UserEntity extends Entity<Long> implements IdAware<Long> {
    @Getter(NONE)
    @Setter(NONE)
    public final Address address;
    String name;
    Integer age;

    @Meta
    public static record Address(String postalCode, String city, String street) {
    }
}
