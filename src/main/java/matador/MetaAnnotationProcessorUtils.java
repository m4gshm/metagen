package matador;

import io.jbock.javapoet.*;
import lombok.experimental.UtilityClass;
import matador.MetaBean.BeanBuilder;
import org.jetbrains.annotations.NotNull;

import javax.annotation.processing.Messager;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.jbock.javapoet.FieldSpec.builder;
import static io.jbock.javapoet.MethodSpec.constructorBuilder;
import static io.jbock.javapoet.MethodSpec.methodBuilder;
import static io.jbock.javapoet.TypeSpec.classBuilder;
import static java.beans.Introspector.decapitalize;
import static java.util.Optional.of;
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
            var evaluatedType = evalType(paramType);
            params.add(MetaBean.Param.builder()
                    .name(paramName)
                    .type(paramType)
                    .evaluatedType(evaluatedType)
                    .build());
        }
        return params;
    }

    static void updateType(MetaBean.Property property, TypeMirror propType, List<MetaBean.Param> beanParameters) {
        var existType = property.getType();
        if (existType == null) {
            property.setType(propType);
            property.setEvaluatedType(evalType(propType, beanParameters));
        } else if (!existType.equals(propType)) {
            //todo set Object or shared parent type
//            property.setType(null);
        }
    }

    static boolean isObjectType(TypeElement type) {
        return "java.lang.Object".equals(type.getQualifiedName().toString());
    }

    static CodeBlock newInstanceCall(TypeName className, CodeBlock args) {
        return CodeBlock.builder().add("new $T<>($L)", className, args).build();
    }

    @NotNull
    private static CodeBlock enumConstructorArgs(String name, String type, CodeBlock getter, CodeBlock setter) {
        var builder = enumConstructorArgs(name, type).toBuilder();
        if (getter != null) {
            builder.add(", ").add(getter);
        }
        if (setter != null) {
            builder.add(", ").add(setter);
        }
        return builder.build();
    }

    @NotNull
    private static CodeBlock enumConstructorArgs(String name, String type) {
        return CodeBlock.builder().add("\"" + name + "\"").add(", ").add(type).build();
    }

    static String dotClass(TypeName type) {
        return (type != null ? type : TypeName.OBJECT) + ".class";
    }

    static TypeMirror evalType(TypeMirror type) {
        return evalType(type, List.of());
    }

    static TypeMirror evalType(TypeMirror type, List<MetaBean.Param> beanParameters) {
        return type instanceof TypeVariable typeVariable ? evalType(typeVariable, beanParameters)
                : type instanceof IntersectionType intersectionType ? evalType(intersectionType, beanParameters)
                : type instanceof DeclaredType dt ? dt.asElement().asType()
                : type instanceof ArrayType || type instanceof PrimitiveType ? type : null;
    }

    private static TypeMirror evalType(IntersectionType intersectionType, List<MetaBean.Param> beanParameters) {
        return evalType(intersectionType.getBounds().get(0), beanParameters);
    }

    private static TypeMirror evalType(TypeVariable typeVariable, List<MetaBean.Param> beanParameters) {
        var collect = beanParameters != null
                ? beanParameters.stream().collect(toMap(p -> p.getName().asType(), p -> p.getType()))
                : Map.<TypeMirror, TypeMirror>of();
        var type = collect.get(typeVariable);
        if (type != null && !type.equals(typeVariable)) {
            return evalType(type, beanParameters);
        } else {
            return evalType(typeVariable.getUpperBound(), beanParameters);
        }
    }

    private static TypeSpec newEnumParams(String enumName, List<MetaBean.Param> beanTypeParameters) {
        var className = ClassName.get("", enumName);
        var typeVariable = TypeVariableName.get("T");
        var typesBuilder = typeAwareClass(className, typeVariable);
        var paramNames = new LinkedHashSet<String>();
        for (var param : beanTypeParameters) {
            var name = param.getName().getSimpleName().toString();
            paramNames.add(name);
            var type = TypeName.get(param.getEvaluatedType());
            typesBuilder.addField(FieldSpec.builder(ParameterizedTypeName.get(className,
                            getUnboxedTypeVarName(type)), name, PUBLIC, FINAL, STATIC)
                    .initializer(newInstanceCall(className, enumConstructorArgs(name, dotClass(type)))).build());
        }
        var uniqueNames = new HashSet<String>(paramNames);

        var nameFieldName = getUniqueName("name", uniqueNames);
        var typeFieldName = getUniqueName("type", uniqueNames);
        var nameArgType = ClassName.get(String.class);
        var typeArgType = ParameterizedTypeName.get(ClassName.get(Class.class), typeVariable);
        typesBuilder = populateTypeAwareClass(typesBuilder, nameFieldName, typeFieldName, nameArgType, typeArgType);

        var constructor = constructorBuilder();
        var constructorBody = CodeBlock.builder();

        populateConstructor(constructor, constructorBody, nameArgType, nameFieldName, typeArgType, typeFieldName);

        typesBuilder.addMethod(constructor.addCode(constructorBody.build()).build());

        typesBuilder = addValues(typesBuilder, className, paramNames, uniqueNames);
        return typesBuilder.build();
    }

    @NotNull
    private static TypeSpec.Builder populateTypeAwareClass(TypeSpec.Builder fieldsClassBuilder, String nameFieldName, String typeFieldName,
                                                           ClassName nameArgType, ParameterizedTypeName typeArgType) {
        return fieldsClassBuilder
                .addField(FieldSpec.builder(nameArgType, nameFieldName).addModifiers(PUBLIC, FINAL).build())
                .addField(FieldSpec.builder(typeArgType, typeFieldName).addModifiers(PUBLIC, FINAL).build())
                .addMethod(
                        methodBuilder("name")
                                .addAnnotation(Override.class)
                                .addModifiers(PUBLIC).returns(nameArgType)
                                .addStatement("return this." + nameFieldName)
                                .build()
                )
                .addMethod(
                        methodBuilder("type")
                                .addAnnotation(Override.class)
                                .addModifiers(PUBLIC).returns(typeArgType)
                                .addStatement("return this." + typeFieldName)
                                .build()
                );
    }

    private static void addGetter(
            TypeSpec.Builder fieldsClassBuilder, ClassName beanType, TypeVariableName propertyTypeVar,
            ParameterizedTypeName getterType, String getterName
    ) {
        fieldsClassBuilder.addField(FieldSpec.builder(getterType, getterName).addModifiers(PUBLIC, FINAL).build());
        fieldsClassBuilder.addMethod(
                methodBuilder("get")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .addParameter(beanType, "bean")
                        .returns(propertyTypeVar)
                        .addStatement("return this." + getterName + ".apply(bean)")
                        .build()
        );
    }

    private static void addSetter(
            TypeSpec.Builder builder, ClassName beanType, TypeVariableName propertyTypeVar,
            ParameterizedTypeName setterType, String setterName
    ) {
        builder.addField(FieldSpec.builder(setterType, setterName).addModifiers(PUBLIC, FINAL).build());
        builder.addMethod(
                methodBuilder("set")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .addParameter(beanType, "bean")
                        .addParameter(propertyTypeVar, "value")
                        .addStatement(setterName + ".accept(bean, value)")
                        .build()
        );
    }

    @NotNull
    private static ParameterizedTypeName getFunctionType(ClassName beanType, TypeVariableName propertyTypeVar) {
        return ParameterizedTypeName.get(ClassName.get(Function.class), beanType, propertyTypeVar);
    }

    @NotNull
    private static ParameterizedTypeName getBiFunctionType(ClassName beanType, TypeVariableName propertyTypeVar, TypeName returnType) {
        return ParameterizedTypeName.get(ClassName.get(BiFunction.class), beanType, propertyTypeVar, returnType);
    }

    @NotNull
    private static ParameterizedTypeName getBiConsumerType(ClassName beanType, TypeVariableName propertyTypeVar) {
        return ParameterizedTypeName.get(ClassName.get(BiConsumer.class), beanType, propertyTypeVar);
    }

    @NotNull
    private static TypeSpec.Builder typeAwareClass(ClassName className, TypeVariableName typeVariable) {
        return classBuilder(className)
                .addTypeVariable(typeVariable)
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Typed.class), typeVariable))
                .addModifiers(PUBLIC, STATIC);
    }

    private static void populateConstructor(MethodSpec.Builder constructor, CodeBlock.Builder constructorBody,
                                            ClassName nameType, String nameName, TypeName typeType, String typeName) {
        constructor.addModifiers(PRIVATE)
                .addParameter(nameType, nameName)
                .addParameter(typeType, typeName);

        constructorBody
                .addStatement("this." + nameName + " = " + nameName)
                .addStatement("this." + typeName + " = " + typeName);
    }

    static MetaBean getBean(Messager messager, TypeElement type, DeclaredType declaredType) {
        if (type == null || isObjectType(type)) {
            return null;
        }

        var typeParameters = extractGenericParams(type, declaredType);

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
                updateType(property, propType, typeParameters);
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
                    updateType(property, propType, typeParameters);
                }
            } else if (!isStatic && isPublic && enclosedElement instanceof VariableElement ve) {
                var propType = ve.asType();
                var property = getProperty(properties, ve.getSimpleName().toString(), annotationMirrors);
                property.setField(ve);
                updateType(property, propType, typeParameters);
            } else if (enclosedElement instanceof TypeElement te && enclosedElement.getAnnotation(Meta.class) != null) {
                var nestedBean = getBean(messager, te, null);
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
                getBean(messager, superclass.typeElement, superclass.declaredType)
        ).orElse(null);

        var interfaceBeans = type.getInterfaces().stream().map(MetaAnnotationProcessorUtils::getTypeInfo)
                .filter(Objects::nonNull).map(iface -> getBean(messager, iface.typeElement, iface.declaredType))
                .toList();

        var name = type.getSimpleName().toString();
        var suffix = ofNullable(meta).map(Meta::suffix).map(String::trim).filter(m -> !m.isEmpty()).orElse(Meta.META);
        var builder = ofNullable(meta).map(Meta::builder);
        var superBuilderInfo = superBean != null ? superBean.getBeanBuilderInfo() : null;
        var beanBuilder = builder.map(Meta.Builder::detect).orElse(false)
                ? newBeanBuilder(messager, type, typeParameters, builder.map(Meta.Builder::className).orElse(Meta.Builder.CLASS_NAME), superBuilderInfo) : null;

        return MetaBean.builder()
                .isRecord(isRecord)
                .type(type)
                .meta(meta)
                .name(name + suffix)
                .superclass(superBean)
                .interfaces(interfaceBeans)
                .nestedTypes(new ArrayList<>(nestedTypes.values()))
                .properties(new ArrayList<>(properties.values()))
                .modifiers(type.getModifiers())
                .typeParameters(typeParameters)
                .annotations(annotations)
                .beanBuilderInfo(beanBuilder)
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

    static MethodSpec callValuesMethod(String name, TypeName typeClassName, boolean overr) {
        return returnListMethodBuilder(
                name, typeClassName, overr,
                CodeBlock.builder().addStatement("return $T.values()", typeClassName).build()
        ).build();
    }

    static MethodSpec.Builder returnListMethodBuilder(String name, TypeName typeClassName, boolean overr, CodeBlock code) {
        var builder = methodBuilder(name)
                .addModifiers(PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(List.class), typeClassName))
                .addCode(code);
        if (overr) {
            builder.addAnnotation(Override.class);
        }
        return builder;
    }

    static TypeSpec newTypeBean(MetaBean bean, Modifier... modifiers) {
        var meta = ofNullable(bean.getMeta());
        var props = meta.map(Meta::properties);
        var parameters = meta.map(Meta::params);

        var addFieldsEnum = props.map(Meta.Properties::enumerate).orElse(false);
        var addParamsEnum = parameters.map(Meta.Parameters::enumerate).orElse(false);

        var beanType = ClassName.get(bean.getType());

        var name = bean.getName();
        var typeFieldType = ParameterizedTypeName.get(ClassName.get(Class.class), beanType);
        var typeGetter = methodBuilder("type")
                .addModifiers(PUBLIC, FINAL)
                .returns(typeFieldType)
                .addCode(CodeBlock.builder()
                        .addStatement("return type")
                        .build());

        var typeField = FieldSpec.builder(
                        typeFieldType, "type", PUBLIC, FINAL)
                .initializer(CodeBlock.builder().addStatement("$T.class", beanType).build())
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
                    callValuesMethod(methodName, ClassName.get("", typeName), inheritParams)
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
                        callValuesMethod(parentClass.methodName(), ClassName.get("", superTypeName), inheritSuperParams)
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
                builder.addMethod(returnListMethodBuilder(
                        interfacesParams.methodName(),
                        WildcardTypeName.subtypeOf(Typed.class),
                        inheritParamsOf,
                        CodeBlock.builder().addStatement("return $L.get(type)", fieldName).build()
                ).addParameter(ParameterSpec.builder(Class.class, "type").build()).build());
            }
        }

        var inheritProps = false;
        if (addFieldsEnum) {
            var propsInfo = props.get();
            inheritProps = Meta.Properties.METHOD_NAME.equals(propsInfo.methodName());
            var typeName = ClassName.get("", getUniqueName(propsInfo.className(), uniqueNames));
            var typeVariable = TypeVariableName.get("T");
            var fieldsClassBuilder = typeAwareClass(typeName, typeVariable);

            var propertyPerName = new LinkedHashMap<String, MetaBean.Property>();
            var allReadable = true;
            var allWritable = true;
            for (var property : bean.getProperties()) {
                var propertyName = property.getName();
                if (propertyPerName.put(propertyName, property) != null) {
                    throw new IllegalStateException("property already handled, " + propertyName);
                }
                allReadable &= isReadable(property);
                allWritable &= isWriteable(property);
            }
            if (superclass != null) {
                var superProperties = superclass.getProperties();
                for (var property : superProperties) {
                    var propertyName = property.getName();
                    if (!propertyPerName.containsKey(propertyName)) {
                        propertyPerName.put(propertyName, property);
                        allReadable &= isReadable(property);
                        allWritable &= isWriteable(property);
                    }
                }
            }

            final ClassName readTypeName, writeTypeName, readWriteTypeName;
            if (!allReadable) {
                readTypeName = ClassName.get("", getUniqueName("Read", uniqueNames));
            } else {
                readTypeName = typeName;
            }

            if (!allWritable) {
                writeTypeName = ClassName.get("", getUniqueName("Write", uniqueNames));
            } else {
                writeTypeName = typeName;
            }

            if (!allReadable || !allWritable) {
                readWriteTypeName = ClassName.get("", getUniqueName("ReadWrite", uniqueNames));
            } else {
                readWriteTypeName = typeName;
            }

            var rwUsed = false;
            var readUsed = false;
            var writeUsed = false;
            for (var property : propertyPerName.values()) {
                var read = isReadable(property);
                var writable = isWriteable(property);
                var full = read && writable;
                if (full) {
                    rwUsed = true;
                } else if (read) {
                    readUsed = true;
                } else if (writable) {
                    writeUsed = true;
                }
                var typ = full ? readWriteTypeName : read ? readTypeName : writable ? writeTypeName : typeName;
                fieldsClassBuilder.addField(newPropertyConstant(beanType, property, typ));
            }

            var readWriteInterface = ParameterizedTypeName.get(ClassName.get(ReadWrite.class), beanType, typeVariable);
            var writeInterface = writeInterface(beanType, typeVariable);
            var readInterface = ParameterizedTypeName.get(ClassName.get(Read.class), beanType, typeVariable);

            var nameArgType = ClassName.get(String.class);
            var typeArgType = ParameterizedTypeName.get(ClassName.get(Class.class), typeVariable);

            if (rwUsed && !readWriteTypeName.equals(typeName)) {
                var getterArgName = "getter";
                var setterArgName = "setter";
                var getter = getFunctionType(beanType, typeVariable);
                var setter = getBiConsumerType(beanType, typeVariable);

                var constructor = constructorBuilder().addModifiers(PRIVATE)
                        .addParameter(nameArgType, "name")
                        .addParameter(typeArgType, "type")
                        .addParameter(getter, getterArgName)
                        .addParameter(setter, setterArgName);

                var constructorBody = CodeBlock.builder()
                        .addStatement("super($L, $L)", "name", "type")
                        .addStatement("this." + getterArgName + " = " + getterArgName)
                        .addStatement("this." + setterArgName + " = " + setterArgName);

                constructor.addCode(constructorBody.build());

                var subType = classBuilder(readWriteTypeName)
                        .addModifiers(STATIC, FINAL, PUBLIC)
                        .superclass(ParameterizedTypeName.get(typeName, typeVariable))
                        .addTypeVariable(typeVariable)
                        .addSuperinterface(readWriteInterface)
                        .addMethod(constructor.build());
                addGetter(subType, beanType, typeVariable, getter, getterArgName);
                addSetter(subType, beanType, typeVariable, setter, setterArgName);

                fieldsClassBuilder.addType(subType.build());
            }

            if (readUsed && !readTypeName.equals(typeName)) {
                var getterArgName = "getter";
                var getter = getFunctionType(beanType, typeVariable);

                var constructor = constructorBuilder().addModifiers(PRIVATE)
                        .addParameter(nameArgType, "name")
                        .addParameter(typeArgType, "type")
                        .addParameter(getter, getterArgName);

                var constructorBody = CodeBlock.builder()
                        .addStatement("super($L, $L)", "name", "type")
                        .addStatement("this." + getterArgName + " = " + getterArgName);

                constructor.addCode(constructorBody.build());

                var subType = classBuilder(readTypeName)
                        .addModifiers(STATIC, FINAL, PUBLIC)
                        .superclass(ParameterizedTypeName.get(typeName, typeVariable))
                        .addTypeVariable(typeVariable)
                        .addSuperinterface(readInterface)
                        .addMethod(constructor.build());

                addGetter(subType, beanType, typeVariable, getter, getterArgName);

                fieldsClassBuilder.addType(subType.build());
            }

            if (writeUsed && !writeTypeName.equals(typeName)) {
                var setterArgName = "setter";
                var setter = getBiConsumerType(beanType, typeVariable);

                var constructor = constructorBuilder().addModifiers(PRIVATE)
                        .addParameter(nameArgType, "name")
                        .addParameter(typeArgType, "type")
                        .addParameter(setter, setterArgName);

                var constructorBody = CodeBlock.builder()
                        .addStatement("super($L, $L)", "name", "type")
                        .addStatement("this." + setterArgName + " = " + setterArgName);

                constructor.addCode(constructorBody.build());

                var subType = classBuilder(writeTypeName)
                        .addModifiers(STATIC, FINAL, PUBLIC)
                        .superclass(ParameterizedTypeName.get(typeName, typeVariable))
                        .addTypeVariable(typeVariable)
                        .addSuperinterface(writeInterface)
                        .addMethod(constructor.build());

                addSetter(subType, beanType, typeVariable, setter, setterArgName);

                fieldsClassBuilder.addType(subType.build());
            }

            uniqueNames.addAll(propertyPerName.keySet());

            var nameFieldName = getUniqueName("name", uniqueNames);
            var typeFieldName = getUniqueName("type", uniqueNames);

            fieldsClassBuilder = populateTypeAwareClass(fieldsClassBuilder,
                    nameFieldName, typeFieldName, nameArgType, typeArgType);

            var constructor = constructorBuilder();
            var constructorBody = CodeBlock.builder();

            populateConstructor(constructor, constructorBody, nameArgType, nameFieldName, typeArgType, typeFieldName);

            if (allReadable) {
                var getterFieldName = getUniqueName("getter", uniqueNames);
                var getterFunction = getFunctionType(beanType, typeVariable);

                addGetter(fieldsClassBuilder, beanType, typeVariable, getterFunction, getterFieldName);

                constructor.addParameter(getterFunction, "getter");
                constructorBody.addStatement("this." + getterFieldName + " = " + "getter");
            }

            if (allWritable) {
                var setterFieldName = getUniqueName("setter", uniqueNames);
                var setterConsumer = getBiConsumerType(beanType, typeVariable);

                addSetter(fieldsClassBuilder, beanType, typeVariable, setterConsumer, setterFieldName);

                constructor.addParameter(setterConsumer, "setter");
                constructorBody.addStatement("this." + setterFieldName + " = " + "setter");
            }

            if (allReadable && allWritable) {
                fieldsClassBuilder.addSuperinterface(readWriteInterface);
            } else if (allReadable) {
                fieldsClassBuilder.addSuperinterface(readInterface);
            } else if (allWritable) {
                fieldsClassBuilder.addSuperinterface(writeInterface);
            }

            fieldsClassBuilder.addMethod(constructor.addCode(constructorBody.build()).build());

            fieldsClassBuilder = addValues(fieldsClassBuilder, typeName, propertyPerName.keySet(), uniqueNames);

            builder.addType(fieldsClassBuilder.build());
            builder.addMethod(
                    callValuesMethod(propsInfo.methodName(), typeName, inheritProps)
            );
        }

        var inheritMetamodel = inheritParams && inheritParamsOf && inheritProps;
        if (inheritMetamodel) {
            typeGetter.addAnnotation(Override.class);
        }
        builder.addMethod(typeGetter.build());

        if (inheritMetamodel) {
            builder.addSuperinterface(ParameterizedTypeName.get(
                    ClassName.get(MetaModel.class), beanType
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

        var beanBuilderInfo = bean.getBeanBuilderInfo();
        if (beanBuilderInfo != null) {
            builder.addType(newBuilderType(beanBuilderInfo));
        }

        return builder.build();
    }

    @NotNull
    private static ParameterizedTypeName writeInterface(ClassName beanType, TypeVariableName typeVariable) {
        return ParameterizedTypeName.get(ClassName.get(Write.class), beanType, typeVariable);
    }

    private static TypeSpec newBuilderType(BeanBuilder builderInfo) {
        var type = builderInfo.getType();
        var beanType = ClassName.get(type);

        var metaClassName = builderInfo.getMetaClassName();
        var className = ClassName.get("", metaClassName);
        var typeVariable = TypeVariableName.get("T");
        var builder = typeAwareClass(className, typeVariable).addModifiers(FINAL);

        var setterNames = new ArrayList<String>();
        for (var setter : builderInfo.getSetters()) {
            var setterName = setter.getName();
            builder.addField(newPropertyConstant(
                    beanType, setter.getEvaluatedType(), className, setterName,
                    null, null, setter.getSetter(), null));
            setterNames.add(setterName);
        }
        var uniqueNames = new HashSet<>(setterNames);

        var nameFieldName = getUniqueName("name", uniqueNames);
        var typeFieldName = getUniqueName("type", uniqueNames);
        var setterFieldName = getUniqueName("setter", uniqueNames);
        var setterArgName = "setter";

        var nameArgType = ClassName.get(String.class);
        var typeArgType = ParameterizedTypeName.get(ClassName.get(Class.class), typeVariable);
        var setterType = getBiConsumerType(beanType, typeVariable);

        builder = populateTypeAwareClass(builder, nameFieldName, typeFieldName, nameArgType, typeArgType);

        var constructor = constructorBuilder();
        var constructorBody = CodeBlock.builder();
        populateConstructor(constructor, constructorBody, nameArgType, nameFieldName, typeArgType, typeFieldName);

        constructor.addParameter(setterType, setterArgName);
        constructorBody.addStatement("this." + setterFieldName + " = " + setterArgName);

        constructor.addCode(constructorBody.build());

        builder.addMethod(constructor.build());

        builder.addField(FieldSpec.builder(setterType, setterFieldName).addModifiers(PUBLIC, FINAL).build());
        builder.addMethod(
                methodBuilder("set")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .addParameter(beanType, "builder")
                        .addParameter(typeVariable, "value")
                        .addStatement(setterFieldName + ".accept(builder, value)")
                        .build()
        );

        addValues(builder, className, setterNames, uniqueNames);

        builder.addSuperinterface(writeInterface(beanType, typeVariable));
        return builder.build();
    }

    private static BeanBuilder newBeanBuilder(
            Messager messager, TypeElement beanType, List<MetaBean.Param> typeParameters,
            String metaClassName, BeanBuilder superBuilder) {
        var builderAnnotation = beanType.getAnnotationMirrors().stream().filter(a -> {
            var name = a.getAnnotationType().toString();
            return Set.of(
                    "lombok.Builder",
                    "lombok.SuperBuilder",
                    "lombok.experimental.SuperBuilder"
            ).contains(name);
        }).findAny().orElse(null);
        if (builderAnnotation != null) {
            var isInheritor = builderAnnotation.getAnnotationType().toString().contains("SuperBuilder");
            var annotationType = builderAnnotation.getAnnotationType();
            var elementValues = builderAnnotation.getElementValues();
            var values = elementValues.entrySet().stream().collect(toMap(e -> e.getKey().toString(), e -> e.getValue().getValue()));

            var defaultValues = annotationType.asElement().getEnclosedElements().stream()
                    .map(e -> e instanceof ExecutableElement ee ? ee : null).filter(Objects::nonNull)
                    .collect(toMap(e -> e.getSimpleName().toString(), e -> ofNullable(e.getDefaultValue()).map(AnnotationValue::getValue).orElse("")));

            var builderClassName = of(getAnnotationValue("builderClassName", values, defaultValues))
                    .map(v -> v.isEmpty() ? beanType.getSimpleName() + "Builder" : v).get();

            var builderType = beanType.getEnclosedElements().stream()
                    .map(e -> e instanceof TypeElement te ? te : null)
                    .filter(e -> e != null && e.getSimpleName().contentEquals(builderClassName)
                            && beanType.equals(e.getEnclosingElement())
                    ).findFirst().orElse(null);

            if (builderType == null) {
                messager.printWarning("cannot determine builder class '" + builderClassName + "'", beanType);
                return null;
            } else {
                var builderMethodName = getAnnotationValue("builderMethodName", values, defaultValues);
                var buildMethodName = getAnnotationValue("buildMethodName", values, defaultValues);
                var setterPrefix = getAnnotationValue("setterPrefix", values, defaultValues);

                var setters = getBuilderSetters(builderType, isInheritor ? superBuilder : null);

                return BeanBuilder.builder()
                        .metaClassName(metaClassName)
                        .className(builderClassName)
                        .setPrefix(setterPrefix)
                        .type(builderType)
                        .typeParameters(typeParameters)
                        .builderMethodName(builderMethodName)
                        .buildMethodName(buildMethodName)
                        .setters(setters)
                        .build();
            }
        }
        return null;
    }

    private static ArrayList<BeanBuilder.Setter> getBuilderSetters(TypeElement typeElement, BeanBuilder superBuilder) {
        var setters = new ArrayList<BeanBuilder.Setter>();
        var builderType = typeElement.asType();
        var element = typeElement;
        DeclaredType declaredType = null;
        while (element != null && !isObjectType(element)) {
            var typeParameters = extractGenericParams(element, declaredType);
            var builderElements = element.getEnclosedElements();
            for (var enclosedElement : builderElements) {
                var modifiers = enclosedElement.getModifiers();
                var isPublic = modifiers.contains(PUBLIC);
                var isStatic = modifiers.contains(STATIC);
                if (!isStatic && isPublic && enclosedElement instanceof ExecutableElement ee) {
                    var returnType = evalType(ee.getReturnType(), typeParameters);
                    var returnSame = builderType.equals(returnType);
                    var parameters = ee.getParameters();
                    if (returnSame && parameters.size() == 1) {
                        var paramElement = parameters.get(0);
                        var paramType = paramElement.asType();
                        var evaluatedType = evalType(paramType, typeParameters);
                        setters.add(BeanBuilder.Setter.builder().type(paramType).evaluatedType(evaluatedType)
                                .name(ee.getSimpleName().toString()).setter(ee).build());
                    }
                }
            }
            if (element.getSuperclass() instanceof DeclaredType dt) {
                if (dt.asElement() instanceof TypeElement te) {
                    if (superBuilder != null && dt.getKind() == TypeKind.ERROR) {
                        //actual to the Lombok @SuperBuilder
                        var typeArguments = dt.getTypeArguments();

                        var superBuilderType = superBuilder.getType();
                        var superParameters = superBuilderType.getTypeParameters();
                        var actualizedSuperSetters = superBuilder.getSetters().stream().map(setter -> {
                            if (setter.getType() instanceof TypeVariable typeVariable) {
                                var i = getIndex(typeVariable.asElement(), superParameters);
                                var typeMirror = i >= 0 && i < typeArguments.size() ? typeArguments.get(i) : null;
                                return typeMirror != null ? setter.toBuilder().evaluatedType(typeMirror).build() : setter;
                            }
                            return setter;
                        }).toList();
                        setters.addAll(actualizedSuperSetters);
                        element = null;
                        declaredType = null;
                    } else {
                        element = te;
                        declaredType = dt;
                    }
                } else {
                    element = null;
                    declaredType = null;
                }
            } else {
                break;
            }
        }
        return setters;
    }

    private static int getIndex(Element typeVariable, List<? extends TypeParameterElement> typeParameters) {
        if (typeVariable == null) {
            return -1;
        }
        for (int i = 0; i < typeParameters.size(); i++) {
            var typeParameter = typeParameters.get(i);
            if (typeVariable.equals(typeParameter)) {
                return i;
            }
        }
        return -1;
    }

    private static String getAnnotationValue(String attributeName, Map<String, Object> values, Map<String, Object> defaultValues) {
        return ofNullable(values.getOrDefault(attributeName, defaultValues.get(attributeName))).map(Object::toString).orElse("");
    }

    @NotNull
    private static TypeSpec.Builder addValues(
            TypeSpec.Builder builder, ClassName typeName, Collection<String> propertyNames, Set<String> uniqueNames
    ) {
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

    private static boolean isReadable(MetaBean.Property property) {
        var getter = property.getGetter();
        var recordComponent = property.getRecordComponent();
        var field = property.getField();
        return getter != null || recordComponent != null || field != null;
    }

    private static boolean isWriteable(MetaBean.Property property) {
        var setter = property.getSetter();
        var field = property.getField();
        return (setter != null || field != null) && property.getRecordComponent() == null;
    }

    @NotNull
    private static FieldSpec newPropertyConstant(ClassName beanType, MetaBean.Property property, ClassName constType) {
        var name = property.getName();
        var type = property.getEvaluatedType();
        var field = property.getField();
        var record = property.getRecordComponent();
        var getter = property.getGetter();
        var setter = property.getSetter();
        return newPropertyConstant(beanType, type, constType, name, field, getter, setter, record);
    }

    @NotNull
    private static FieldSpec newPropertyConstant(ClassName beanType, TypeMirror propertyType,
                                                 ClassName constType, String constName,
                                                 VariableElement field, ExecutableElement getter,
                                                 ExecutableElement setter, RecordComponentElement record) {
        final CodeBlock getterArg;
        var getterName = ofNullable(getter).map(ExecutableElement::getSimpleName).orElse(null);
        if (getterName != null) {
            getterArg = CodeBlock.builder().add("$T::$L", beanType, getterName).build();
        } else if (record != null) {
            var readAccessorName = record.getAccessor().getSimpleName();
            getterArg = CodeBlock.builder().add("$T::$L", beanType, readAccessorName).build();
        } else if (field != null) {
            getterArg = CodeBlock.builder().add("bean -> bean.$L", field.getSimpleName()).build();
        } else {
            getterArg = null;
        }

        final CodeBlock setterArg;
        var setterName = ofNullable(setter).map(ExecutableElement::getSimpleName).orElse(null);
        if (setterName != null) {
            setterArg = CodeBlock.builder().add("$T::$L", beanType, setterName).build();
        } else if (record == null && field != null) {
            setterArg = CodeBlock.builder().add("(bean, value) -> bean.$L = value", field.getSimpleName()).build();
        } else {
            setterArg = null;
        }

        var typeName = TypeName.get(propertyType);
        return FieldSpec.builder(
                ParameterizedTypeName.get(constType, getUnboxedTypeVarName(typeName)), constName, PUBLIC, FINAL, STATIC
        ).initializer(newInstanceCall(constType, enumConstructorArgs(constName, dotClass(typeName), getterArg, setterArg))).build();
    }

    private static TypeName getUnboxedTypeVarName(TypeName type) {
        return type.isPrimitive() ? type.box() : type;
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

    private record PropertyType(ParameterizedTypeName functionType, ClassName beanType) {

    }

    record TypeInfo(DeclaredType declaredType, TypeElement typeElement) {

    }
}
