package meta;

import meta.util.MetaBean;
import meta.util.MetaCustomizerException;

import javax.annotation.processing.Messager;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Map;

import static java.util.stream.Collectors.toMap;
import static meta.util.ClassLoadUtility.load;

/**
 * The metadata customizer contract.
 * See {@link meta.Meta}
 */
public interface MetaCustomizer<T> {
    @SuppressWarnings("unchecked")
    static MetaCustomizer<?> instantiate(Meta.Extend customizerInfo) {
        var customizerClass = load(customizerInfo::value);
        var optsMap = Arrays.stream(customizerInfo.opts()).collect(toMap(
                Meta.Extend.Opt::key, Meta.Extend.Opt::value, (l, r) -> l)
        );

        try {
            return customizerClass.getDeclaredConstructor(Map.class).newInstance(optsMap);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            try {
                return customizerClass.getDeclaredConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e2) {
                throw new MetaCustomizerException(customizerClass, e2);
            } catch (NoSuchMethodException e2) {
                e.addSuppressed(e2);
                throw new MetaCustomizerException(customizerClass, e);
            }
        }
    }

    T customize(Messager messager, MetaBean bean, T out);
}
