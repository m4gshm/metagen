package matador;

public interface MetaModel<T> extends PropertiesAware, ParametersAware, SuperParametersAware {

    Class<T> type();

}
