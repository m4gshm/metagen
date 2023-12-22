package matador;

public interface MetaModel<T> extends PropertiesAware, ParametersAware {

    Class<T> type();

}
