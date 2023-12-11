package matador;

import lombok.Builder;
import lombok.Data;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Set;

import static matador.MetaAnnotationProcessorUtils.getPackage;

@Data
@Builder(toBuilder = true)
public class MetaBean {
    private TypeElement type;
    private String name;
    private Set<Modifier> modifiers;
    private List<Property> properties;
    private List<Param> typeParameters;
    private MetaBean superclass;
    private List<MetaBean> nestedTypes;
    private List<MetaBean> interfaces;
    private boolean isRecord;
    private Meta meta;

    public String getClassName() {
        return type.getSimpleName().toString();
    }

    public String getPackageName() {
        var packageElement = getPackage(type);
        return packageElement != null ? packageElement.getQualifiedName().toString() : null;
    }

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
        private TypeParameterElement name;
        private TypeMirror type;
    }

    @Data
    @Builder
    public static class Interface {
        private TypeElement type;
        private List<Param> typeParameters;
    }
}
