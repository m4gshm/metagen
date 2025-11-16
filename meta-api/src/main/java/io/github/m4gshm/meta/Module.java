package io.github.m4gshm.meta;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Used to generate class metadata outside project (jar dependencies of core library).
 * <pre>
 * Example:
 *
 * &#064;Module
 * class final JdbcModule {
 *     private static Statement statement;
 *     private static Connection connection;
 * }
 * </pre>
 */
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
