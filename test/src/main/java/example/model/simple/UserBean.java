package example.model.simple;


import example.IdAware;
import lombok.Data;
import metagen.Meta;
import metagen.Meta.Params;
import metagen.Meta.Props;

import static metagen.Meta.EnumType.NAME;
import static metagen.Meta.EnumType.TYPE;

@Data
@Meta(properties = @Props(NAME), params = @Params(TYPE))
public class UserBean<ID extends Long> implements IdAware<ID> {

    public ID id;
    public Address address;
    String name;
    Integer age;
    private Tag[] tags;

    @Data
    public static class Address {
        private String postalCode;
        private String city;
        private String street;
    }

    @Data
    public static class Tag {
        private String tagValue;
    }
}
