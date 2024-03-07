package meta;

import io.jbock.javapoet.*;
import meta.Meta.EnumType;
import meta.MetaBean.Param;
import meta.MetaBean.Property;

import javax.annotation.processing.Generated;
import javax.annotation.processing.Messager;
import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static io.jbock.javapoet.FieldSpec.builder;
import static io.jbock.javapoet.MethodSpec.constructorBuilder;
import static io.jbock.javapoet.MethodSpec.methodBuilder;
import static io.jbock.javapoet.TypeName.OBJECT;
import static io.jbock.javapoet.TypeSpec.classBuilder;
import static io.jbock.javapoet.WildcardTypeName.subtypeOf;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.IntStream.range;
import static javax.lang.model.element.Modifier.*;
import static meta.Meta.EnumType.*;
import static meta.MetaBeanExtractor.getMethodName;

public class JavaPoetUtils {
    public static TypeSpec.Builder newMetaTypeBuilder(
            Messager messager, MetaBean bean, Collection<? extends MetaCustomizer> customizers
    ) {
        var meta = ofNullable(bean.getMeta());
        var props = meta.map(Meta::properties);
        var parameters = meta.map(Meta::params);
        var metaMethods = meta.map(Meta::methods);

        var propsEnum = props.map(Meta.Props::value).orElse(FULL);
        var paramsEnum = parameters.map(Meta.Params::value).orElse(FULL);
        var methodsEnum = metaMethods.map(Meta.Methods::value).orElse(Meta.Methods.EnumType.NONE);

        var beanType = ClassName.get(bean.getType());

        var name = bean.getName();
        var typeFieldType = typeClassOf(beanType);
        var typeGetter = methodBuilder("type")
                .addModifiers(PUBLIC, FINAL)
                .returns(typeFieldType)
                .addCode(CodeBlock.builder()
                        .addStatement("return type")
                        .build());

        var typeField = FieldSpec.builder(typeFieldType, "type", PUBLIC, FINAL)
                .initializer(dotClass(beanType))
                .build();

        var builder = classBuilder(name)
                .addAnnotation(generatedAnnotation())
                .addMethod(constructorBuilder().build())
                .addModifiers(FINAL);

        var uniqueNames = new HashSet<String>();

        var inheritParams = false;
        var inheritSuperParams = false;
        var inheritParamsOf = false;
        var superclass = bean.getSuperclass();
        var interfaces = bean.getInterfaces();

        var typeParameters = bean.getTypeParameters();

        var addParamsType = !typeParameters.isEmpty();
        var addInheritParamsOf = false;

        if (paramsEnum != NONE) {
            var params = parameters.get();
            var typeName = getUniqueName(params.className(), uniqueNames);
            var methodName = params.methodName();

            inheritParams = Meta.Params.METHOD_NAME.equals(methodName) && paramsEnum == FULL;
            if (addParamsType) {
                builder.addType(newParamsType(typeName, typeParameters, paramsEnum));
            }

            var inheritedParamsFieldName = "inheritedParams";
            var inheritedParamsField = FieldSpec.builder(
                    ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(Class.class),
                            ParameterizedTypeName.get(ClassName.get(List.class),
                                    subtypeOf(wildcardParametrized(ClassName.get(Typed.class), 1)))),
                    inheritedParamsFieldName, PUBLIC, FINAL
            );

            var inheritedParamsFieldInitializer = CodeBlock.builder().add("$T.ofEntries(\n", Map.class);

            var inherited = params.inherited();
            var parentClass = inherited != null ? inherited.parentClass() : null;
            var superTypeParams = superclass != null ? superclass.getTypeParameters() : List.<Param>of();
            var addSuperParams = !superTypeParams.isEmpty() && parentClass != null && parentClass.enumerate();
            if (addSuperParams) {
                inheritSuperParams = Meta.Params.Inherited.Super.METHOD_NAME.equals(parentClass.methodName()) && paramsEnum == FULL;
                var superTypeName = getUniqueName(
                        superclass.getClassName() + parentClass.classNameSuffix(), uniqueNames
                );
                builder.addType(newParamsType(superTypeName, superTypeParams, paramsEnum));
                if (paramsEnum == FULL) {
                    builder.addMethod(callValuesMethod(
                            parentClass.methodName(),
                            ClassName.get("", superTypeName),
                            wildcardParametrized(ClassName.get("", superTypeName), 1),
                            inheritSuperParams
                    ));
                }
                addInheritedParams(
                        inheritedParamsFieldInitializer,
                        ClassName.get(Meta.Params.Inherited.Super.class),
                        superTypeName, false
                );
                addInheritedParams(
                        inheritedParamsFieldInitializer,
                        ClassName.get(superclass.getType()),
                        superTypeName, true
                );
            }

            var interfacesParams = inherited != null ? inherited.interfaces() : null;
            if (interfacesParams != null && interfaces != null && !interfaces.isEmpty() && interfacesParams.enumerate()) {
                inheritParamsOf = Meta.Params.Inherited.Interfaces.METHOD_NAME.equals(interfacesParams.methodName()) && paramsEnum == FULL;
                for (int i = 0; i < interfaces.size(); i++) {
                    var iface = interfaces.get(i);
                    var ifaceParametersEnumName = getUniqueName(
                            iface.getClassName() + interfacesParams.classNameSuffix(), uniqueNames
                    );
                    builder.addType(newParamsType(ifaceParametersEnumName, iface.getTypeParameters(), paramsEnum));
                    addInheritedParams(
                            inheritedParamsFieldInitializer,
                            ClassName.get(iface.getType()),
                            ifaceParametersEnumName,
                            addSuperParams || i > 0);
                }
                inheritedParamsField.initializer(inheritedParamsFieldInitializer.add("\n)").build());
                if (paramsEnum == FULL) {
                    addInheritParamsOf = true;
                    builder.addField(inheritedParamsField.build());
                    builder.addMethod(returnListMethodBuilder(
                            interfacesParams.methodName(),
                            subtypeOf(wildcardParametrized(ClassName.get(Typed.class), 1)),
                            CodeBlock.builder().addStatement("return $L.get(type)", inheritedParamsFieldName).build(), inheritParamsOf
                    ).addParameter(ParameterSpec.builder(Class.class, "type").build()).build());
                }
            }

            if (addParamsType) {
                builder.addMethod(
                        callValuesMethod(methodName,
                                ClassName.get("", typeName),
                                wildcardParametrized(ClassName.get("", typeName), 1),
                                inheritParams)
                );
            }
        }

