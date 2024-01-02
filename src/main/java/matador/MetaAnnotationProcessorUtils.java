package matador;

import io.jbock.javapoet.*;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import javax.annotation.processing.Messager;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import java.util.*;
import java.util.function.Function;

import static io.jbock.javapoet.FieldSpec.builder;
import static io.jbock.javapoet.MethodSpec.constructorBuilder;
import static io.jbock.javapoet.MethodSpec.methodBuilder;
import static io.jbock.javapoet.TypeSpec.Builder;
import static io.jbock.javapoet.TypeSpec.classBuilder;
import static java.beans.Introspector.decapitalize;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static javax.lang.model.element.Modifier.*;
import static javax.lang.model.type.TypeKind.BOOLEAN;

@UtilityClass
public class MetaAnnotationProcessorUtils {

    public static final String METAS = "Metas";

    static boolean isBoolGetter(ExecutableElement executableElement) {
        var name = getMethodName(executableElement);
        return name.length() > 2 && name.startsWith("is") && executableElement.getReturnType().getKind() == BOOLEAN;
    }

    static boolean isSetter(ExecutableElement executableElement) {
        var name = getMethodName(executableElement);
        return name.length() > 3 && name.startsWith("set");
    }

    static boolean isGetter(ExecutableElement executableElement) {
        var name = getMethodName(executableElement);
        return name.length() > 3 && name.startsWith("get");
    }

    static String getMethodName(ExecutableElement ee) {
        return ee.getSimpleName().toString();
    }

    static String getPropertyName(String prefix, ExecutableElement ee) {
        return decapitalize(getMethodName(ee).substring(prefix.length()));
    }

    static MetaBean.Property getProperty(Map<String, MetaBean.Property> properties, String propName,
                                         List<? extends AnnotationMirror> annotations) {
        return properties.computeIfAbsent(propName, name -> MetaBean.Property.builder().name(name)
                .annotations(annotations).build());
    }

    static PackageElement getPackage(TypeElement type) {
        var enclosingElement = type.getEnclosingElement();
        while (!(enclosingElement instanceof PackageElement) && enclosingElement != null) {
            enclosingElement = enclosingElement.getEnclosingElement();
        }
        return (PackageElement) enclosingElement;
    }

    static TypeInfo getTypeInfo(TypeMirror typeMirror) {
        return typeMirror instanceof DeclaredType declaredType
                && declaredType.asElement() instanceof TypeElement typeElement
                && !isObjectType(typeElement) ? new TypeInfo(declaredType, typeElement) : null;
    }

    static List<MetaBean.Param> extractGenericParams(TypeElement typeElement, DeclaredType declaredType) {
        var arguments = declaredType != null ? declaredType.getTypeArguments() : null;
        var parameters = typeElement.getTypeParameters();

        var params = new ArrayList<MetaBean.Param>();
        for (int i = 0; i < parameters.size(); i++) {
            var paramName = parameters.get(i);
            var paramType = arguments != null ? arguments.get(i) : paramName.asType();
            params.add(MetaBean.Param.builder()
                    .name(paramName)
                    .type(paramType)
                    .build());
        }
        return params;
    }

    static void updateType(MetaBean.Property property, TypeMirror propType) {
        var existType = property.getType();
        if (existType == null) {
            property.setType(propType);
        } else if (!existType.equals(propType)) {
            //todo set Object or shared parent type
//            property.setType(null);
        }
    }

    static boolean isObjectType(TypeElement type) {
        return "java.lang.Object".equals(type.getQualifiedName().toString());
    }

    static CodeBlock newInstanceCall(TypeName className, String name, String type, CodeBlock getter) {
        return CodeBlock.builder().add("new $T<>($L)", className, enumConstructorArgs(name, type, getter)).build();
    }

    @NotNull
    private static CodeBlock enumConstructorArgs(String name, String type, CodeBlock getter) {
        var builder = CodeBlock.builder().add("\"" + name + "\"").add(", ").add(type);
        if (getter != null) {
            builder.add(", ").add(getter);
        }
        return builder.build();
    }

