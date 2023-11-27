package matador;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.*;

import static java.beans.Introspector.decapitalize;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.BOOLEAN;

@SupportedAnnotationTypes("matador.Meta")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class SimpleAnnotationProcessor extends AbstractProcessor {
    private static ExecutableElement getIfGetterOrSetter(ExecutableElement executableElement) {
        return isGetterOrSetter(executableElement) ? executableElement : null;
    }

    private static boolean isGetterOrSetter(ExecutableElement executableElement) {
        var getter = isGetter(executableElement);
        var setter = isSetter(executableElement);
        var boolGetter = isBoolGetter(executableElement);
        return getter || setter || boolGetter;
    }

    private static boolean isBoolGetter(ExecutableElement executableElement) {
        var name = getMethodName(executableElement);
        return name.length() > 2 && name.startsWith("is") && executableElement.getReturnType().getKind() == BOOLEAN;
    }

    private static boolean isSetter(ExecutableElement executableElement) {
        var name = getMethodName(executableElement);
        return name.length() > 3 && name.startsWith("set");
    }

    private static boolean isGetter(ExecutableElement executableElement) {
        var name = getMethodName(executableElement);
        return name.length() > 3 && name.startsWith("get");
    }

    private static String getMethodName(ExecutableElement ee) {
        return ee.getSimpleName().toString();
    }

    private static String getPropertyName(String prefix, ExecutableElement ee) {
        return decapitalize(getMethodName(ee).substring(prefix.length()));
    }

    private static Bean.Property getProperty(LinkedHashMap<String, Bean.Property> properties, String propName) {
        return properties.computeIfAbsent(propName, name -> Bean.Property.builder().name(name).build());
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations,
                           final RoundEnvironment roundEnv) {

        var elements = roundEnv.getElementsAnnotatedWith(Meta.class);
        var beans = elements.stream().map(e -> e instanceof TypeElement type ? type : null).filter(Objects::nonNull).map(type -> {
            var properties = new LinkedHashMap<String, Bean.Property>();
            var enclosedElements = type.getEnclosedElements();
            for (var enclosedElement : enclosedElements) {
                var modifiers = enclosedElement.getModifiers();
                var isPublic = modifiers.contains(PUBLIC);
                var isStatic = modifiers.contains(STATIC);
                if (!isStatic && isPublic) {
                    if (enclosedElement instanceof ExecutableElement ee) {
                        var getter = isGetter(ee);
                        var setter = isSetter(ee);
                        var boolGetter = isBoolGetter(ee);
                        var propName = getter ? getPropertyName("get", ee)
                                : boolGetter ? getPropertyName("is", ee)
                                : setter ? getPropertyName("set", ee)
                                : null;
                        if (propName != null) {
                            var property = getProperty(properties, propName);
                            property.setSetter(setter);
                            property.setGetter(getter || boolGetter);
                        }
                    } else if (enclosedElement instanceof VariableElement ve) {
                        getProperty(properties, ve.getSimpleName().toString()).setField(true);
                    }
                }
            }
            var meta = type.getAnnotation(Meta.class);
            return Bean.builder()
                    .name(type.getQualifiedName().toString())
                    .properties(new ArrayList<>(properties.values()))
                    .build();
        }).toList();

        return true;
    }

}
