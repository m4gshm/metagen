package metagen;

import java.util.List;

public interface PropertiesAware<T> {
    List<? extends Typed<?>> properties();

}
