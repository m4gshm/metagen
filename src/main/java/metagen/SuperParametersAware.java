package metagen;

import java.util.List;

public interface SuperParametersAware<T> {
    List<? extends Typed<?>> superParameters();
}