    static String dotClass(TypeName type) {
        return (type != null ? type : TypeName.OBJECT) + ".class";
    }

    static TypeName getType(TypeMirror type, List<MetaBean.Param> typeParameters) {
        return type instanceof TypeVariable typeVariable ? getType(typeVariable, typeParameters)
                : type instanceof IntersectionType intersectionType ? getType(intersectionType, typeParameters)
                : type instanceof ArrayType || type instanceof DeclaredType || type instanceof PrimitiveType
                ? TypeName.get(type) : null;
    }

    private static TypeName getType(IntersectionType intersectionType, List<MetaBean.Param> typeParameters) {
        return getType(intersectionType.getBounds().get(0), typeParameters);
    }

    private static TypeName getType(TypeVariable typeVariable, List<MetaBean.Param> typeParameters) {
        var collect = typeParameters != null
                ? typeParameters.stream().collect(toMap(p -> p.getName().asType(), p -> p.getType()))
                : Map.<TypeMirror, TypeMirror>of();
        var type = collect.get(typeVariable);
        if (type != null && !type.equals(typeVariable)) {
            return getType(type, typeParameters);
        } else {
            return getType(typeVariable.getUpperBound(), typeParameters);
        }
    }

    private static TypeSpec newEnumParams(String enumName, List<MetaBean.Param> typeParameters) {
        var className = ClassName.get("", enumName);
        var classTypeVar = TypeVariableName.get("T");
        var typesBuilder = typeAwareClass(className, classTypeVar);
        var paramNames = new LinkedHashSet<String>();
        for (var param : typeParameters) {
            var name = param.getName().getSimpleName().toString();
            paramNames.add(name);
            var type = dotClass(getType(param.getType(), typeParameters));
            typesBuilder.addField(FieldSpec.builder(className, name, PUBLIC, FINAL, STATIC)
                    .initializer(newInstanceCall(className, name, type, null)).build());
        }
        var uniqueNames = new HashSet<String>(paramNames);
        typesBuilder = populateTypeAwareClass(typesBuilder, classTypeVar, null, uniqueNames);
        typesBuilder = addValues(typesBuilder, className, paramNames, uniqueNames);
        return typesBuilder.build();
    }

    @NotNull
    private static Getter getterType(MetaBean bean, TypeName returnType) {
        var beanType = ClassName.get(bean.getType());
        return new Getter(ParameterizedTypeName.get(ClassName.get(Function.class), beanType, returnType), beanType);
    }


    @NotNull
    private static Builder populateTypeAwareClass(
            Builder fieldsBuilder, TypeVariableName classTypeVar, Getter getter, Set<String> uniqueNames
    ) {

        var nameName = getUniqueName("name", uniqueNames);
        var typeName = getUniqueName("type", uniqueNames);
        var getterName = getUniqueName("getter", uniqueNames);

        var nameType = ClassName.get(String.class);
        var typeType = ParameterizedTypeName.get(ClassName.get(Class.class), classTypeVar);

        fieldsBuilder
                .addField(FieldSpec.builder(nameType, nameName).addModifiers(PUBLIC, FINAL).build())
                .addField(FieldSpec.builder(typeType, typeName).addModifiers(PUBLIC, FINAL).build())
                .addMethod(
                        methodBuilder("name")
                                .addAnnotation(Override.class)
                                .addModifiers(PUBLIC).returns(nameType)
                                .addStatement("return this." + nameName)
                                .build()
                )
                .addMethod(
                        methodBuilder("type")
                                .addAnnotation(Override.class)
                                .addModifiers(PUBLIC).returns(typeType)
                                .addStatement("return this." + typeName)
                                .build()
                );

        if (getter != null) {
            fieldsBuilder.addField(FieldSpec.builder(getter.functionType(), getterName).addModifiers(PUBLIC, FINAL).build());
            fieldsBuilder.addMethod(
                    methodBuilder("get")
//                            .addAnnotation(Override.class)
                            .addModifiers(PUBLIC)
                            .addParameter(getter.beanType(), "bean")
                            .returns(classTypeVar)
                            .addStatement("return this." + getterName + ".apply(bean)")
                            .build()
            );
        }

        var constructor = typeAwareEnumLikeConstructor(getter, nameType, nameName, typeType, typeName);

        return fieldsBuilder.addMethod(constructor.build());
    }