        var inheritProps = false;
        if (propsEnum != NONE) {
            var propsLevelUniqueNames = new HashSet<String>();
            var propsInfo = props.get();
            inheritProps = Meta.Props.METHOD_NAME.equals(propsInfo.methodName()) && propsEnum == FULL;
            var typeName = ClassName.get("", getUniqueName(propsInfo.className(), uniqueNames));
            var typeVariable = TypeVariableName.get("T");
            var propsClassBuilder = propsEnum == FULL
                    ? typeAwareClass(typeName, typeVariable)
                    : classBuilder(typeName).addModifiers(PUBLIC, STATIC);

            var propertyPerName = new LinkedHashMap<String, Property>();
            var propertyWeights = new LinkedHashMap<String, AtomicInteger>();

            for (var property : bean.getPublicProperties()) {
                if (!property.isExcluded()) {
                    var propertyName = property.getName();
                    if (propertyPerName.put(propertyName, property) != null) {
                        throw new IllegalStateException("property already handled, " + propertyName);
                    }
                    propertyWeights.put(propertyName, new AtomicInteger(1));
                }
            }

            var sc = superclass;
            var superClassPropsWeight = 2;
            while (sc != null) {
                addProperties(sc, propertyPerName, propertyWeights, superClassPropsWeight);
                sc = sc.getSuperclass();
                superClassPropsWeight++;
            }

            addInterfaceProps(interfaces, propertyPerName, propertyWeights, 1);

            var allReadable = true;
            var allWritable = true;
            for (var property : propertyPerName.values()) {
                allReadable &= isReadable(property);
                allWritable &= isWriteable(property);
            }

            var getterName = "getter";
            var setterName = "setter";
            var getterType = getFunctionType(beanType, typeVariable);
            var setterType = getBiConsumerType(beanType, typeVariable);
            var nameArgType = ClassName.get(String.class);
            var typeArgType = typeClassOf(typeVariable);

            var readTypeName = !allReadable ? ClassName.get("", getUniqueName("Read", propsLevelUniqueNames)) : typeName;
            var writeTypeName = !allWritable ? ClassName.get("", getUniqueName("Write", propsLevelUniqueNames)) : typeName;
            var readWriteTypeName = !allReadable || !allWritable
                    ? ClassName.get("", getUniqueName("ReadWrite", propsLevelUniqueNames))
                    : typeName;

            var rwUsed = false;
            var readUsed = false;
            var writeUsed = false;

            var orderedProperties = weightOrdered(propertyWeights);

            for (var propertyName : orderedProperties) {
                var property = requireNonNull(propertyPerName.get(propertyName), propertyName + " is null");
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

//                var propertyBean = property.getBean();
//                var propAnnotations = property.getAnnotations();
//                if (propAnnotations != null || propertyBean != null) {
//                    var propertyName = property.getName();
//                    var chars = propertyName.toCharArray();
//                    chars[0] = Character.toUpperCase(chars[0]);
//                    var propTypeName = getUniqueName(new String(chars), propsLevelUniqueNames);
//                    var propType = ClassName.get("", propTypeName);
//
//                    var constructor = full ? rwInheritConstructor(nameArgType, typeArgType, getterType, getterName, setterType, setterName, CodeBlock.builder()
//                            .addStatement("super($L, $L, $L, $L)", "name", "type", getterName, setterName))
//                            : read ? inheritorConstructor(nameArgType, typeArgType, getterType, getterName, CodeBlock.builder()
//                            .addStatement("super($L, $L, $L)", "name", "type", getterName))
//                            : writable ? inheritorConstructor(nameArgType, typeArgType, setterType, setterName, CodeBlock.builder()
//                            .addStatement("super($L, $L, $L)", "name", "type", setterName))
//                            : null;
//
//                    var subType = classBuilder(propType)
//                            .addModifiers(STATIC, FINAL, PUBLIC)
//                            .superclass(ParameterizedTypeName.get(typ, typeVariable))
//                            .addTypeVariable(typeVariable);
//
//                    if (constructor != null) {
//                        subType.addMethod(constructor.build());
//                    }
////                    if (propAnnotations != null) {
////                        for (var propAnnotation : propAnnotations) {
////                            if (propAnnotation.getAnnotationType().asElement() instanceof TypeElement te) {
////                                var retention = te.getAnnotation(Retention.class);
////                                if (retention.value() != SOURCE) {
//////                                    var propAnnotationType = AnnotationSpec.builder(ClassName.get(te));
//////                                    var values = propAnnotation.getElementValues();
//////                                    values.forEach((annotationName, annotationValue) -> {
//////                                        var string = annotationName.getSimpleName().toString();
//////                                        propAnnotationType.addMember(string, CodeBlock.of(annotationValue.toString()));
//////                                    });
//////                                    subType.addAnnotation(propAnnotationType.build());
////                                }
////                            }
////                        }
////                    }
//                    propsClassBuilder.addType(subType.build());
//                    typ = propType;
//                }

                var paramTypeName = TypeName.get(property.getEvaluatedType());
                var fieldSpec = switch (propsEnum) {
                    case FULL -> newPropertyConstant(typ, property.getName(),
                            paramTypeName, property.getField(), property.isPublicField(), property.getGetter(),
                            property.getSetter(), property.getRecordComponent());
                    case NAME ->
                            staticField(property.getName(), ClassName.get(String.class)).initializer("\"$L\"", property.getName());
                    case TYPE ->
                            staticField(property.getName(), typeClassOf(paramTypeName)).initializer(dotClass(paramTypeName));
                    default -> null;
                };
                if (fieldSpec != null) {
                    propsClassBuilder.addField(fieldSpec.build());
                }
            }

            if (propsEnum == FULL) {
                var readWriteInterface = ParameterizedTypeName.get(ClassName.get(ReadWrite.class), beanType, typeVariable);
                var writeInterface = writeInterface(beanType, typeVariable);
                var readInterface = ParameterizedTypeName.get(ClassName.get(Read.class), beanType, typeVariable);

                if (rwUsed && !readWriteTypeName.equals(typeName)) {
                    var extendsReadType = readTypeName.equals(typeName);
                    var initialize = extendsReadType
                            ? CodeBlock.builder()
                            .addStatement("super($L, $L, $L)", "name", "type", getterName)
                            .addStatement("this." + setterName + " = " + setterName)
                            : CodeBlock.builder()
                            .addStatement("super($L, $L)", "name", "type")
                            .addStatement("this." + getterName + " = " + getterName)
                            .addStatement("this." + setterName + " = " + setterName);
                    var constructor = rwInheritConstructor(nameArgType, typeArgType,
                            getterType, getterName, setterType, setterName, initialize);

                    var subType = classBuilder(readWriteTypeName)
                            .addModifiers(STATIC, PUBLIC)
                            .superclass(ParameterizedTypeName.get(typeName, typeVariable))
                            .addTypeVariable(typeVariable)
                            .addSuperinterface(readWriteInterface)
                            .addMethod(constructor.build());

                    if (!extendsReadType) {
                        addGetter(subType, beanType, typeVariable, getterType, getterName);
                    }
                    addSetter(subType, beanType, typeVariable, setterType, setterName, "set", "bean", true);

                    propsClassBuilder.addType(subType.build());
                }

                if (readUsed && !readTypeName.equals(typeName)) {
                    var constructor = inheritorConstructor(nameArgType, typeArgType, getterType, getterName, CodeBlock.builder()
                            .addStatement("super($L, $L)", "name", "type")
                            .addStatement("this." + getterName + " = " + getterName));

                    var subType = classBuilder(readTypeName)
                            .addModifiers(STATIC, PUBLIC)
                            .superclass(ParameterizedTypeName.get(typeName, typeVariable))
                            .addTypeVariable(typeVariable)
                            .addSuperinterface(readInterface)
                            .addMethod(constructor.build());

                    addGetter(subType, beanType, typeVariable, getterType, getterName);

                    propsClassBuilder.addType(subType.build());
                }

                if (writeUsed && !writeTypeName.equals(typeName)) {
                    var constructor = inheritorConstructor(nameArgType, typeArgType, setterType, setterName, CodeBlock.builder()
                            .addStatement("super($L, $L)", "name", "type")
                            .addStatement("this." + setterName + " = " + setterName));

                    var subType = classBuilder(writeTypeName)
                            .addModifiers(STATIC, PUBLIC)
                            .superclass(ParameterizedTypeName.get(typeName, typeVariable))
                            .addTypeVariable(typeVariable)
                            .addSuperinterface(writeInterface)
                            .addMethod(constructor.build());

                    addSetter(subType, beanType, typeVariable, setterType, setterName, "set", "bean", true);

                    propsClassBuilder.addType(subType.build());
                }

                uniqueNames.addAll(propertyPerName.keySet());

                var nameFieldName = getUniqueName("name", uniqueNames);
                var typeFieldName = getUniqueName("type", uniqueNames);

                populateTypeAwareClass(propsClassBuilder, nameFieldName, typeFieldName, nameArgType, typeArgType);

                var constructor = constructorBuilder();
                var constructorBody = CodeBlock.builder();

                populateConstructor(constructor, constructorBody, nameArgType, nameFieldName, typeArgType, typeFieldName);

                if (allReadable) {
                    var getterFieldName = getUniqueName("getter", uniqueNames);
                    var getterFunction = getFunctionType(beanType, typeVariable);

                    addGetter(propsClassBuilder, beanType, typeVariable, getterFunction, getterFieldName);

                    constructor.addParameter(getterFunction, "getter");
                    constructorBody.addStatement("this." + getterFieldName + " = " + "getter");
                }

                if (allWritable) {
                    var setterFieldName = getUniqueName("setter", uniqueNames);
                    var setterConsumer = getBiConsumerType(beanType, typeVariable);

                    addSetter(propsClassBuilder, beanType, typeVariable, setterConsumer, setterFieldName, "set", "bean", true);

                    constructor.addParameter(setterConsumer, "setter");
                    constructorBody.addStatement("this." + setterFieldName + " = " + "setter");
                }

                if (allReadable && allWritable) {
                    propsClassBuilder.addSuperinterface(readWriteInterface);
                } else if (allReadable) {
                    propsClassBuilder.addSuperinterface(readInterface);
                } else if (allWritable) {
                    propsClassBuilder.addSuperinterface(writeInterface);
                }

                propsClassBuilder.addMethod(constructor.addCode(constructorBody.build()).build());
                propsClassBuilder = addValues(propsClassBuilder, typeName, propertyPerName.keySet(), 1, uniqueNames);
            } else {
                if (!propertyPerName.isEmpty()) {
                    propsClassBuilder = addValues(propsClassBuilder, ClassName.get(String.class), propertyPerName.keySet(), uniqueNames);
                }
            }
            builder.addType(propsClassBuilder.build());
            if (propsEnum == FULL) {
                builder.addMethod(callValuesMethod(
                        propsInfo.methodName(),
                        typeName,
                        wildcardParametrized(typeName, 1),
                        inheritProps));
            }
        }

//        var inheritMethods = false;
        if (methodsEnum != Meta.Methods.EnumType.NONE) {
            var methodsLevelUniqueNames = new HashSet<String>();
            var methodsInfo = metaMethods.get();
//            inheritMethods = Meta.Methods.METHOD_NAME.equals(methodsInfo.methodName()) && propsEnum == FULL;

            var typeName = ClassName.get("", getUniqueName(methodsInfo.className(), uniqueNames));
            var methodsClassBuilder = classBuilder(typeName).addModifiers(PUBLIC, STATIC);

            var methods = new LinkedHashSet<String>();
            var methodWeights = new LinkedHashMap<String, AtomicInteger>();

            for (var method : bean.getPublicMethods()) {
                var methodName = getMethodName(method);
                var added = methods.add(methodName);
                if (!added) {
                    throw new IllegalStateException("method already handled, " + method);
                }
            }

            var sc = superclass;
            var superClassMethodWeight = 2;
            while (sc != null) {
                addMethods(sc, methods, methodWeights, superClassMethodWeight);
                sc = sc.getSuperclass();
                superClassMethodWeight++;
            }

            addInterfaceMethods(interfaces, methods, methodWeights, 1);

            var orderedMethods = weightOrdered(methodWeights);
            methodsLevelUniqueNames.addAll(orderedMethods);

            for (var methodName : orderedMethods) {
                var fieldSpec = switch (methodsEnum) {
                    case NAME -> staticField(methodName, ClassName.get(String.class)).initializer("\"$L\"", methodName);
                    default -> null;
                };
                if (fieldSpec != null) {
                    methodsClassBuilder.addField(fieldSpec.build());
                }
            }

            addValues(methodsClassBuilder, ClassName.get(String.class), orderedMethods, methodsLevelUniqueNames);
            builder.addType(methodsClassBuilder.build());
        }

