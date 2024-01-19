package metagen;

import java.util.List;

public interface ParametersAware<T> {
    List<? extends Typed<?>> parameters();

    List<? extends Typed<?>> parametersOf(Class<?> inheritedType);
}