    @NotNull
    private static Builder typeAwareClass(ClassName enumName, TypeVariableName typeVariable) {
        return classBuilder(enumName)
                .addTypeVariable(typeVariable)
                .addSuperinterface(Typed.class)
                .addModifiers(PUBLIC, STATIC, FINAL);
    }

    private static MethodSpec.Builder typeAwareEnumLikeConstructor(
            Getter getter, ClassName nameType, String nameName, TypeName typeType, String typeName
    ) {
        var constructor = constructorBuilder().addModifiers(PRIVATE)
                .addParameter(nameType, nameName)
                .addParameter(typeType, typeName);
        var constructorBody = CodeBlock.builder()
                .addStatement("this." + nameName + " = " + nameName)
                .addStatement("this." + typeName + " = " + typeName);
        if (getter != null) {
            constructor.addParameter(getter.functionType(), "getter");
            constructorBody.addStatement("this." + "getter" + " = " + "getter");
        }

        constructor.addCode(constructorBody.build());
        return constructor;
    }

    static MetaBean getBean(TypeElement type, DeclaredType declaredType, Messager messager) {
        if (type == null || isObjectType(type)) {
            return null;
        }

        var isRecord = type.getRecordComponents() != null;
        var annotations = type.getAnnotationMirrors();
        var meta = type.getAnnotation(Meta.class);

        var properties = new LinkedHashMap<String, MetaBean.Property>();
        var nestedTypes = new LinkedHashMap<String, MetaBean>();
        var recordComponents = type.getRecordComponents();
        if (recordComponents != null) {
            for (var recordComponent : recordComponents) {
                var recordName = recordComponent.getSimpleName();
                var propType = recordComponent.asType();
                var annotationMirrors = recordComponent.getAnnotationMirrors();
                var property = getProperty(properties, recordName.toString(), annotationMirrors);
                property.setRecordComponent(recordComponent);
                updateType(property, propType);
            }
        }
        var enclosedElements = type.getEnclosedElements();
        for (var enclosedElement : enclosedElements) {
            var annotationMirrors = enclosedElement.getAnnotationMirrors();
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

                    var property = getProperty(properties, propName, annotationMirrors);
                    if (setter) {
                        property.setSetter(ee);
                    }
                    if (getter || boolGetter) {
                        property.setGetter(ee);
                    }
                    updateType(property, propType);
                }
            } else if (!isStatic && isPublic && enclosedElement instanceof VariableElement ve) {
                var propType = ve.asType();
                var property = getProperty(properties, ve.getSimpleName().toString(), annotationMirrors);
                property.setField(ve);
                updateType(property, propType);
            } else if (enclosedElement instanceof TypeElement te && enclosedElement.getAnnotation(Meta.class) != null) {
                var nestedBean = getBean(te, null, messager);
                var nestedBeanClassName = nestedBean.getClassName();
                var exists = nestedTypes.get(nestedBeanClassName);
                if (exists == null) {
                    nestedTypes.put(nestedBeanClassName, nestedBean);
                } else {
                    //todo
//                    var parentBeanTypes = getParentBean(nestedTypes, typeName).getNestedTypes();
//                    var nestedOfParent = parentBeanTypes.get(nestedBeanClassName);
//                    if (nestedOfParent == null) {
//                        parentBeanTypes.put(nestedBeanClassName, bean);
//                    } else {
//                        messager.printNote("nested class already handled, '" + nestedBeanClassName + "' " +
//                                nestedOfParent + ", parent '" + typeName + "'", type);
//                    }
                }
            }
        }

        var superBean = ofNullable(getTypeInfo(type.getSuperclass())).map(superclass ->
                getBean(superclass.typeElement, superclass.declaredType, messager)
        ).orElse(null);

        var interfaceBeans = type.getInterfaces().stream().map(MetaAnnotationProcessorUtils::getTypeInfo)
                .filter(Objects::nonNull).map(iface -> getBean(iface.typeElement, iface.declaredType, messager))
                .toList();

        var name = type.getSimpleName().toString();
        var suffix = meta != null ? meta.suffix() : null;
        return MetaBean.builder()
                .isRecord(isRecord)
                .type(type)
                .meta(meta)
                .name(name + (suffix == null || suffix.trim().isEmpty() ? Meta.META : suffix))
                .superclass(superBean)
                .interfaces(interfaceBeans)
                .nestedTypes(new ArrayList<>(nestedTypes.values()))
                .properties(new ArrayList<>(properties.values()))
                .modifiers(type.getModifiers())
                .typeParameters(new ArrayList<>(extractGenericParams(type, declaredType)))
                .annotations(annotations)
                .build();
    }

    static String getAggregatorName(String beanPackage) {
        final String prefix;
        if (beanPackage != null) {
            var delim = beanPackage.lastIndexOf('.');
            var packName = delim > 0 ? beanPackage.substring(delim + 1) : beanPackage;
            var letters = packName.toCharArray();
            letters[0] = Character.toUpperCase(letters[0]);
            prefix = String.valueOf(letters);
        } else {
            prefix = "";
        }
        return !prefix.isEmpty() ? prefix : METAS;
    }

    static MethodSpec enumValuesMethod(String name, TypeName typeClassName, boolean overr) {
        var code = CodeBlock.builder()
                .addStatement("return $T.values()", typeClassName)
                .build();
        return enumValuesMethodBuilder(name, typeClassName, overr, code).build();
    }

    static MethodSpec.Builder enumValuesMethodBuilder(String name, TypeName typeClassName, boolean overr, CodeBlock codeBlock) {
        var builder = methodBuilder(name)
                .addModifiers(PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(List.class), typeClassName))
                .addCode(codeBlock);
        if (overr) {
            builder.addAnnotation(Override.class);
        }
        return builder;
    }

    private static TypeSpec newTypeInterface(
            String interfaceName, String enumName, MetaBean interfaceMeta
    ) {
        var builder = classBuilder(interfaceName);
        builder.addType(newEnumParams(enumName, interfaceMeta.getTypeParameters()));
        return builder.build();
    }

    static TypeSpec newTypeBean(MetaBean bean, Modifier... modifiers) {
        var meta = ofNullable(bean.getMeta());
        var props = meta.map(Meta::properties);
        var parameters = meta.map(Meta::params);

        var addFieldsEnum = props.map(Meta.Properties::enumerate).orElse(false);
        var addParamsEnum = parameters.map(Meta.Parameters::enumerate).orElse(false);

        var className = ClassName.get(bean.getType());

        var name = bean.getName();
        var typeFieldType = ParameterizedTypeName.get(ClassName.get(Class.class), className);
        var typeGetter = methodBuilder("type")
                .addModifiers(PUBLIC, FINAL)
                .returns(typeFieldType)
                .addCode(CodeBlock.builder()
                        .addStatement("return type")
                        .build());

        var typeField = FieldSpec.builder(
                        typeFieldType, "type", PUBLIC, FINAL)
                .initializer(CodeBlock.builder().addStatement("$T.class", className).build())
                .build();

        var instanceField = FieldSpec.builder(
                ClassName.get("", name), "instance", PUBLIC, STATIC, FINAL
        ).initializer(CodeBlock.of("new $L()", name)).build();

        var builder = classBuilder(name)
                .addMethod(constructorBuilder().build())
                .addField(instanceField)
                .addField(typeField)
                .addModifiers(modifiers)
                .addModifiers(FINAL);

        var uniqueNames = new HashSet<String>();

        var inheritParams = false;
        var inheritSuperParams = false;
        var inheritParamsOf = false;
        var superclass = bean.getSuperclass();
        var interfaces = bean.getInterfaces();
        if (addParamsEnum) {
            var params = parameters.get();
            var typeName = getUniqueName(params.className(), uniqueNames);
            var methodName = params.methodName();
            inheritParams = Meta.Parameters.METHOD_NAME.equals(methodName);

            builder.addType(newEnumParams(typeName, bean.getTypeParameters()));
            builder.addMethod(
                    enumValuesMethod(methodName, ClassName.get("", typeName), inheritParams)
            );

            var fieldName = "inheritedParams";
            var inheritedParamsField = FieldSpec.builder(
                    ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(Class.class),
                            ParameterizedTypeName.get(ClassName.get(List.class),
                                    WildcardTypeName.subtypeOf(Typed.class))), fieldName, PUBLIC, FINAL
            );
            var inheritedParamsFieldInitializer = CodeBlock.builder().add("$T.ofEntries(\n", Map.class);

            var inherited = params.inherited();
            var parentClass = inherited != null ? inherited.parentClass() : null;
            var addSuperParams = superclass != null && parentClass != null && parentClass.enumerate();
            if (addSuperParams) {
                inheritSuperParams = Meta.Parameters.Inherited.Super.METHOD_NAME.equals(parentClass.methodName());
                var superTypeName = getUniqueName(
                        superclass.getClassName() + parentClass.classNameSuffix(), uniqueNames
                );
                builder.addType(newEnumParams(superTypeName, superclass.getTypeParameters()));
                builder.addMethod(
                        enumValuesMethod(parentClass.methodName(), ClassName.get("", superTypeName), inheritSuperParams)
                );
                addInheritedParams(
                        inheritedParamsFieldInitializer,
                        ClassName.get(Meta.Parameters.Inherited.Super.class),
                        superTypeName, false
                );
                addInheritedParams(
                        inheritedParamsFieldInitializer,
                        ClassName.get(superclass.getPackageName(), superclass.getClassName()),
                        superTypeName, true
                );
            }
            var interfacesParams = inherited != null ? inherited.interfaces() : null;
            if (interfacesParams != null && interfaces != null && interfacesParams.enumerate()) {
                inheritParamsOf = Meta.Parameters.Inherited.Interfaces.METHOD_NAME.equals(interfacesParams.methodName());
                for (int i = 0; i < interfaces.size(); i++) {
                    var iface = interfaces.get(i);
                    var ifaceParametersEnumName = getUniqueName(
                            iface.getClassName() + interfacesParams.classNameSuffix(), uniqueNames
                    );
                    builder.addType(newEnumParams(ifaceParametersEnumName, iface.getTypeParameters()));
                    addInheritedParams(inheritedParamsFieldInitializer, ClassName.get(iface.getPackageName(), iface.getClassName()), ifaceParametersEnumName, addSuperParams || i > 0);
                }
                inheritedParamsField.initializer(inheritedParamsFieldInitializer.add("\n)").build());
                builder.addField(inheritedParamsField.build());
                var code = CodeBlock.builder()
                        .addStatement("return $L.get(type)", fieldName)
                        .build();
                builder.addMethod(enumValuesMethodBuilder(interfacesParams.methodName(),
                        WildcardTypeName.subtypeOf(Typed.class), inheritParamsOf, code
                ).addParameter(ParameterSpec.builder(Class.class, "type").build()).build());
            }
        }

        var inheritProps = false;
        if (addFieldsEnum) {
            var propsInfo = props.get();
            inheritProps = Meta.Properties.METHOD_NAME.equals(propsInfo.methodName());
            var typeName = ClassName.get("", getUniqueName(propsInfo.className(), uniqueNames));
            var classTypeVar = TypeVariableName.get("T");
            var getter = getterType(bean, classTypeVar);
            var fieldsBuilder = typeAwareClass(typeName, classTypeVar);

            var propertyNames = new LinkedHashSet<String>();
            var properties = bean.getProperties();
            for (var property : properties) {
                var propertyName = property.getName();
                if (!propertyNames.add(propertyName)) {
                    throw new IllegalStateException("property already handled, " + propertyName);
                }
                addConstant(typeName, fieldsBuilder, bean.getTypeParameters(), propertyName, property.getType(), property, getter);
            }

            if (superclass != null) {
                var superProperties = superclass.getProperties();
                for (var property : superProperties) {
                    var propertyName = property.getName();
                    if (!propertyNames.contains(propertyName)) {
                        propertyNames.add(propertyName);
                        addConstant(typeName, fieldsBuilder, superclass.getTypeParameters(), propertyName, property.getType(), property, getter);
                    }
                }
            }

            uniqueNames.addAll(propertyNames);

            fieldsBuilder = populateTypeAwareClass(fieldsBuilder, classTypeVar, getter, uniqueNames);
            fieldsBuilder = addValues(fieldsBuilder, typeName, propertyNames, uniqueNames);

            builder.addType(fieldsBuilder.build());
            builder.addMethod(
                    enumValuesMethod(propsInfo.methodName(), typeName, inheritProps)
            );
        }

        var inheritMetamodel = inheritParams && inheritParamsOf && inheritProps;
        if (inheritMetamodel) {
            typeGetter.addAnnotation(Override.class);
        }
        builder.addMethod(typeGetter.build());

        if (inheritMetamodel) {
            builder.addSuperinterface(ParameterizedTypeName.get(
                    ClassName.get(MetaModel.class), className
            ));
        } else {
            if (inheritParams && inheritParamsOf) {
                builder.addSuperinterface(ClassName.get(ParametersAware.class));
            }
            if (inheritProps) {
                builder.addSuperinterface(ClassName.get(PropertiesAware.class));
            }
        }
        if (inheritSuperParams) {
            builder.addSuperinterface(ClassName.get(SuperParametersAware.class));
        }

        var srcModifiers = ofNullable(bean.getModifiers()).orElse(Set.of());
        var accessLevel = srcModifiers.contains(PRIVATE) ? PRIVATE
                : srcModifiers.contains(PROTECTED) ? PROTECTED
                : srcModifiers.contains(PUBLIC) ? PUBLIC : null;
        if (accessLevel != null) {
            builder.addModifiers(accessLevel);
        }

        var nestedTypes = ofNullable(bean.getNestedTypes()).orElse(List.of());
        for (var nestedBean : nestedTypes) {
            var beanClassName = nestedBean.getClassName();
            var nestedName = getUniqueName(beanClassName, uniqueNames);
            if (!beanClassName.equals(nestedName)) {
                nestedBean = nestedBean.toBuilder().className(nestedName).build();
            }
            builder.addType(newTypeBean(nestedBean, STATIC));
        }