        var inheritMetamodel = inheritParams && inheritParamsOf && inheritProps;
        if (inheritMetamodel) {
            typeGetter.addAnnotation(Override.class);
        }

        if (paramsEnum == FULL && propsEnum == FULL) {
            builder.addField(FieldSpec.builder(
                    ClassName.get("", name), "instance", PUBLIC, STATIC, FINAL
            ).initializer(CodeBlock.of("new $L()", name)).build());

            builder.addField(typeField);
            builder.addMethod(typeGetter.build());
        } else {
            builder.addField(typeField);
        }

        if (inheritMetamodel) {
            builder.addSuperinterface(ParameterizedTypeName.get(
                    ClassName.get(MetaModel.class), beanType
            ));
        } else {
            if ((inheritParams && addParamsType) || (inheritParamsOf && addInheritParamsOf)) {
                builder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(ParametersAware.class), beanType));
            }
            if (inheritProps) {
                builder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(PropertiesAware.class), beanType));
            }
        }
        if (inheritSuperParams) {
            builder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(SuperParametersAware.class), beanType));
        }

        var srcModifiers = ofNullable(bean.getType().getModifiers()).orElse(Set.of());
        var accessLevel = srcModifiers.contains(PRIVATE) ? PRIVATE
                : srcModifiers.contains(PROTECTED) ? PROTECTED
                : srcModifiers.contains(PUBLIC) ? PUBLIC : null;
        if (accessLevel != null) {
            builder.addModifiers(accessLevel);
        }

        var beanBuilderInfo = bean.getBeanBuilderInfo();
        if (ofNullable(bean.getMeta())
                .map(Meta::builder)
                .map(Meta.Builder::generateMeta)
                .orElse(false) && beanBuilderInfo != null) {
            builder.addType(newBuilderType(beanBuilderInfo));
        }

        for (var generator : customizers) {
            generator.customize(messager, bean, builder);
        }

        return builder;
    }

    private static void addProperties(MetaBean bean, Map<String, Property> propertyPerName,
                                      Map<String, AtomicInteger> usedWeights, int weight) {
        var properties = bean.getPublicProperties();
        for (var property : properties) {
            addInheritedProp(property, propertyPerName, usedWeights, weight);
        }
        addInterfaceProps(bean.getInterfaces(), propertyPerName, usedWeights, weight);
    }

    private static void addInterfaceMethods(List<MetaBean> interfaces, Set<String> methods,
                                            Map<String, AtomicInteger> methodWeights, int weight) {
        if (interfaces == null) {
            return;
        }
        for (var iface : interfaces) {
            addMethods(iface, methods, methodWeights, weight);
        }
    }

    private static void addMethods(MetaBean bean, Set<String> methods,
                                   Map<String, AtomicInteger> methodWeights, int weight) {
        for (var method : bean.getPublicMethods()) {
            var methodName = getMethodName(method);
            methodWeights.computeIfAbsent(methodName, k -> new AtomicInteger(weight)).incrementAndGet();
            methods.add(methodName);
        }
        addInterfaceMethods(bean.getInterfaces(), methods, methodWeights, weight);
    }

    private static List<String> weightOrdered(Map<String, AtomicInteger> weightValues) {
        var ordered = new ArrayList<>(weightValues.keySet());
        ordered.sort(new WeightComparator(weightValues));
        return ordered;
    }

    private static void addInterfaceProps(List<MetaBean> interfaces, Map<String, Property> propertyPerName,
                                          Map<String, AtomicInteger> usedWeights, int weight) {
        if (interfaces == null) {
            return;
        }
        for (var iface : interfaces) {
            List<Property> properties = iface.getPublicProperties();
            for (var property : properties) {
                addInheritedProp(property, propertyPerName, usedWeights, weight);
            }
            addInterfaceProps(iface.getInterfaces(), propertyPerName, usedWeights, weight);
        }
    }

    private static void addInheritedProp(Property property, Map<String, Property> propertyPerName,
                                         Map<String, AtomicInteger> usedWeights, int weight) {
        if (!property.isExcluded()) {
            var propertyName = property.getName();
            if (!propertyPerName.containsKey(propertyName)) {
                propertyPerName.put(propertyName, property);
            }
            usedWeights.computeIfAbsent(propertyName, k -> new AtomicInteger(weight)).incrementAndGet();
        }
    }

    private static MethodSpec.Builder baseConstructor(ClassName nameArgType, ParameterizedTypeName typeArgType) {
        return constructorBuilder()
                .addModifiers(PRIVATE)
                .addParameter(nameArgType, "name")
                .addParameter(typeArgType, "type");
    }

    private static MethodSpec.Builder inheritorConstructor(ClassName nameArgType, ParameterizedTypeName typeArgType,
                                                           ParameterizedTypeName fieldParam, String fieldName,
                                                           CodeBlock.Builder initialize) {
        return baseConstructor(nameArgType, typeArgType)
                .addParameter(fieldParam, fieldName)
                .addCode(initialize.build());
    }

    private static MethodSpec.Builder rwInheritConstructor(ClassName nameArgType, ParameterizedTypeName typeArgType,
                                                           ParameterizedTypeName getterType, String getterName,
                                                           ParameterizedTypeName setterType, String setterName,
                                                           CodeBlock.Builder initialize) {
        return baseConstructor(nameArgType, typeArgType)
                .addParameter(getterType, getterName)
                .addParameter(setterType, setterName)
                .addCode(initialize.build());
    }

    public static ParameterizedTypeName typeClassOf(TypeName typeName) {
        return ParameterizedTypeName.get(ClassName.get(Class.class), typeName);
    }

    public static CodeBlock newInstanceCall(TypeName className, TypeName typeVarName, CodeBlock args) {
        return CodeBlock.builder().add("new $T<$T>($L)", className, typeVarName, args).build();
    }

    public static CodeBlock newInstanceCall(TypeName className, TypeName typeVarName, TypeName builderVarName, CodeBlock args) {
        return CodeBlock.builder().add("new $T<$T, $T>($L)", className, typeVarName, builderVarName, args).build();
    }

    public static CodeBlock.Builder enumConstructorArgs(
            String name, CodeBlock type, CodeBlock getter, CodeBlock setter
    ) {
        var builder = enumConstructorArgs(name, type).toBuilder();
        if (getter != null) {
            builder.add(", ").add(getter);
        }
        if (setter != null) {
            builder.add(", ").add(setter);
        }
        return builder;
    }

    public static CodeBlock enumConstructorArgs(String name, CodeBlock type) {
        return CodeBlock.builder().add("\"$L\", $L", name, type).build();
    }

    public static CodeBlock dotClass(TypeName type) {
        if (type instanceof ParameterizedTypeName pt) {
            type = pt.rawType;
            return CodeBlock.of("(Class)$T.class", type);
        }
        return CodeBlock.of("$T.class", type);
    }

    private static TypeSpec newParamsType(String typeName, List<Param> beanTypeParameters, EnumType enumType) {
        var className = ClassName.get("", typeName);
        var typeVariable = TypeVariableName.get("T");
        var typesBuilder = enumType == FULL ? typeAwareClass(className, typeVariable) : classBuilder(typeName).addModifiers(PUBLIC, STATIC);
        var paramNames = new LinkedHashSet<String>();
        for (var param : beanTypeParameters) {
            var name = param.getName().getSimpleName().toString();
            paramNames.add(name);
            var type = TypeName.get(param.getEvaluatedType());
            var unboxedTypeVarName = unboxedTypeVarName(type);

            var fieldSpec = switch (enumType) {
                case FULL -> staticField(name, ParameterizedTypeName.get(className, unboxedTypeVarName)).initializer(
                        newInstanceCall(className, unboxedTypeVarName, enumConstructorArgs(name, dotClass(type)))
                );
                case NAME -> staticField(name, ClassName.get(String.class)).initializer("\"$L\"", name);
                case TYPE -> staticField(name, typeClassOf(type)).initializer(dotClass(type));
                default -> null;
            };

            if (fieldSpec != null) {
                typesBuilder.addField(fieldSpec.build());
            }
        }

        var uniqueNames = new HashSet<>(paramNames);
        if (enumType == FULL) {
            var nameFieldName = getUniqueName("name", uniqueNames);
            var typeFieldName = getUniqueName("type", uniqueNames);
            var nameArgType = ClassName.get(String.class);
            var typeArgType = typeClassOf(typeVariable);
            populateTypeAwareClass(typesBuilder, nameFieldName, typeFieldName, nameArgType, typeArgType);

            var constructor = constructorBuilder();
            var constructorBody = CodeBlock.builder();

            populateConstructor(constructor, constructorBody, nameArgType, nameFieldName, typeArgType, typeFieldName);
            typesBuilder.addMethod(constructor.addCode(constructorBody.build()).build());

            addValues(typesBuilder, className, paramNames, 1, uniqueNames);
        } else if (enumType == NAME) {
            if (!paramNames.isEmpty()) {
                addValues(typesBuilder, ClassName.get(String.class), paramNames, uniqueNames);
            }
        }

        return typesBuilder.build();
    }

    public static FieldSpec.Builder staticField(String name, TypeName type) {
        return FieldSpec.builder(type, name, PUBLIC, FINAL, STATIC);
    }

    public static TypeSpec.Builder populateTypeAwareClass(TypeSpec.Builder builder,
                                                          String nameFieldName, String typeFieldName,
                                                          ClassName nameArgType, ParameterizedTypeName typeArgType) {
        addFieldWithReadAccessor(builder, nameFieldName, nameArgType, "name", true);
        addFieldWithReadAccessor(builder, typeFieldName, typeArgType, "type", true);
        return builder;
    }

    public static void addFieldWithReadAccessor(
            TypeSpec.Builder builder, String fieldName, TypeName type, String accessorName, boolean override
    ) {
        var accessor = methodBuilder(accessorName);
        if (override) {
            accessor.addAnnotation(Override.class);
        }
        accessor
                .addModifiers(PUBLIC)
                .returns(type)
                .addStatement("return this." + fieldName);
        builder
                .addField(FieldSpec.builder(type, fieldName).addModifiers(PUBLIC, FINAL).build())
                .addMethod(accessor.build());
    }

    public static void addGetter(
            TypeSpec.Builder builder, ClassName beanType, TypeVariableName propertyTypeVar,
            ParameterizedTypeName getterType, String getterFieldName
    ) {
        builder.addField(FieldSpec.builder(getterType, getterFieldName).addModifiers(PUBLIC, FINAL).build());
        builder.addMethod(
                methodBuilder("get")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .addParameter(beanType, "bean")
                        .returns(propertyTypeVar)
                        .addStatement("return this." + getterFieldName + ".apply(bean)")
                        .build()
        );
    }

    public static void addSetter(
            TypeSpec.Builder builder, TypeName beanType, TypeVariableName propertyTypeVar,
            ParameterizedTypeName setterType, String fieldName, String methodName, String argName, boolean overr
    ) {
        builder.addField(FieldSpec.builder(setterType, fieldName).addModifiers(PUBLIC, FINAL).build());
        var method = methodBuilder(methodName)
                .addModifiers(PUBLIC)
                .addParameter(beanType, argName)
                .addParameter(propertyTypeVar, "value")
                .addStatement(fieldName + ".accept(" + argName + ", value)");
        if (overr) {
            method.addAnnotation(Override.class);
        }
        builder.addMethod(method.build());
    }

    public static ParameterizedTypeName getFunctionType(ClassName beanType, TypeVariableName propertyTypeVar) {
        return ParameterizedTypeName.get(ClassName.get(Function.class), beanType, propertyTypeVar);
    }

    public static ParameterizedTypeName getBiConsumerType(TypeName beanType, TypeVariableName propertyTypeVar) {
        return ParameterizedTypeName.get(ClassName.get(BiConsumer.class), beanType, propertyTypeVar);
    }

    public static TypeSpec.Builder typeAwareClass(ClassName className, TypeVariableName typeVariable) {
        return classBuilder(className)
                .addTypeVariable(typeVariable)
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Typed.class), typeVariable))
                .addModifiers(PUBLIC, STATIC);
    }

    public static void populateConstructor(MethodSpec.Builder constructor, CodeBlock.Builder constructorBody,
                                           ClassName nameType, String nameName, TypeName typeType, String typeName) {
        constructor
                .addModifiers(PRIVATE)
                .addParameter(nameType, "name")
                .addParameter(typeType, "type");

        constructorBody
                .addStatement("this." + nameName + " = " + "name")
                .addStatement("this." + typeName + " = " + "type");
    }

    static MethodSpec callValuesMethod(String name, ClassName typeClassName, TypeName returnType, boolean overr) {
        return returnListMethodBuilder(name, returnType,
                CodeBlock.builder().addStatement("return $T.values()", typeClassName).build(), overr
        ).build();
    }

    static MethodSpec.Builder returnListMethodBuilder(String name, TypeName typeClassName, CodeBlock code, boolean overr) {
        var builder = methodBuilder(name)
                .addModifiers(PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(List.class), typeClassName))
                .addCode(code);
        if (overr) {
            builder.addAnnotation(Override.class);
        }
        return builder;
    }

    private static ParameterizedTypeName writeInterface(TypeName beanType, TypeVariableName typeVariable) {
        return ParameterizedTypeName.get(ClassName.get(Write.class), beanType, typeVariable);
    }

    private static TypeSpec newBuilderType(MetaBean.BeanBuilder builderInfo) {
        var beanType = wildcardParametrized(builderInfo.getType());

        var metaClassName = builderInfo.getMetaClassName();
        var className = ClassName.get("", metaClassName);
        var typeVariable = TypeVariableName.get("T");
        var builder = typeAwareClass(className, typeVariable).addModifiers(FINAL);

        var setterNames = new ArrayList<String>();
        for (var setter : builderInfo.getSetters()) {
            var setterName = setter.getName();
            builder.addField(newPropertyConstant(
                    className, setterName,
                    TypeName.get(setter.getEvaluatedType()), null, false, null,
                    setter.getSetter(), null).build());
            setterNames.add(setterName);
        }
        var uniqueNames = new HashSet<>(setterNames);

        var nameFieldName = getUniqueName("name", uniqueNames);
        var typeFieldName = getUniqueName("type", uniqueNames);
        var setterFieldName = getUniqueName("setter", uniqueNames);
        var setterArgName = "setter";

        var nameArgType = ClassName.get(String.class);
        var typeArgType = typeClassOf(typeVariable);
        var setterType = getBiConsumerType(beanType, typeVariable);

        populateTypeAwareClass(builder, nameFieldName, typeFieldName, nameArgType, typeArgType);

        var constructor = constructorBuilder();
        var constructorBody = CodeBlock.builder();
        populateConstructor(constructor, constructorBody, nameArgType, nameFieldName, typeArgType, typeFieldName);

        constructor.addParameter(setterType, setterArgName);
        constructorBody.addStatement("this." + setterFieldName + " = " + setterArgName);

        constructor.addCode(constructorBody.build());

        builder.addMethod(constructor.build());

        builder.addField(builder(setterType, setterFieldName).addModifiers(PUBLIC, FINAL).build());

        builder.addMethod(
                methodBuilder("set")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .addParameter(beanType, "builder")
                        .addParameter(typeVariable, "value")
                        .addStatement(setterFieldName + ".accept(builder, value)")
                        .build()
        );

        addValues(builder, className, setterNames, 1, uniqueNames);

        builder.addSuperinterface(writeInterface(beanType, typeVariable));
        return builder.build();
    }

    public static TypeSpec.Builder addValues(TypeSpec.Builder builder, ClassName typeName,
                                             Collection<String> values, int wildcardsAmount,
                                             Set<String> uniqueNames) {
        return addValues(builder, wildcardParametrized(typeName, wildcardsAmount), values, uniqueNames);
    }

    public static TypeSpec.Builder addValues(TypeSpec.Builder builder, TypeName typeName,
                                             Collection<String> values, Set<String> uniqueNames) {
        var valuesField = getUniqueName("values", uniqueNames);
        return builder
                .addField(
                        listField(valuesField, typeName, CodeBlock.builder()
                                .add(values.stream().reduce((l, r) -> l + (!l.isEmpty() ? ", " : "") + r).orElse(""))
                                .build(), PRIVATE, FINAL, STATIC)
                )
                .addMethod(
                        MethodSpec.methodBuilder("values")
                                .addModifiers(PUBLIC, FINAL, STATIC)
                                .returns(
                                        ParameterizedTypeName.get(
                                                ClassName.get(List.class),
                                                typeName
                                        )
                                )
                                .addStatement("return " + valuesField).build()
                );
    }

    public static TypeName wildcardParametrized(TypeElement type) {
        return wildcardParametrized(ClassName.get(type), type.getTypeParameters().size());
    }

    public static TypeName wildcardParametrized(ClassName typeName, int amount) {
        return amount <= 0 ? typeName : ParameterizedTypeName.get(typeName, range(0, amount)
                .mapToObj(i -> subtypeOf(OBJECT))
                .toArray(WildcardTypeName[]::new));
    }

    static void addInheritedParams(
            CodeBlock.Builder mapInitializer, ClassName mapKey, String paramsEnumName, boolean addComma
    ) {
        var mapValue = CodeBlock.builder().add("$L.values()", paramsEnumName).build().toString();
        addMapEntry(mapInitializer, mapKey, mapValue, addComma);
    }

    static void addMapEntry(
            CodeBlock.Builder mapInitializer, ClassName mapKey, String mapValue, boolean addComma
    ) {
        mapInitializer.indent();
        if (addComma) {
            mapInitializer.add(",\n");
        }
        mapInitializer.add(mapEntry(dotClass(mapKey), mapValue)).unindent();
    }

    public static CodeBlock mapEntry(CodeBlock mapKey, String mapValue) {
        return CodeBlock.builder().add("$T.entry($L, $L)", Map.class, mapKey, mapValue).build();
    }

    public static String getUniqueName(String name, Collection<String> uniqueNames) {
        while (uniqueNames.contains(name)) {
            name = "_" + name;
        }
        uniqueNames.add(name);
        return name;
    }

    public static boolean isReadable(Property property) {
        var getter = property.getGetter();
        var recordComponent = property.getRecordComponent();
        return getter != null || recordComponent != null || isPublicField(property);
    }

    public static boolean isWriteable(Property property) {
        var setter = property.getSetter();
        return (setter != null || isPublicField(property)) && property.getRecordComponent() == null;
    }

    private static boolean isPublicField(Property property) {
        return property.getField() != null && property.isPublicField();
    }

    private static FieldSpec.Builder newPropertyConstant(ClassName constType, String constName, TypeName paramName,
                                                         VariableElement field, boolean isPublicField,
                                                         ExecutableElement getter, ExecutableElement setter,
                                                         RecordComponentElement record) {
        var uniqueNames = new HashSet<String>();
        var beanParamName = getUniqueName("bean", uniqueNames);
        var valueParamName = getUniqueName("value", uniqueNames);

        var getterArgCode = getGetterCallCode(beanParamName, isPublicField, field, record, getter);
        var setterArgCode = getSetterCallCode(beanParamName, valueParamName, isPublicField, field, record, setter);

        var unboxedTypeVarName = unboxedTypeVarName(paramName);
        return staticField(constName, ParameterizedTypeName.get(constType, unboxedTypeVarName)).initializer(
                newInstanceCall(constType, unboxedTypeVarName, enumConstructorArgs(
                        constName, dotClass(paramName), getterArgCode, setterArgCode
                ).build())
        );
    }

    public static CodeBlock getSetterCallCode(String beanParamName, String valueParamName, boolean isPublicField,
                                              VariableElement field, RecordComponentElement record,
                                              ExecutableElement setter) {
        var setterName = ofNullable(setter).map(ExecutableElement::getSimpleName).orElse(null);
        if (setterName != null) {
            return CodeBlock.builder().addNamed("($bean:L, $val:L) -> $bean:L.$setter:L($val:L)",
                    Map.of("bean", beanParamName, "val", valueParamName, "setter", setterName)
            ).build();
        } else if (record == null && (field != null && isPublicField)) {
            return CodeBlock.builder().addNamed("($bean:L, $val:L) -> $bean:L.$field:L = $val:L",
                    Map.of("bean", beanParamName, "val", valueParamName, "field", field.getSimpleName())
            ).build();
        }
        return null;
    }

    public static CodeBlock getGetterCallCode(
            String paramName, boolean isPublicField, VariableElement field,
            RecordComponentElement record, ExecutableElement getter
    ) {
        var callName = getCallName(isPublicField, field, record, getter);
        return callName != null ? CodeBlock.builder().addNamed("$param:L -> $param:L.$call:L", Map.of(
                "param", paramName, "call", callName)).build() : null;
    }

    public static CodeBlock getGetterCallInitByNewCode(
            String varName, String paramName,
            boolean isPublicField, VariableElement field,
            ExecutableElement getter, ExecutableElement setter,
            Collection<String> uniqueNames
    ) {
        if (setter == null && !(isPublicField && field != null)) {
            throw new IllegalArgumentException("no setter of public field");
        }
        var callName = getCallName(isPublicField, field, null, getter);
        var tempVar = getUniqueName(varName, uniqueNames);
        var callType = getCallType(isPublicField, field, null, getter);

        var newInstance = requireNonNull(getNewInstanceCode(callType),
                () -> "cannot determine object instantiate code for '" + callType + "'");

        var builder = CodeBlock.builder()
                .beginControlFlow("$L -> ", paramName)
                .addStatement("var $L = $L.$L", tempVar, paramName, callName)
                .beginControlFlow("if ($L == null)", tempVar)
                .addStatement("$L = $L", tempVar, newInstance);

        if (isPublicField && field != null) {
            builder.addStatement("$L.$L = $L", paramName, callName, tempVar);
        } else {
            builder.addStatement("$L.$L($L)", paramName, setter.getSimpleName().toString(), tempVar);
        }
        return builder
                .endControlFlow()
                .addStatement("return $L", tempVar)
                .endControlFlow()
                .build();
    }

    private static CodeBlock getNewInstanceCode(TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType dt) {
            return CodeBlock.of("new $T()", TypeName.get(typeMirror));
        }
        if (typeMirror instanceof ArrayType at) {
            return CodeBlock.of("new $T[0]", TypeName.get(typeMirror));
        }
        return null;
    }

    private static String getCallName(boolean isPublicField, VariableElement field,
                                      RecordComponentElement record, ExecutableElement getter) {
        return getter != null ? getter.getSimpleName() + "()"
                : record != null ? record.getAccessor().getSimpleName() + "()"
                : field != null && isPublicField ? field.getSimpleName().toString() : null;
    }

    private static TypeMirror getCallType(
            boolean isPublicField, VariableElement field, RecordComponentElement record, ExecutableElement getter
    ) {
        return getter != null ? getter.getReturnType()
                : record != null ? record.getAccessor().getReturnType()
                : field != null && isPublicField ? field.asType() : null;
    }

    public static TypeName unboxedTypeVarName(TypeName type) {
        return type.isPrimitive() ? type.box() : type;
    }

    static FieldSpec listField(String name, TypeName type, CodeBlock init, Modifier... modifiers) {
        return FieldSpec.builder(ParameterizedTypeName.get(ClassName.get(List.class), type), name, modifiers)
                .initializer(CodeBlock.builder().add("$T.of($L)", List.class, init).build())
                .build();
    }

    static FieldSpec mapField(String name, ClassName key, ClassName value, CodeBlock init, Modifier... modifiers) {
        return FieldSpec.builder(
                ParameterizedTypeName.get(ClassName.get(Map.class), key, value), name, modifiers
        ).initializer(init).build();
    }

    public static CodeBlock.Builder initMapByEntries(List<String> entries) {
        var init = CodeBlock.builder().add("$T.ofEntries(\n", Map.class);
        for (var i = 0; i < entries.size(); i++) {
            var mapPart = entries.get(i);
            if (i > 0) {
                init.add(",\n");
            }
            init.add(CodeBlock.builder().indent().add(mapPart).unindent().build());
        }
        init.add("\n)");
        return init;
    }

    static AnnotationSpec generatedAnnotation() {
        return AnnotationSpec.builder(Generated.class).addMember("value", "\"$L\"", ClassName.get(Meta.class).canonicalName()).build();
    }

    private static class WeightComparator implements Comparator<String> {
        private final AtomicInteger defaultValue;
        private final Map<String, AtomicInteger> weights;

        public WeightComparator(Map<String, AtomicInteger> weights) {
            this.weights = weights;
            defaultValue = new AtomicInteger(1);
        }

        @Override
        public int compare(String prop1, String prop2) {
            var w1 = weights.getOrDefault(prop1, defaultValue);
            var w2 = weights.getOrDefault(prop2, defaultValue);
            return -Integer.compare(w1.get(), w2.get());
        }
    }
}
