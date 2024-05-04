package meta;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

@Target(TYPE)
@Retention(SOURCE)
public @interface Module {

    DestinationPackage destinationPackage() default DestinationPackage.ModuleNameBased;

    enum DestinationPackage {
        ModuleNameBased,
        OfModule,
        OfClass
    }
}
