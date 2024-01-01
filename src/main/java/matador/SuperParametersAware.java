package matador;

import java.util.List;

public interface SuperParametersAware {
    List<? extends Typed> superParameters();
}
