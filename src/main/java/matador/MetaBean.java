package matador;

import lombok.Builder;
import lombok.Data;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Set;

import static matador.MetaBeanUtils.getPackage;

@Data
@Builder(toBuilder = true)
public class MetaBean {
    private TypeElement type;
    private String name;
    private String className;
    private Set<Modifier> modifiers;
    private List<Property> properties;
    private List<Param> typeParameters;
    private MetaBean superclass;
    private List<MetaBean> nestedTypes;
    private List<MetaBean> interfaces;
    private boolean isRecord;
    private Meta meta;
    private BeanBuilder beanBuilderInfo;
    private List<? extends AnnotationMirror> annotations;

    public String getClassName() {
        return className != null ? className : type.getSimpleName().toString();
    }

    public String getPackageName() {
        var packageElement = getPackage(type);
        return packageElement != null ? packageElement.getQualifiedName().toString() : null;
    }

    @Data
    @Builder
    public static final class BeanBuilder {
        private String metaClassName;
        private String className;
        private String setPrefix;
        private String builderMethodName;
        private String buildMethodName;
        private TypeElement type;
        private List<Param> typeParameters;
        private List<Setter> setters;
        @Data
        @Builder(toBuilder = true)
        public static final class Setter {
            private String name;
            private TypeMirror type;
            private TypeMirror evaluatedType;
            private ExecutableElement setter;
        }
    }

    @Data
    @Builder
    public static final class Property {
        private String name;
        private ExecutableElement setter;
        private ExecutableElement getter;
        private VariableElement field;
        private RecordComponentElement recordComponent;
        private TypeMirror type;
        private TypeMirror evaluatedType;
        private List<? extends AnnotationMirror> annotations;
    }

    @Data
    @Builder
    public static final class Param {
        private TypeParameterElement name;
        private TypeMirror type;
        private TypeMirror evaluatedType;
    }

    @Data
    @Builder
    public static class Interface {
        private TypeElement type;
        private List<Param> typeParameters;
    }
}
