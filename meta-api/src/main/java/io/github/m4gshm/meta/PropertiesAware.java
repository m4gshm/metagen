package io.github.m4gshm.meta;

import java.util.List;

/**
 * Provides access to bean properties.
 * @param <T> bean type.
 */
public interface PropertiesAware<T> {
    List<? extends Typed<?>> properties();

}
