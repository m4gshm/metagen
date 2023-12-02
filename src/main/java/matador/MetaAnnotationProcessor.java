package matador;

import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.JavaFile;
import io.jbock.javapoet.TypeSpec;
import lombok.SneakyThrows;
import matador.Bean.Output;
import matador.Bean.Property;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.jbock.javapoet.FieldSpec.builder;
import static io.jbock.javapoet.MethodSpec.constructorBuilder;
import static io.jbock.javapoet.TypeSpec.Builder;
import static io.jbock.javapoet.TypeSpec.anonymousClassBuilder;
import static io.jbock.javapoet.TypeSpec.classBuilder;
import static io.jbock.javapoet.TypeSpec.enumBuilder;
import static java.beans.Introspector.decapitalize;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
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

    private static Property getProperty(Map<String, Property> properties, String propName) {
        return properties.computeIfAbsent(propName, name -> Property.builder().name(name).build());
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

        var properties = new LinkedHashMap<String, Property>();
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
                                                        Map<String, Property> properties,
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
                var propType = recordComponent.asType();
                var property = getProperty(properties, name.toString());
                property.setGetter(true);
                updateType(property, propType);
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
                    final TypeMirror propType;
                    if (getter || boolGetter) {
                        propType = ee.getReturnType();
                    } else {
                        var parameters = ee.getParameters();
                        if (parameters.size() == 1) {
                            var element = parameters.get(0);
                            propType = element.asType();
                        } else if (parameters.size() == 2) {
                            var first = parameters.get(0);
                            var isIndex = first.asType() instanceof PrimitiveType primitiveType
                                    && "int".equals(primitiveType.toString());
                            propType = isIndex ? parameters.get(1).asType() : null;
                        } else {
                            propType = null;
                        }
                    }

                    var property = getProperty(properties, propName);
                    property.setSetter(setter);
                    property.setGetter(getter || boolGetter);
                    updateType(property, propType);
                }
            } else if (!isStatic && isPublic && enclosedElement instanceof VariableElement ve) {
                var propType = ve.asType();
                var property = getProperty(properties, ve.getSimpleName().toString());
                property.setField(true);
                updateType(property, propType);
            } else if (enclosedElement instanceof TypeElement te && enclosedElement.getAnnotation(Meta.class) != null) {
                nestedTypes.add(getBean(te));
            }
        }
    }

    private static void updateType(Property property, TypeMirror propType) {
        var existType = property.getType();
        if (existType == null) {
            property.setType(propType);
        } else if (!existType.equals(propType)) {
            //todo set Object or shared parent type
            property.setType(null);
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

    private static ClassName classType() {
        return ClassName.get("", "Class<?>");
    }

    private static String getStrType(TypeMirror type) {
        return type instanceof TypeVariable typeVariable ? getStrType(typeVariable.getUpperBound())
                : type instanceof IntersectionType intersectionType ? getStrType(intersectionType.getBounds().get(0))
                : type instanceof ArrayType || type instanceof DeclaredType || type instanceof PrimitiveType ? type.toString()
                : null;
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
            fieldsBuilder.addEnumConstant(property.getName(), enumConstructor(dotClass(getStrType(property.getType()))));
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
        var typeType = classType();
        var typeName = "type";
        return enumBuilder(enumName).addModifiers(PUBLIC)
                .addField(builder(typeType, typeName).addModifiers(PUBLIC, FINAL).build())
                .addMethod(constructorBuilder().addParameter(typeType, typeName).addCode(
                        CodeBlock.builder().add("this." + typeName + " = " + typeName + ";").build()
                ).build());
    }

    private Builder fieldsEnumBuilder(String enumName) {
        var typeType = classType();
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
