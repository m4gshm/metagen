package example.simple;


import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import metagen.Meta;
import metagen.Meta.EnumType;
import metagen.Meta.Params;
import metagen.Meta.Props;

import static lombok.AccessLevel.NONE;
import static metagen.Meta.EnumType.NAME;
import static metagen.Meta.EnumType.TYPE;

@Data
@Meta(properties = @Props(NAME), params = @Params(TYPE))
public class User implements IdAware<Long> {

    public Long id;
    public Address address;
    String name;
    Integer age;
    @Getter(NONE)
    @Setter(NONE)
    private Integer version; // excluded private field

    @Data
    @Meta(properties = @Props(NAME))
    public static class Address {
        private final String postalCode;
        private final String city;
        private final String street;
    }
}
