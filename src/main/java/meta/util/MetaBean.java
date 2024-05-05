package meta.util;

import lombok.Builder;
import lombok.Data;
import meta.Meta;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Bean metadata model.
 */
@Data
public class MetaBean {
    private final TypeElement type;
    private String name;
    private String packageName;
    private List<ExecutableElement> methods;
    private List<Property> properties;
    private List<Param> typeParameters;
    private MetaBean superclass;
    private List<TypeElement> nestedTypes;
    private List<MetaBean> interfaces;
    private boolean isRecord;
    private Meta meta;
    private BeanBuilder beanBuilderInfo;

    public List<MetaBean.Property> getPublicProperties() {
        return this.getProperties().stream().filter(MetaBean.Property::isPublic).toList();
    }

    public List<ExecutableElement> getPublicMethods() {
        return this.getMethods().stream().filter(ee -> ee.getModifiers().contains(PUBLIC)).toList();
    }

    public String getClassName() {
        return type.getSimpleName().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var metaBean = (MetaBean) o;
        return Objects.equals(type, metaBean.type);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type);
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
        private boolean isPublicField;
        private RecordComponentElement recordComponent;
        private TypeMirror type;
        private TypeMirror evaluatedType;
        private List<AnnotationMirror> annotations;
        private MetaBean bean;
        private boolean excluded;

        public boolean isPublic() {
            return (field != null && isPublicField) || setter != null || getter != null || recordComponent != null;
        }

        public <T extends Annotation> Map<? extends ExecutableElement, ? extends AnnotationValue> getAnnotation(
                Class<T> annotationClass
        ) {
            var annotationClassName = annotationClass.getCanonicalName();
            return annotations.stream().filter(a -> {
                        var annotationType = a.getAnnotationType();
                        var annotationTypeName = annotationType.toString();
                        return annotationTypeName.equals(annotationClassName);
                    })
                    .map(AnnotationMirror::getElementValues)
                    .findFirst().orElse(null);
        }
    }

    @Data
    @Builder(toBuilder = true)
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
