package example.model.simple;


import example.IdAware;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import metagen.Meta;
import metagen.Meta.Params;
import metagen.Meta.Props;

import static lombok.AccessLevel.NONE;
import static metagen.Meta.EnumType.*;

@Data
@Meta(properties = @Props(NAME), params = @Params(TYPE))
public class UserBean implements IdAware<Long> {

    public Long id;
    public Address address;
    String name;
    Integer age;
    @Getter(NONE)
    @Setter(NONE)
    private Integer transientVersion;

    @Data
    @Meta(properties = @Props(NAME), params = @Params(Meta.EnumType.NONE))
    public static class Address {
        private final String postalCode;
        private final String city;
        private final String street;
    }
}
