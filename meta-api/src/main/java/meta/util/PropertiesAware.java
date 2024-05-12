package meta.util;

import java.util.List;

/**
 * Provides access to bean properties.
 * @param <T> bean type.
 */
public interface PropertiesAware<T> {
    List<? extends Typed<?>> properties();

}
