package meta;

import java.util.List;

public interface SuperParametersAware<T> {
    default List<? extends Typed<?>> superParameters() {
        return List.of();
    }
}
