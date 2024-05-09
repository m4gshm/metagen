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
 *
 * @param <B> class spec builder type.
 *            See {@link meta.Meta}
 */
public interface MetaCustomizer<B> {

    @SuppressWarnings("unchecked")
    static <B> MetaCustomizer<B> instantiate(Meta.Extend customizerInfo, Class<B> builderType) {
        var customizerClass = load(customizerInfo::value);
        var optsMap = Arrays.stream(customizerInfo.opts()).collect(toMap(
                Meta.Extend.Opt::key, Meta.Extend.Opt::value, (l, r) -> l)
        );
        var metaCustomizer = getbMetaCustomizer(customizerClass, optsMap);
        return builderType.isAssignableFrom(metaCustomizer.builderType()) ? (MetaCustomizer<B>) metaCustomizer : null;
    }

    private static MetaCustomizer<?> getbMetaCustomizer(
            Class<? extends MetaCustomizer<?>> customizerClass, Map<String, String[]> optsMap
    ) {
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

    Class<B> builderType();

    B customize(Messager messager, MetaBean bean, B out);
}
