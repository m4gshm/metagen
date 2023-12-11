package matador;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

@Target({TYPE, PACKAGE})
@Retention(SOURCE)
public @interface Meta {

    String META = "Meta";

    String suffix() default META;

    Properties properties() default @Properties();

    Parameters params() default @Parameters();

    boolean aggregate() default true;

    @Retention(SOURCE)
    @interface Properties {
        boolean enumerate() default true;

        String className() default "Props";
        String methodName() default "properties";
    }

    @Retention(SOURCE)
    @interface Parameters {
        boolean enumerate() default true;

        String className() default "Params";

        String methodName() default "parameters";

        Super inherited() default @Super();

        @Retention(SOURCE)
        @interface Super {
            boolean enumerate() default true;

            String className() default "SuperParams";

            String methodName() default "superParameters";
        }
    }

}
