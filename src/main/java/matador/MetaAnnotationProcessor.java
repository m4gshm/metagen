package matador;

import io.jbock.javapoet.JavaFile;
import io.jbock.javapoet.TypeSpec;
import lombok.SneakyThrows;
import matador.Bean.Output;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.util.*;

import static java.beans.Introspector.decapitalize;
import static javax.lang.model.element.Modifier.*;
import static javax.lang.model.type.TypeKind.BOOLEAN;

@SupportedAnnotationTypes("matador.Meta")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class MetaAnnotationProcessor extends AbstractProcessor {

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

    private static PackageElement getPackage(TypeElement type) {
        var enclosingElement = type.getEnclosingElement();
        while (!(enclosingElement instanceof PackageElement) && enclosingElement != null) {
            enclosingElement = enclosingElement.getEnclosingElement();
        }
        return (PackageElement) enclosingElement;
    }

    private static JavaFileObject newJavaFileObject(Bean bean) {
        var output = bean.getOutput();
        var fields = TypeSpec.enumBuilder("Fields");
        for (var property : bean.getProperties()) {
            fields.addEnumConstant(property.getName());
        }

        var javaFile = JavaFile.builder(output.getPackage(), TypeSpec.classBuilder(output.getName())
                .addModifiers(PUBLIC, FINAL)
                .addType(fields
                        .build())
                .build()).build();
        return javaFile.toJavaFileObject();
    }

    @Override
    @SneakyThrows
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {

        var elements = roundEnv.getElementsAnnotatedWith(Meta.class);
        var beans = elements.stream().map(e -> e instanceof TypeElement type ? type : null).filter(Objects::nonNull).map(type -> {
            var recordComponents = type.getRecordComponents();
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
            var suffix = meta.suffix();
            var simpleName = type.getSimpleName();

            var packageElement = getPackage(type);

            var name = simpleName.toString();
            var metaName = name + suffix;
            var pack = packageElement != null ? packageElement.getQualifiedName().toString() : null;
            return Bean.builder()
                    .name(name)
                    .output(Output.builder().name(metaName).package_(pack).build())
                    .properties(new ArrayList<>(properties.values()))
                    .build();
        }).toList();


        for (var bean : beans) {
            var output = bean.getOutput();

            var javaFileObject = newJavaFileObject(bean);

            var builderFile = processingEnv.getFiler().createSourceFile(output.getPackage() + "." + output.getName());
            try (var out = new PrintWriter(builderFile.openWriter())) {
                var reader = javaFileObject.openReader(true);
                reader.transferTo(out);
            }
        }


        return true;
    }

}
