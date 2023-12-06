package matador;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

@Target({TYPE, PACKAGE})
@Retention(SOURCE)
public @interface Meta {
    String suffix() default "Meta";

    Fields fields() default @Fields();

    Parameters params() default @Parameters();

    boolean aggregate() default true;

    @Retention(SOURCE)
    @interface Fields {
        boolean enumerate() default true;

        String className() default "Fields";
    }

    @Retention(SOURCE)
    @interface Parameters {
        boolean enumerate() default true;

        String className() default "Params";
    }

}
