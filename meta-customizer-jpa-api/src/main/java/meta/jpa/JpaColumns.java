package meta.jpa;


import java.lang.annotation.Retention;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

public interface JpaColumns {
    String OPT_CLASS_NAME = "className";
    String OPT_IMPLEMENTS = "implements";
    String OPT_WITH_SUPERCLASS_COLUMNS = "withSuperclassColumns";
    String OPT_CHECK_FOR_ENTITY_ANNOTATION = "checkForEntityAnnotation";
    String[] DEFAULT_WITH_SUPERCLASS_COLUMNS = new String[]{TRUE.toString()};
    String[] DEFAULT_CHECK_FOR_ENTITY_ANNOTATION = new String[]{FALSE.toString()};
    String[] DEFAULT_CLASS_NAME = new String[]{"JpaColumn"};
    Class[] DEFAULT_IMPLEMENTS = new Class[]{Column.class};

    @Retention(SOURCE)
    @interface Exclude {

    }
}
