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

        String METHOD_NAME = "properties";
        String CLASS_NAME = "Props";

        boolean enumerate() default true;

        String className() default CLASS_NAME;
        String methodName() default METHOD_NAME;
    }

    @Retention(SOURCE)
    @interface Parameters {

        String METHOD_NAME = "parameters";
        String CLASS_NAME = "Params";

        boolean enumerate() default true;

        String className() default CLASS_NAME;

        String methodName() default METHOD_NAME;

        Super inherited() default @Super();

        @Retention(SOURCE)
        @interface Super {

            String METHOD_NAME = "superParameters";
            String CLASS_NAME = "SuperParams";

            boolean enumerate() default true;

            String className() default CLASS_NAME;

            String methodName() default METHOD_NAME;
        }
    }

}
