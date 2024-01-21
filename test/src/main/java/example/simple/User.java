package example.simple;


import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import meta.Meta;
import meta.Meta.Params;
import meta.Meta.Props;

import static lombok.AccessLevel.NONE;
import static meta.Meta.EnumType.NAME;
import static meta.Meta.EnumType.TYPE;

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
