package meta;

public interface MetaModel<T> extends PropertiesAware<T>, ParametersAware<T> {

    Class<T> type();

}