//        if (addParamsEnum) {
//            if (interfaces != null) interfaces.forEach((interfaceMeta) -> {
//                var interfaceName = getUniqueNestedTypeName(interfaceMeta.getClassName(), uniqueNames);
//                builder.addType(newTypeInterface(interfaceName, meta.get().params().className(), interfaceMeta));
//            });
//        }

        return builder.build();
    }

    @NotNull
    private static Builder addValues(Builder builder, ClassName typeName, Set<String> propertyNames, Set<String> uniqueNames) {
        var valuesField = getUniqueName("values", uniqueNames);
        return builder
                .addField(listField(valuesField, typeName, CodeBlock.builder()
                        .add(propertyNames.stream().reduce((l, r) -> l + (!l.isEmpty() ? ", " : "") + r)
                                .orElse("")).build(), PRIVATE, FINAL, STATIC)
                )
                .addMethod(MethodSpec.methodBuilder("values")
                        .addModifiers(PUBLIC, FINAL, STATIC)
                        .returns(ParameterizedTypeName.get(ClassName.get(List.class), typeName))
                        .addStatement("return " + valuesField).build());
    }

    public static void addInheritedParams(
            CodeBlock.Builder mapInitializer, ClassName mapKey, String paramsEnumName, boolean addComma
    ) {
        var mapValue = CodeBlock.builder().add("$L.values()", paramsEnumName).build().toString();
        addMapEntry(mapInitializer, mapKey, mapValue, addComma);
    }

    public static void addMapEntry(
            CodeBlock.Builder mapInitializer, ClassName mapKey, String mapValue, boolean addComma
    ) {
        mapInitializer.indent();
        if (addComma) {
            mapInitializer.add(",\n");
        }
        mapInitializer.add(mapEntry(mapKey, mapValue)).unindent();
    }

    @NotNull
    public static CodeBlock mapEntry(ClassName mapKey, String mapValue) {
        return CodeBlock.builder().add(
                "$T.entry($L.class, $L)",
                Map.class,
                mapKey,
                mapValue).build();
    }

    @NotNull
    public static CodeBlock mapEntry(String mapKey, String mapValue) {
        return CodeBlock.builder().add(
                "$T.entry($L, $L)",
                Map.class,
                mapKey,
                mapValue).build();
    }

    private static String getUniqueName(String name, Collection<String> uniqueNames) {
        while (uniqueNames.contains(name)) {
            name = "_" + name;
        }
        uniqueNames.add(name);
        return name;
    }

    private static void addConstant(ClassName className, Builder fieldsBuilder, List<MetaBean.Param> typeParameters,
                                    String propertyName, TypeMirror propertyType, MetaBean.Property property,
                                    Getter getter) {
        var setter = property.getSetter();
        var recordComponent = property.getRecordComponent();
        var field = property.getField();

        var type = getType(propertyType, typeParameters);
        var typeArg = dotClass(type);
        var propertyGetter = property.getGetter();
        final CodeBlock getterArg;
        if (propertyGetter != null) {
            var readAccessorName = propertyGetter.getSimpleName();
            getterArg = CodeBlock.builder().add("$T::$L", getter.beanType(), readAccessorName).build();
        } else if (recordComponent != null) {
            var readAccessorName = recordComponent.getAccessor().getSimpleName();
            getterArg = CodeBlock.builder().add("$T::$L", getter.beanType(), readAccessorName).build();
        } else if (field != null) {
            getterArg = CodeBlock.builder().add("bean -> bean.$L", field.getSimpleName()).build();
        } else {
            getterArg = CodeBlock.builder().add("bean -> {\n")
                    .indent()
                    .addStatement(
                            "throw new UnsupportedOperationException(\"readonly property '" + propertyName + "'\")"
                    )
                    .unindent()
                    .add("}").build();
        }

        TypeName typeVariableName = type;//TypeVariableName.get(type);
        typeVariableName = typeVariableName.isPrimitive() ? typeVariableName.box() : typeVariableName;

        fieldsBuilder.addField(FieldSpec.builder(ParameterizedTypeName.get(className, typeVariableName),
                        propertyName, PUBLIC, FINAL, STATIC)
                .initializer(newInstanceCall(className, propertyName, typeArg, getterArg)).build());
    }

    @NotNull
    public static FieldSpec listField(String name, TypeName type, CodeBlock init, Modifier... modifiers) {
        return builder(ParameterizedTypeName.get(ClassName.get(List.class), type), name, modifiers)
                .initializer(CodeBlock.builder().add("$T.of($L)", List.class, init).build())
                .build();
    }

    @NotNull
    public static FieldSpec mapField(String name, ClassName key, ClassName value, CodeBlock init, Modifier... modifiers) {
        return builder(
                ParameterizedTypeName.get(ClassName.get(Map.class), key, value), name, modifiers
        ).initializer(init).build();
    }

    @NotNull
    public static CodeBlock.Builder initMapByEntries(List<String> entries) {
        var init = CodeBlock.builder().add("$T.ofEntries(\n", Map.class);
        for (int i = 0; i < entries.size(); i++) {
            var mapPart = entries.get(i);
            if (i > 0) {
                init.add(",\n");
            }
            init.add(mapPart);
        }
        init.add("\n)");
        return init;
    }

    private record Getter(ParameterizedTypeName functionType, ClassName beanType) {

    }

    record TypeInfo(DeclaredType declaredType, TypeElement typeElement) {

    }
}
