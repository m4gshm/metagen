package metagen;

import java.util.List;

public interface ParametersAware<T> {
    default List<? extends Typed<?>> parameters() {
        return List.of();
    }

    default List<? extends Typed<?>> parametersOf(Class<?> inheritedType) {
        return null;
    }
}
