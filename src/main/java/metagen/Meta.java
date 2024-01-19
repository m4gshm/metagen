package metagen;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import static metagen.Meta.EnumType.FULL;

@Target({TYPE, PACKAGE})
@Retention(SOURCE)
public @interface Meta {
    String META = "Meta";

    String suffix() default META;

    Props properties() default @Props();

    Params params() default @Params();

    Builder builder() default @Builder();

    boolean aggregate() default true;

    Extend[] customizers() default {};

    enum EnumType {
        NONE,
        NAME,
        TYPE,
        FULL
    }

    @Retention(SOURCE)
    @interface Props {
        String METHOD_NAME = "properties";
        String CLASS_NAME = "Prop";

        EnumType value() default FULL;

        String className() default CLASS_NAME;

        String methodName() default METHOD_NAME;
    }

    @Retention(SOURCE)
    @interface Params {
        String METHOD_NAME = "parameters";
        String CLASS_NAME = "Param";

        EnumType value() default FULL;

        String className() default CLASS_NAME;

        String methodName() default METHOD_NAME;

        Inherited inherited() default @Inherited;

        @Retention(SOURCE)
        @interface Inherited {
            Super parentClass() default @Super;

            Interfaces interfaces() default @Interfaces;

            @Retention(SOURCE)
            @interface Super {
                String METHOD_NAME = "superParameters";
                String CLASS_NAME_SUFFIX = "Param";

                boolean enumerate() default true;

                String classNameSuffix() default CLASS_NAME_SUFFIX;

                String methodName() default METHOD_NAME;
            }

            @Retention(SOURCE)
            @interface Interfaces {
                String METHOD_NAME = "parametersOf";
                String CLASS_NAME_SUFFIX = "Param";

                boolean enumerate() default true;

                String classNameSuffix() default CLASS_NAME_SUFFIX;

                String methodName() default METHOD_NAME;
            }
        }
    }

    /**
     * Generates Lombok @Builder, @SuperBuilder meta class.
     * Experimental feature.
     */
    @Retention(SOURCE)
    @interface Builder {
        String CLASS_NAME = "BuilderMeta";

        String className() default CLASS_NAME;

        boolean detect() default true;
        boolean generateMeta() default false;
    }

    @Retention(SOURCE)
    @interface Extend {
        Class<? extends MetaCustomizer<?>> value();

        Opt[] opts() default {};

        @Retention(SOURCE)
        @interface Opt {
            String key();

            String[] value();
        }
    }
}
