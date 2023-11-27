package meta;

import java.util.List;

/**
 * Provides access to parameters of superclass or implemented interfaces.
 * @param <T> bean type.
 */
public interface SuperParametersAware<T> {
    default List<? extends Typed<?>> superParameters() {
        return List.of();
    }
}
