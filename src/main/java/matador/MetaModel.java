package matador;

public interface MetaModel<T> extends ParametersAware {

    Class<T> type();

    Typed[] properties();

}
