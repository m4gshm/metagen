package io.github.m4gshm.meta.processor;

import io.github.m4gshm.meta.Meta;

import javax.annotation.processing.Messager;

/**
 * A metadata customizer interface.
 *
 * @param <B> class spec builder type.
 *            See {@link Meta}
 */
public interface MetaCustomizer<B> {

    Class<B> builderType();

    void init(Messager messager, Meta.Extend.Opt... opts);

    B customize(Messager messager, MetaBean bean, B classBuilder);
}
