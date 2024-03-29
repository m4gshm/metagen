package example.simple;


import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import meta.Meta;
import meta.Meta.Params;
import meta.Meta.Props;

import static lombok.AccessLevel.NONE;
import static meta.Meta.Content.NAME;
import static meta.Meta.Content.TYPE;

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

    @Meta(properties = @Props(NAME))
    public record Address(String postalCode, String city, String street) {
        public String getFullAddress() {
            return postalCode + ", " + city + ", " + street;
        }
    }
}
