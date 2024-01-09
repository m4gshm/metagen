package matador;

import javax.annotation.processing.Messager;

public interface MetaCustomizer<T> {
    T customize(Messager messager, MetaBean bean, T out);
}
