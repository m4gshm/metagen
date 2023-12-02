package matador;

import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.JavaFile;
import io.jbock.javapoet.TypeSpec;
import lombok.SneakyThrows;
import matador.Bean.Output;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import java.io.PrintWriter;
import java.util.*;

import static io.jbock.javapoet.FieldSpec.builder;
import static io.jbock.javapoet.MethodSpec.constructorBuilder;
import static io.jbock.javapoet.TypeSpec.*;
import static java.beans.Introspector.decapitalize;
import static javax.lang.model.element.Modifier.*;
import static javax.lang.model.type.TypeKind.BOOLEAN;

@SupportedAnnotationTypes("matador.Meta")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class MetaAnnotationProcessor extends AbstractProcessor {

    private String fieldsEnumName = "Fields";
    private String typeParametersName = "Params";

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

    private static Bean.Property getProperty(Map<String, Bean.Property> properties, String propName) {
        return properties.computeIfAbsent(propName, name -> Bean.Property.builder().name(name).build());
    }

    private static PackageElement getPackage(TypeElement type) {
        var enclosingElement = type.getEnclosingElement();
        while (!(enclosingElement instanceof PackageElement) && enclosingElement != null) {
            enclosingElement = enclosingElement.getEnclosingElement();
        }
        return (PackageElement) enclosingElement;
    }

    private static Bean getBean(TypeElement type) {
        var isRecord = type.getRecordComponents() != null;

        var properties = new LinkedHashMap<String, Bean.Property>();
        var typeParameters = new LinkedHashMap<String, List<Bean.Param>>();
        var nestedTypes = new ArrayList<Bean>();

        extractPropertiesAndNestedTypes(type, properties, typeParameters, nestedTypes);
        var meta = type.getAnnotation(Meta.class);
        var suffix = meta.suffix();
        var simpleName = type.getSimpleName();

        var packageElement = getPackage(type);

        var name = simpleName.toString();
        var metaName = name + suffix;
        var pack = packageElement != null ? packageElement.getQualifiedName().toString() : null;
        return Bean.builder()
                .name(name)
                .modifiers(type.getModifiers())
                .isRecord(isRecord)
                .typeParameters(typeParameters)
                .properties(new ArrayList<>(properties.values()))
                .types(nestedTypes)
                .output(Output.builder().name(metaName).package_(pack).build())
                .build();
    }

    private static void extractPropertiesAndNestedTypes(TypeElement type,
                                                        Map<String, Bean.Property> properties,
                                                        Map<String, List<Bean.Param>> typeParameters,
                                                        List<Bean> nestedTypes
    ) {
        if (type == null || isObjectType(type)) {
            return;
        }

        if (type.getSuperclass() instanceof DeclaredType declaredType
                && declaredType.asElement() instanceof TypeElement sup
                && !isObjectType(sup)) {
            var arguments = declaredType.getTypeArguments();
            var parameters = sup.getTypeParameters();

            var params = new ArrayList<Bean.Param>();
            for (int i = 0; i < arguments.size(); i++) {
                var paramType = arguments.get(i);
                var paramName = parameters.get(i);

                if (paramType instanceof DeclaredType declType && declType.asElement() instanceof TypeElement typ) {
                    var name = paramName.getSimpleName().toString();
                    var fullType = typ.getQualifiedName().toString();
                    var param = Bean.Param.builder().name(name).type(fullType).build();
                    params.add(param);
                }
            }
            typeParameters.put("superclass", params);
            extractPropertiesAndNestedTypes(sup, properties, typeParameters, nestedTypes);
        }

        var recordComponents = type.getRecordComponents();
        if (recordComponents != null) {
            for (var recordComponent : recordComponents) {
                var name = recordComponent.getSimpleName();
                var property = getProperty(properties, name.toString());
                property.setGetter(true);
            }
        }

        var enclosedElements = type.getEnclosedElements();
        for (var enclosedElement : enclosedElements) {
            var modifiers = enclosedElement.getModifiers();
            var isPublic = modifiers.contains(PUBLIC);
            var isStatic = modifiers.contains(STATIC);
            if (!isStatic && isPublic && enclosedElement instanceof ExecutableElement ee) {
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
            } else if (!isStatic && isPublic && enclosedElement instanceof VariableElement ve) {
                getProperty(properties, ve.getSimpleName().toString()).setField(true);
            } else if (enclosedElement instanceof TypeElement te && enclosedElement.getAnnotation(Meta.class) != null) {
                nestedTypes.add(getBean(te));
            }
        }
    }

    private static boolean isObjectType(TypeElement type) {
        return "java.lang.Object".equals(type.getQualifiedName().toString());
    }

    private static TypeSpec enumConstructor(String value) {
        return anonymousClassBuilder(CodeBlock.builder().add(value).build()).build();
    }

    private static String dotClass(String type) {
        return (type != null && !type.isEmpty() ? type : "Object") + ".class";
    }

    private TypeSpec newTypeSpec(Bean bean) {
        var typesBuilder = typesEnumBuilder(typeParametersName);
        var addTypes = false;

        var typeParameters = bean.getTypeParameters();
        for (var subType : typeParameters.keySet()) {
            var params = typeParameters.get(subType);
            if ("superclass".equals(subType)) {
                for (var param : params) {
                    addTypes = true;
                    typesBuilder.addEnumConstant(param.getName(), enumConstructor(dotClass(param.getType())));
                }
            }
        }

        var fieldsBuilder = fieldsEnumBuilder(fieldsEnumName);
        var properties = bean.getProperties();
        for (var property : properties) {
            fieldsBuilder.addEnumConstant(property.getName(), enumConstructor(dotClass(property.getType())));
        }

        var builder = classBuilder(bean.getOutput().getName())
                .addMethod(constructorBuilder().addModifiers(PRIVATE).build())
                .addModifiers(FINAL);

        if (addTypes) {
            builder.addType(typesBuilder.build());
        }
        if (!properties.isEmpty()) {
            builder.addType(fieldsBuilder.build());
        }
        var modifiers = bean.getModifiers();
        var accessLevel = modifiers.contains(PRIVATE) ? PRIVATE
                : modifiers.contains(PROTECTED) ? PROTECTED
                : modifiers.contains(PUBLIC) ? PUBLIC : null;
        if (accessLevel != null) {
            builder.addModifiers(accessLevel);
        }

        for (var nested : bean.getTypes()) {
            builder.addType(newTypeSpec(nested));
        }

        return builder.build();
    }

    private Builder typesEnumBuilder(String enumName) {
        var typeType = ClassName.get(Class.class);
        var typeName = "type";
        return enumBuilder(enumName).addModifiers(PUBLIC)
                .addField(builder(typeType, typeName).addModifiers(PUBLIC, FINAL).build())
                .addMethod(constructorBuilder().addParameter(typeType, typeName).addCode(
                        CodeBlock.builder().add("this." + typeName + " = " + typeName + ";").build()
                ).build());
    }

    private Builder fieldsEnumBuilder(String enumName) {
        var typeType = ClassName.get(Class.class);
        var typeName = "type";
        return enumBuilder(enumName).addModifiers(PUBLIC)
                .addField(builder(typeType, typeName).addModifiers(PUBLIC, FINAL).build())
                .addMethod(constructorBuilder().addParameter(typeType, typeName).addCode(
                        CodeBlock.builder().add("this." + typeName + " = " + typeName + ";").build()
                ).build());
    }


    @Override
    @SneakyThrows
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        var elements = roundEnv.getElementsAnnotatedWith(Meta.class);
        var beans = elements.stream().map(e -> e instanceof TypeElement type ? type : null)
                .filter(Objects::nonNull).filter(type -> type.getEnclosingElement() instanceof PackageElement)
                .map(MetaAnnotationProcessor::getBean).toList();

        for (var bean : beans) {
            var output = bean.getOutput();

            var javaFileObject = JavaFile.builder(bean.getOutput().getPackage(), newTypeSpec(bean)).build().toJavaFileObject();

            var builderFile = processingEnv.getFiler().createSourceFile(output.getPackage() + "." + output.getName());
            try (var out = new PrintWriter(builderFile.openWriter())) {
                var reader = javaFileObject.openReader(true);
                reader.transferTo(out);
            }
        }

        return true;
    }

}
