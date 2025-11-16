package io.github.m4gshm.meta;

/**
 * Full access to a bean metadata.
 * @param <T> bean type.
 */
public interface MetaModel<T> extends PropertiesAware<T>, ParametersAware<T> {

    Class<T> type();

}
