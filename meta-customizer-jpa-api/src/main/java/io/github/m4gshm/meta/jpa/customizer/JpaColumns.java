package io.github.m4gshm.meta.jpa.customizer;


import lombok.RequiredArgsConstructor;
import io.github.m4gshm.meta.jpa.Column;

import java.lang.annotation.Retention;
import java.util.function.Function;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import static io.github.m4gshm.meta.jpa.customizer.JpaColumns.GeneratedColumnNamePostProcess.PostProcessors.noop;

public interface JpaColumns {
    String OPT_CLASS_NAME = "className";
    String OPT_IMPLEMENTS = "implements";
    String OPT_WITH_SUPERCLASS_COLUMNS = "withSuperclassColumns";
    String OPT_CHECK_FOR_ENTITY_ANNOTATION = "checkForEntityAnnotation";
    /**
     * A class that implements the GeneratedColumnNamePostProcess interface or one of the values: noop, toUpperCase, toLowerCase
     */
    String OPT_GENERATED_COLUMN_NAME_POST_PROCESS = "generatedColumnNamePostProcess";

    String[] DEFAULT_OPT_GENERATED_COLUMN_NAME_POST_PROCESS = new String[]{noop.name()};
    String[] DEFAULT_WITH_SUPERCLASS_COLUMNS = new String[]{TRUE.toString()};
    String[] DEFAULT_CHECK_FOR_ENTITY_ANNOTATION = new String[]{FALSE.toString()};
    String[] DEFAULT_CLASS_NAME = new String[]{"JpaColumn"};
    Class[] DEFAULT_IMPLEMENTS = new Class[]{Column.class};

    @Retention(SOURCE)
    @interface Exclude {

    }

    interface GeneratedColumnNamePostProcess extends Function<String, String> {
        @RequiredArgsConstructor
        enum PostProcessors implements GeneratedColumnNamePostProcess {
            noop(s -> s),
            toUpperCase(s -> s != null ? s.toUpperCase() : s),
            toLowerCase(s -> s != null ? s.toLowerCase() : s);

            private final Function<String, String> function;

            @Override
            public String apply(String s) {
                return function.apply(s);
            }
        }
    }
}
