package matador;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.Objects;
import java.util.Set;

import static javax.lang.model.type.TypeKind.BOOLEAN;

@SupportedAnnotationTypes("matador.Meta")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class SimpleAnnotationProcessor extends AbstractProcessor {
    private static ExecutableElement getIfGetterOrSetter(ExecutableElement executableElement) {
        var name = executableElement.getSimpleName().toString();
        var getter = name.length() > 3 && name.startsWith("get");
        var setter = name.length() > 3 && name.startsWith("set");
        var boolGetter = name.length() > 2 && name.startsWith("is") && executableElement.getReturnType().getKind() == BOOLEAN;
        return getter || setter || boolGetter ? executableElement : null;
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations,
                           final RoundEnvironment roundEnv) {

        var elements = roundEnv.getElementsAnnotatedWith(Meta.class);
        elements.stream().map(e -> e instanceof TypeElement type ? type : null).filter(Objects::nonNull).map(type -> {
            type.getEnclosedElements().stream().map(
                    ee -> ee instanceof ExecutableElement ex ? getIfGetterOrSetter(ex) : ee instanceof VariableElement ve ? ve : null
            ).filter(Objects::nonNull)
        })

        return true;
    }
}
