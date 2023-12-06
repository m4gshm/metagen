package matador;

import lombok.Builder;
import lombok.Data;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
public class MetaBean {
    private String name;
    private String class_;
    private String package_;
    private Set<Modifier> modifiers;
    private List<Property> properties;
    private Map<String, MetaBean> nestedTypes;
    private Map<String, MetaBean.Interface> interfaces;
    private List<Param> typeParameters;
    private boolean isRecord;
    private Meta meta;

    @Data
    @Builder
    public static final class Property {
        private String name;
        private boolean setter;
        private boolean getter;
        private boolean field;
        private TypeMirror type;
    }

    @Data
    @Builder
    public static final class Param {
        private String name;
        private TypeMirror type;
    }

    @Data
    @Builder
    public static class Interface {
        private String name;
        private List<Param> typeParameters;
    }
}
