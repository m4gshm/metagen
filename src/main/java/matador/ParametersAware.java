package matador;

public interface ParametersAware {
    Typed[] parameters();

    Typed[] parametersOf(Class<?> inheritedType);
}
