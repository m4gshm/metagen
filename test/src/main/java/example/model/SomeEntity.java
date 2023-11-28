package example.model;


import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import matador.Meta;

@Meta
@Data
public class SomeEntity {
    public long id;
    String name;
    Integer age;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    public final Address address;

    @Meta
    public static record Address(String postalCode, String city, String street) {

    }
}
