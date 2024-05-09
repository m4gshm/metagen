package meta;

import javax.annotation.processing.Messager;
import java.util.Map;

/**
 * The metadata customizer contract.
 *
 * @param <B> class spec builder type.
 *            See {@link meta.Meta}
 */
public interface MetaCustomizer<B> {

    Class<B> builderType();

    void init(Meta.Extend.Opt... opts);

    B customize(Messager messager, MetaBean bean, B out);
}
