package meta;

import javax.annotation.processing.Messager;

/**
 * The metadata customizer contract.
 * See {@link meta.Meta}
 */
public interface MetaCustomizer<T> {
    T customize(Messager messager, MetaBean bean, T out);
}
