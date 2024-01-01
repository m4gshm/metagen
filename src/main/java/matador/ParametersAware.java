package matador;

import java.util.List;

public interface ParametersAware {
    List<? extends Typed> parameters();

    List<? extends Typed> parametersOf(Class<?> inheritedType);
}
