package matador;

import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.JavaFile;
import io.jbock.javapoet.TypeSpec;
import lombok.SneakyThrows;
import matador.Meta.Fields;
import matador.Meta.Parameters;
import matador.MetaBean.Property;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.*;
import java.io.PrintWriter;
import java.util.*;

import static io.jbock.javapoet.FieldSpec.builder;
import static io.jbock.javapoet.MethodSpec.constructorBuilder;
import static io.jbock.javapoet.TypeSpec.*;
import static java.beans.Introspector.decapitalize;
import static java.util.Optional.ofNullable;
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

    private static TypInfo getTypeInfo(TypeMirror typeMirror) {
        return typeMirror instanceof DeclaredType declaredType
                && declaredType.asElement() instanceof TypeElement typeElement
                && !isObjectType(typeElement) ? new TypInfo(declaredType, typeElement) : null;
    }

    private static List<MetaBean.Param> extractGenericParams(DeclaredType declaredType, TypeElement typeElement) {
        var arguments = declaredType.getTypeArguments();
        var parameters = typeElement.getTypeParameters();

        var params = new ArrayList<MetaBean.Param>();
        for (int i = 0; i < arguments.size(); i++) {
            var paramType = arguments.get(i);
            var paramName = parameters.get(i);
            params.add(MetaBean.Param.builder()
                    .name(paramName.getSimpleName().toString())
                    .type(paramType)
                    .build());
        }
        return params;
    }

    private static void updateType(Property property, TypeMirror propType) {
        var existType = property.getType();
        if (existType == null) {
            property.setType(propType);
        } else if (!existType.equals(propType)) {
            //todo set Object or shared parent type
//            property.setType(null);
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
                : type instanceof ArrayType || type instanceof DeclaredType || type instanceof PrimitiveType
                ? type.toString() : null;
    }

    private static MetaBean getParentBean(Map<String, MetaBean> nestedTypes, String typeName) {
        var parentBean = nestedTypes.get(typeName);
        if (parentBean == null) {
            parentBean = MetaBean.builder().name(typeName).isRecord(false).build();
            nestedTypes.put(typeName, parentBean);
        }
        return parentBean;
    }

    private static Builder typesEnumBuilder(String enumName) {
        var typeType = classType();
        var typeName = "type";
        return enumBuilder(enumName)
                .addModifiers(PUBLIC)
                .addField(builder(typeType, typeName).addModifiers(PUBLIC, FINAL).build())
                .addMethod(
                        constructorBuilder()
                                .addParameter(typeType, typeName)
                                .addCode(CodeBlock.builder()
                                        .add("this." + typeName + " = " + typeName + ";")
                                        .build())
                                .build()
                );
    }

    static void populateTypeParameters(Builder builder, String enumName, List<MetaBean.Param> typeParameters) {
        var typesBuilder = typesEnumBuilder(enumName);
        for (var param : typeParameters) {
            typesBuilder.addEnumConstant(param.getName(), enumConstructor(dotClass(getStrType(param.getType()))));
        }
        if (!typeParameters.isEmpty()) {
            builder.addType(typesBuilder.build());
        }
    }

    private MetaBean getBean(TypeElement type) {
        var isRecord = type.getRecordComponents() != null;

        var properties = new LinkedHashMap<String, Property>();
        var typeParameters = new ArrayList<MetaBean.Param>();
        var nestedTypes = new LinkedHashMap<String, MetaBean>();

        var meta = type.getAnnotation(Meta.class);

        var superclass = type.getSuperclass();
        if (superclass instanceof DeclaredType declaredType
                && declaredType.asElement() instanceof TypeElement sup
                && !isObjectType(sup)) {
            typeParameters.addAll(extractGenericParams(declaredType, sup));
        }

        var interfaces = new LinkedHashMap<String, MetaBean.Interface>();
        extractPropertiesAndNestedTypes(type, properties, nestedTypes, interfaces);

        var suffix = meta.suffix();
        var simpleName = type.getSimpleName();

        var packageElement = getPackage(type);

        var name = simpleName.toString();
        var metaName = name + suffix;
        var pack = packageElement != null ? packageElement.getQualifiedName().toString() : null;
        return MetaBean.builder()
                .meta(meta)
                .ofClass(name)
                .modifiers(type.getModifiers())
                .isRecord(isRecord)
                .typeParameters(typeParameters)
                .interfaces(interfaces)
                .properties(new ArrayList<>(properties.values()))
                .nestedTypes(nestedTypes)
                .name(metaName)
                .package_(pack)
                .build();
    }

    private void extractPropertiesAndNestedTypes(TypeElement type,
                                                 Map<String, Property> properties,
                                                 Map<String, MetaBean> nestedTypes,
                                                 Map<String, MetaBean.Interface> interfaces) {
        if (type == null || isObjectType(type)) {
            return;
        }
        var messager = processingEnv.getMessager();
        var typeName = type.getSimpleName().toString();

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
                        : setter ? getPropertyName("set", ee) : null;
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
                var bean = getBean(te);
                var name = bean.getName();
                var exists = nestedTypes.get(name);
                if (exists == null) {
                    nestedTypes.put(name, bean);
                } else {
                    var parentBeanTypes = getParentBean(nestedTypes, typeName).getNestedTypes();
                    var nestedOfParent = parentBeanTypes.get(name);
                    if (nestedOfParent == null) {
                        parentBeanTypes.put(name, bean);
                    } else {
                        messager.printNote("nested class already handled, '" + name + "' " +
                                nestedOfParent + ", parent '" + typeName + "'", type);
                    }
                }
            }
        }

        ofNullable(getTypeInfo(type.getSuperclass())).ifPresent(superclass -> extractPropertiesAndNestedTypes(
                superclass.typeElement, properties, nestedTypes, interfaces)
        );

        type.getInterfaces().stream().map(MetaAnnotationProcessor::getTypeInfo).filter(Objects::nonNull).forEach(iface -> {
            var params = extractGenericParams(iface.declaredType, iface.typeElement);
            extractPropertiesAndNestedTypes(iface.typeElement, properties, nestedTypes, interfaces);

            var name = iface.typeElement.getSimpleName().toString();
            var beanInterface = MetaBean.Interface.builder()
                    .name(name)
                    .typeParameters(params)
                    .build();
            var exists = interfaces.get(name);
            if (exists == null) {
                interfaces.put(name, beanInterface);
            } else {
                var parentBean = getParentBean(nestedTypes, typeName);
                var parentBeanInterfaces = parentBean.getInterfaces();
                if (parentBeanInterfaces == null) {
                    parentBean.setInterfaces(parentBeanInterfaces = new LinkedHashMap<>());
                }
                var nestedOfParent = parentBeanInterfaces.get(name);
                if (nestedOfParent == null) {
                    parentBeanInterfaces.put(name, beanInterface);
                } else {
                    messager.printNote("interface already handled, interface '" + name + "' " + exists +
                            " , parent '" + typeName + "'", type);
                }
            }
        });
    }

    private TypeSpec newTypeSpec(String interfaceName, String enumName, MetaBean.Interface interfaceMeta) {
        var builder = classBuilder(interfaceName);
        populateTypeParameters(builder, enumName, interfaceMeta.getTypeParameters());
        return builder.build();
    }

    private TypeSpec newTypeSpec(MetaBean bean) {
        var meta = ofNullable(bean.getMeta());
        var fields = meta.map(Meta::fields);
        var addFieldsEnum = fields.map(Fields::enumerate).orElse(false);
        var addParamsEnum = meta.map(Meta::params).map(Parameters::enumerate).orElse(false);

        var builder = classBuilder(bean.getName())
                .addMethod(constructorBuilder().addModifiers(PRIVATE).build())
                .addModifiers(FINAL);

        var typeParameters = bean.getTypeParameters();
        if (addParamsEnum) {
            populateTypeParameters(builder, meta.get().params().className(), typeParameters);
        }

        if (addFieldsEnum) {
            var fieldsBuilder = fieldsEnumBuilder(fields.get().className());
            var properties = bean.getProperties();
            for (var property : properties) {
                fieldsBuilder.addEnumConstant(property.getName(), enumConstructor(dotClass(getStrType(property.getType()))));
            }

            if (!properties.isEmpty()) {
                builder.addType(fieldsBuilder.build());
            }
        }
        var modifiers = ofNullable(bean.getModifiers()).orElse(Set.of());
        var accessLevel = modifiers.contains(PRIVATE) ? PRIVATE
                : modifiers.contains(PROTECTED) ? PROTECTED
                : modifiers.contains(PUBLIC) ? PUBLIC : null;
        if (accessLevel != null) {
            builder.addModifiers(accessLevel);
        }

        var nestedTypes = ofNullable(bean.getNestedTypes()).orElse(Map.of());
        nestedTypes.forEach((nestedName, nestedBean) -> builder.addType(newTypeSpec(nestedBean)));

        if (addParamsEnum) {
            var interfaces = bean.getInterfaces();
            if (interfaces != null) interfaces.forEach((interfaceName, interfaceMeta) -> {
                while (nestedTypes.containsKey(interfaceName)) {
                    interfaceName = "_" + interfaceName;
                }

                builder.addType(newTypeSpec(interfaceName, meta.get().params().className(), interfaceMeta));
            });
        }

        return builder.build();
    }

    private Builder fieldsEnumBuilder(String enumName) {
        var typeType = classType();
        var typeName = "type";
        return enumBuilder(enumName)
                .addModifiers(PUBLIC)
                .addField(builder(typeType, typeName).addModifiers(PUBLIC, FINAL).build())
                .addMethod(
                        constructorBuilder()
                                .addParameter(typeType, typeName)
                                .addCode(CodeBlock.builder()
                                        .add("this." + typeName + " = " + typeName + ";")
                                        .build())
                                .build()
                );
    }

    @Override
    @SneakyThrows
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        var elements = roundEnv.getElementsAnnotatedWith(Meta.class);
        var beans = elements.stream()
                .map(e -> e instanceof TypeElement type ? type : null).filter(Objects::nonNull)
                .filter(type -> type.getEnclosingElement() instanceof PackageElement)
                .map(this::getBean).toList();

        for (var bean : beans) {
            var javaFileObject = JavaFile.builder(bean.getPackage_(), newTypeSpec(bean)).build().toJavaFileObject();
            var builderFile = processingEnv.getFiler().createSourceFile(bean.getPackage_() + "." + bean.getName());
            try (var out = new PrintWriter(builderFile.openWriter());
                 var reader = javaFileObject.openReader(true)) {
                reader.transferTo(out);
            }
        }
        return true;
    }

    private record TypInfo(DeclaredType declaredType, TypeElement typeElement) {

    }

}
