package example.simple;


import io.github.m4gshm.meta.Meta.Props;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import io.github.m4gshm.meta.Meta;
import io.github.m4gshm.meta.Meta.Params;

import static lombok.AccessLevel.NONE;
import static io.github.m4gshm.meta.Meta.Content.NAME;
import static io.github.m4gshm.meta.Meta.Content.TYPE;

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

    @Meta
    public record Address(String postalCode, String city, String street) {
        public String getFullAddress() {
            return postalCode + ", " + city + ", " + street;
        }
    }
}
