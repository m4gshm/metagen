package example.model;


import lombok.*;
import matador.Meta;

@Meta
@Data
@EqualsAndHashCode(callSuper = true)
public class UserEntity extends Entity<Long> {
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    public final Address address;
    String name;
    Integer age;

    @Meta
    public static record Address(String postalCode, String city, String street) {
    }
}
