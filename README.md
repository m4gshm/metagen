# Metagen (under construction)

Enumerated constants generator, based on bean properties and type
parameters.

Requires Java 17 or higher.

## Install

### Gradle (Kotlin syntax)

Add the code below to your `build.gradle.kts`

``` kotlin
repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("io.github.m4gshm:metagen-processor:0.0.1-rc4")
    implementation("io.github.m4gshm:metagen-api:0.0.1-rc4")
}
```

## Minimal usage example

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
```

``` java
package example.simple;

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
  public final Class<User> type = User.class;

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

import java.lang.Class;
import java.lang.String;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("meta.Meta")
public final class UserAddressMeta {
  public final Class<User.Address> type = User.Address.class;

  UserAddressMeta() {
  }

  public static class Prop {
    public static final String postalCode = "postalCode";

    public static final String city = "city";

    public static final String street = "street";

    public static final String fullAddress = "fullAddress";

    private static final List<String> values = List.of(postalCode, city, street, fullAddress);

    public static final List<String> values() {
      return values;
    }
  }
}
```
