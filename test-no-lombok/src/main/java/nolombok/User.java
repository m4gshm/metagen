package nolombok;


import io.github.m4gshm.meta.Meta;
import io.github.m4gshm.meta.Meta.Params;
import io.github.m4gshm.meta.Meta.Props;

import static io.github.m4gshm.meta.Meta.Content.NAME;
import static io.github.m4gshm.meta.Meta.Content.TYPE;

@Meta(properties = @Props(NAME), params = @Params(TYPE))
public class User implements IdAware<Long> {

    public Long id;
    public Address address;
    private String name;
    private Integer age;
    private Integer version;

    @Override
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    @Meta(properties = @Props(NAME))
    public record Address(String postalCode, String city, String street) {
        public String getFullAddress() {
            return postalCode + ", " + city + ", " + street;
        }
    }
}
