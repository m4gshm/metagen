package meta.util;

import lombok.experimental.UtilityClass;
import meta.Meta.Extend;
import meta.MetaCustomizer;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

@UtilityClass
public class MetaCustomizerUtils {
    @SuppressWarnings("unchecked")
    static <B> MetaCustomizer<B> instantiate(Extend customizerInfo, Class<B> builderType, ClassLoader classLoader) {
        var customizerClass = ClassLoadUtility.load(customizerInfo::value);
        var load = ServiceLoader.load(customizerClass, classLoader);

        var metaCustomizer = load.stream().map(ServiceLoader.Provider::get)
                .map(s -> s instanceof MetaCustomizer<?> mc
                        && builderType.isAssignableFrom(mc.builderType()) ? mc : null)
                .filter(Objects::nonNull).findFirst().orElse(null);
        if (metaCustomizer == null) {
            throw new MetaCustomizerException("customizer implementation not found " + customizerClass.getName());
        }
        metaCustomizer.init(customizerInfo.opts());
        return builderType.isAssignableFrom(metaCustomizer.builderType()) ? (MetaCustomizer<B>) metaCustomizer : null;
    }

    private static MetaCustomizer<?> getMetaCustomizer(
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
}
