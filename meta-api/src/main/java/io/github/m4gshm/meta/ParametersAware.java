package io.github.m4gshm.meta;

import java.util.List;

/**
 * Provides access to type parameters.
 * @param <T> bean type.
 */
public interface ParametersAware<T> {
    default List<? extends Typed<?>> parameters() {
        return List.of();
    }

    default List<? extends Typed<?>> parametersOf(Class<?> inheritedType) {
        return null;
    }
}
