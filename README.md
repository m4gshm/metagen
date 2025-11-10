# Metagen (under construction)

Enumerated constants generator, based on bean props and type parameters.

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
Unresolved directive in readme.adoc - include::../../../test/build/generated/sources/annotationProcessor/java/main/example/simple/UserMeta.java[]
```

``` java
Unresolved directive in readme.adoc - include::../../../test/build/generated/sources/annotationProcessor/java/main/example/simple/UserAddressMeta.java[]
```
