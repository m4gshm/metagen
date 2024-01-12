package metagen;

public interface MetaModel<T> extends PropertiesAware<T>, ParametersAware {

    Class<T> type();

}
