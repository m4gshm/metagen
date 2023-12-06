package matador;

import java.util.List;

public interface TypeMetaInfo {

    String pack();
    String name();

    List<Enum<?>> fields();
    List<Enum<?>> parameters();
    List<TypeMetaInfo> inherits();
    List<TypeMetaInfo> nestedTypes();

}
