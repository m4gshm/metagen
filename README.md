# Metagen (under construction)

Enumerated constants generator based on bean properties and type
parameters.

Input:

``` java
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
```

``` java
package example.simple;

import meta.Meta;

@Meta
public interface IdAware<ID> {

    ID getId();
}
```

Output:

``` java
package example.simple;

import java.lang.Class;
import java.lang.Long;
import java.lang.String;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("meta.Meta")
public final class UserMeta {
  UserMeta() {
  }

  public static class IdAwareParam {
    public static final Class<Long> ID = Long.class;
  }

  public static class Prop {
    public static final String id = "id";

    public static final String address = "address";

    public static final String name = "name";

    public static final String age = "age";

    private static final List<String> values = List.of(id, address, name, age);

    public static final List<String> values() {
      return values;
    }
  }
}
```

``` java
package example.simple;

import java.lang.String;
import java.util.List;
import javax.annotation.processing.Generated;
import meta.ParametersAware;

@Generated("meta.Meta")
public final class UserAddressMeta implements ParametersAware<User.Address> {
  UserAddressMeta() {
  }

  public static class Prop {
    public static final String postalCode = "postalCode";

    public static final String city = "city";

    public static final String street = "street";

    private static final List<String> values = List.of(postalCode, city, street);

    public static final List<String> values() {
      return values;
    }
  }
}
```
