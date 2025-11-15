package io.github.m4gshm.meta;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Function;

import static io.github.m4gshm.meta.Meta.ConstantNameStrategy.AS_IS;
import static io.github.m4gshm.meta.Meta.Content.NAME;
import static io.github.m4gshm.meta.Meta.Methods.Content.NONE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Applies the metadata generator for a marked class.
 * The generator creates a metadata class contains information about bean properties and type parameters of the marked class.
 */
@Target({TYPE, FIELD})
@Retention(SOURCE)
public @interface Meta {

    String META = "Meta";

    /**
     * The metadata class name consist of the marked class name followed by a suffix.
     *
     * @return suffix of the metadata.
     */
    String suffix() default META;

    /**
     * @return options of the properties part metadata.
     */
    Props properties() default @Props(NAME);

    /**
     * @return options of the type parameters part metadata.
     */
    Params params() default @Params(Content.NONE);

    /**
     * @return options of the class methods part metadata.
     */
    Methods methods() default @Methods(NONE);

    /**
     * @return options of the Lombok builder part metadata.
     */
    Builder builder() default @Builder(generateMeta = false);

    /**
     * Indicates that the generated metadata is included into a package level aggregator class.
     * The aggregator class is named as the package name, capitalized.
     * The generated metadata class must implement {@link MetaModel}.
     *
     * @return true if the metadata should be aggregated.
     */
    boolean aggregate() default true;

    /**
     * Additional metadata customizers that modify or append generated code.
     *
     * @return one or any customizer options {@link Extend}.
     */
    Extend[] customizers() default {};

    /**
     * Specifies the metadata content to be generated.
     */
    enum Content {
        /**
         * generate nothing
         */
        NONE,
        /**
         * only names of properties or parameters
         */
        NAME,
        /**
         * only types of properties or parameters
         */
        TYPE,
        /**
         * names, types, accessors of properties
         * names, types of type parameters
         * inherited class and interfaces parameters
         */
        FULL
    }

    enum ConstantNameStrategy {
        AS_IS,
        SNAKE_CASE,
        UPPER_SNAKE_CASE;
    }

    /**
     * Describes the properties part of the metadata.
     */
    @Retention(SOURCE)
    @interface Props {
        String METHOD_NAME = "properties";
        String CLASS_NAME = "Prop";

        Content value() default Content.NAME;

        String className() default CLASS_NAME;

        String methodName() default METHOD_NAME;

        ConstantNameStrategy constName() default AS_IS;

        Class<? extends Function<String, String>> nameValueCustomizer() default AsIs.class;

        interface AsIs extends Function<String, String> {

        }
    }

    /**
     * Describes the type parameters part of the metadata.
     */
    @Retention(SOURCE)
    @interface Params {
        String METHOD_NAME = "parameters";
        String CLASS_NAME = "Param";

        Content value();

        String className() default CLASS_NAME;

        String methodName() default METHOD_NAME;

        Inherited inherited() default @Inherited(
                parentClass = @Inherited.Super(enumerate = true), interfaces = @Inherited.Interfaces(enumerate = true));

        /**
         * Options of inherited type parameters.
         */
        @Retention(SOURCE)
        @interface Inherited {
            Super parentClass();

            Interfaces interfaces();

            @Retention(SOURCE)
            @interface Super {
                String METHOD_NAME = "superParameters";
                String CLASS_NAME_SUFFIX = "Param";

                boolean enumerate();

                String classNameSuffix() default CLASS_NAME_SUFFIX;

                String methodName() default METHOD_NAME;
            }

            @Retention(SOURCE)
            @interface Interfaces {
                String METHOD_NAME = "parametersOf";
                String CLASS_NAME_SUFFIX = "Param";

                boolean enumerate();

                String classNameSuffix() default CLASS_NAME_SUFFIX;

                String methodName() default METHOD_NAME;
            }
        }
    }

    /**
     * Describes the class methods part of the metadata.
     */
    @Retention(SOURCE)
    @interface Methods {
        String CLASS_NAME = "Method";

        Content value();

        String className() default CLASS_NAME;

        /**
         * Specifies the metadata content to be generated.
         */
        enum Content {
            /**
             * generate nothing
             */
            NONE,
            /**
             * only names of methods
             */
            NAME,
        }
    }

    /**
     * Generates metadata of the Lombok @Builder, @SuperBuilder features.
     * Experimental feature.
     */
    @Retention(SOURCE)
    @interface Builder {
        String CLASS_NAME = "BuilderMeta";

        String className() default CLASS_NAME;

        boolean detect() default true;

        boolean generateMeta();
    }

    /**
     * Options of a metadata customizer.
     */
    @Retention(SOURCE)
    @interface Extend {
        /**
         * @return a customizer's interface that implementation is instantiated by the {@link java.util.ServiceLoader}.
         */
        Class<?> value();

        Opt[] opts() default {};

        @Retention(SOURCE)
        @interface Opt {
            String key();

            String[] value();
        }
    }

    /**
     * Excludes the marked property or type parameter from the generated metadata part.
     */
    @Retention(SOURCE)
    @Target({METHOD, FIELD, RECORD_COMPONENT})
    @interface Exclude {

    }

}
