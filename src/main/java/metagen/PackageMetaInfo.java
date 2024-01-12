package metagen;

import java.util.List;

public interface PackageMetaInfo {
    String pack();
    List<TypeMetaInfo> types();

}
