# Metagen (under construction)

Enumerated constants generator based on bean properties and type
parameters.

Input:

``` java
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
```

Output: Input:

``` java
package example.model.simple;

import java.lang.Class;
import java.lang.Long;
import java.lang.String;
import java.util.List;
import javax.annotation.processing.Generated;
import metagen.Meta;

@Generated("Meta")
public final class UserBeanMeta {
  UserBeanMeta() {
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

    package example.model.simple;

    import java.lang.String;
    import java.util.List;
    import javax.annotation.processing.Generated;
    import metagen.Meta;

    @Generated("Meta")
    public final class UserBeanAddressMeta {
      UserBeanAddressMeta() {
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
