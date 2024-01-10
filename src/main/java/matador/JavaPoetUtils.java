package matador;

import io.jbock.javapoet.*;

import javax.annotation.processing.Messager;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static io.jbock.javapoet.FieldSpec.builder;
import static io.jbock.javapoet.MethodSpec.constructorBuilder;
import static io.jbock.javapoet.MethodSpec.methodBuilder;
import static io.jbock.javapoet.TypeSpec.classBuilder;
import static java.util.Optional.ofNullable;
import static javax.lang.model.element.Modifier.*;

public class JavaPoetUtils {
    public static TypeSpec.Builder newTypeBuilder(Messager messager, MetaBean bean, Collection<? extends MetaCustomizer> customizers) {
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
                .initializer(CodeBlock.builder().addStatement(dotClass(beanType)).build())
                .build();

        var instanceField = FieldSpec.builder(
                ClassName.get("", name), "instance", PUBLIC, STATIC, FINAL
        ).initializer(CodeBlock.of("new $L()", name)).build();

        var builder = classBuilder(name)
                .addMethod(constructorBuilder().build())
                .addField(instanceField)
                .addField(typeField)
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
                        ClassName.get(superclass.getType()),
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
                    addInheritedParams(
                            inheritedParamsFieldInitializer,
                            ClassName.get(iface.getType()),
                            ifaceParametersEnumName,
                            addSuperParams || i > 0);
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
            for (var property : bean.getProperties().stream().filter(MetaBean.Property::isPublic).toList()) {
                var propertyName = property.getName();
                if (propertyPerName.put(propertyName, property) != null) {
                    throw new IllegalStateException("property already handled, " + propertyName);
                }
                allReadable &= isReadable(property);
                allWritable &= isWriteable(property);
            }
            if (superclass != null) {
                var superProperties = superclass.getProperties().stream().filter(MetaBean.Property::isPublic).toList();
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
                var getterName = "getter";
                var setterName = "setter";
                var getterType = getFunctionType(beanType, typeVariable);
                var setterType = getBiConsumerType(beanType, typeVariable);

                var constructor = constructorBuilder().addModifiers(PRIVATE)
                        .addParameter(nameArgType, "name")
                        .addParameter(typeArgType, "type")
                        .addParameter(getterType, getterName)
                        .addParameter(setterType, setterName);

                var constructorBody = CodeBlock.builder()
                        .addStatement("super($L, $L)", "name", "type")
                        .addStatement("this." + getterName + " = " + getterName)
                        .addStatement("this." + setterName + " = " + setterName);

                constructor.addCode(constructorBody.build());

                var subType = classBuilder(readWriteTypeName)
                        .addModifiers(STATIC, FINAL, PUBLIC)
                        .superclass(ParameterizedTypeName.get(typeName, typeVariable))
                        .addTypeVariable(typeVariable)
                        .addSuperinterface(readWriteInterface)
                        .addMethod(constructor.build());
                addGetter(subType, beanType, typeVariable, getterType, getterName);
                addSetter(subType, beanType, typeVariable, setterType, setterName);

                fieldsClassBuilder.addType(subType.build());
            }

            if (readUsed && !readTypeName.equals(typeName)) {
                var getterName = "getter";
                var getterType = getFunctionType(beanType, typeVariable);

                var constructor = constructorBuilder().addModifiers(PRIVATE)
                        .addParameter(nameArgType, "name")
                        .addParameter(typeArgType, "type")
                        .addParameter(getterType, getterName);

                var constructorBody = CodeBlock.builder()
                        .addStatement("super($L, $L)", "name", "type")
                        .addStatement("this." + getterName + " = " + getterName);

                constructor.addCode(constructorBody.build());

                var subType = classBuilder(readTypeName)
                        .addModifiers(STATIC, FINAL, PUBLIC)
                        .superclass(ParameterizedTypeName.get(typeName, typeVariable))
                        .addTypeVariable(typeVariable)
                        .addSuperinterface(readInterface)
                        .addMethod(constructor.build());

                addGetter(subType, beanType, typeVariable, getterType, getterName);

                fieldsClassBuilder.addType(subType.build());
            }

            if (writeUsed && !writeTypeName.equals(typeName)) {
                var setterName = "setter";
                var setterType = getBiConsumerType(beanType, typeVariable);

                var constructor = constructorBuilder().addModifiers(PRIVATE)
                        .addParameter(nameArgType, "name")
                        .addParameter(typeArgType, "type")
                        .addParameter(setterType, setterName);

                var constructorBody = CodeBlock.builder()
                        .addStatement("super($L, $L)", "name", "type")
                        .addStatement("this." + setterName + " = " + setterName);

                constructor.addCode(constructorBody.build());

                var subType = classBuilder(writeTypeName)
                        .addModifiers(STATIC, FINAL, PUBLIC)
                        .superclass(ParameterizedTypeName.get(typeName, typeVariable))
                        .addTypeVariable(typeVariable)
                        .addSuperinterface(writeInterface)
                        .addMethod(constructor.build());

                addSetter(subType, beanType, typeVariable, setterType, setterName);

                fieldsClassBuilder.addType(subType.build());
            }

            uniqueNames.addAll(propertyPerName.keySet());

            var nameFieldName = getUniqueName("name", uniqueNames);
            var typeFieldName = getUniqueName("type", uniqueNames);

            populateTypeAwareClass(fieldsClassBuilder, nameFieldName, typeFieldName, nameArgType, typeArgType);

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

        var srcModifiers = ofNullable(bean.getType().getModifiers()).orElse(Set.of());
        var accessLevel = srcModifiers.contains(PRIVATE) ? PRIVATE
                : srcModifiers.contains(PROTECTED) ? PROTECTED
                : srcModifiers.contains(PUBLIC) ? PUBLIC : null;
        if (accessLevel != null) {
            builder.addModifiers(accessLevel);
        }

//        var nestedTypes = ofNullable(bean.getNestedTypes()).orElse(List.of());
//        for (var nestedBean : nestedTypes) {
//            var beanClassName = nestedBean.getClassName();
//            var nestedName = getUniqueName(beanClassName, uniqueNames);
//            if (!beanClassName.equals(nestedName)) {
//                nestedBean = nestedBean.toBuilder().className(nestedName).build();
//            }
//            builder.addType(newTypeBuilder(nestedBean, STATIC).build());
//        }

        var beanBuilderInfo = bean.getBeanBuilderInfo();
        if (beanBuilderInfo != null) {
            builder.addType(newBuilderType(beanBuilderInfo));
        }

        for (var generator : customizers) {
            generator.customize(messager, bean, builder);
        }

        return builder;
    }

    public static CodeBlock newInstanceCall(TypeName className, CodeBlock args) {
        return CodeBlock.builder().add("new $T<>($L)", className, args).build();
    }

    public static CodeBlock enumConstructorArgs(String name, CodeBlock type, CodeBlock getter, CodeBlock setter) {
        var builder = enumConstructorArgs(name, type).toBuilder();
        if (getter != null) {
            builder.add(", ").add(getter);
        }
        if (setter != null) {
            builder.add(", ").add(setter);
        }
        return builder.build();
    }

    public static CodeBlock enumConstructorArgs(String name, CodeBlock type) {
        return CodeBlock.builder().add("\"" + name + "\"").add(", ").add(type).build();
    }

    public static CodeBlock dotClass(TypeName type) {
        if (type instanceof ParameterizedTypeName pt) {
            type = pt.rawType;
            return CodeBlock.of("(Class)$T.class", type);
        }
        return CodeBlock.of("$T.class", type);
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
        var uniqueNames = new HashSet<>(paramNames);

        var nameFieldName = getUniqueName("name", uniqueNames);
        var typeFieldName = getUniqueName("type", uniqueNames);
        var nameArgType = ClassName.get(String.class);
        var typeArgType = ParameterizedTypeName.get(ClassName.get(Class.class), typeVariable);

        populateTypeAwareClass(typesBuilder, nameFieldName, typeFieldName, nameArgType, typeArgType);

        var constructor = constructorBuilder();
        var constructorBody = CodeBlock.builder();

        populateConstructor(constructor, constructorBody, nameArgType, nameFieldName, typeArgType, typeFieldName);

        typesBuilder.addMethod(constructor.addCode(constructorBody.build()).build());

        addValues(typesBuilder, className, paramNames, uniqueNames);
        return typesBuilder.build();
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

    public static ParameterizedTypeName getFunctionType(ClassName beanType, TypeVariableName propertyTypeVar) {
        return ParameterizedTypeName.get(ClassName.get(Function.class), beanType, propertyTypeVar);
    }

    public static ParameterizedTypeName getBiConsumerType(ClassName beanType, TypeVariableName propertyTypeVar) {
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

    private static ParameterizedTypeName writeInterface(ClassName beanType, TypeVariableName typeVariable) {
        return ParameterizedTypeName.get(ClassName.get(Write.class), beanType, typeVariable);
    }

    private static TypeSpec newBuilderType(MetaBean.BeanBuilder builderInfo) {
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
                    setter.getEvaluatedType(), className, setterName,
                    null, false, null, setter.getSetter(), null));
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

        addValues(builder, className, setterNames, uniqueNames);

        builder.addSuperinterface(writeInterface(beanType, typeVariable));
        return builder.build();
    }

    public static TypeSpec.Builder addValues(
            TypeSpec.Builder builder, ClassName typeName, Collection<String> values, Set<String> uniqueNames
    ) {
        var valuesField = getUniqueName("values", uniqueNames);
        return builder
                .addField(listField(valuesField, typeName, CodeBlock.builder()
                        .add(values.stream().reduce((l, r) -> l + (!l.isEmpty() ? ", " : "") + r)
                                .orElse("")).build(), PRIVATE, FINAL, STATIC)
                )
                .addMethod(MethodSpec.methodBuilder("values")
                        .addModifiers(PUBLIC, FINAL, STATIC)
                        .returns(ParameterizedTypeName.get(ClassName.get(List.class), typeName))
                        .addStatement("return " + valuesField).build());
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
        return CodeBlock.builder().add(
                "$T.entry($L, $L)",
                Map.class,
                mapKey,
                mapValue).build();
    }

    public static String getUniqueName(String name, Collection<String> uniqueNames) {
        while (uniqueNames.contains(name)) {
            name = "_" + name;
        }
        uniqueNames.add(name);
        return name;
    }

    private static boolean isReadable(MetaBean.Property property) {
        var getter = property.getGetter();
        var recordComponent = property.getRecordComponent();
        return getter != null || recordComponent != null || isPublicField(property);
    }

    private static boolean isWriteable(MetaBean.Property property) {
        var setter = property.getSetter();
        return (setter != null || isPublicField(property)) && property.getRecordComponent() == null;
    }

    private static boolean isPublicField(MetaBean.Property property) {
        return property.getField() != null && property.isPublicField();
    }

    private static FieldSpec newPropertyConstant(ClassName beanType, MetaBean.Property property, ClassName constType) {
        var name = property.getName();
        var type = property.getEvaluatedType();
        var field = property.getField();
        var record = property.getRecordComponent();
        var getter = property.getGetter();
        var setter = property.getSetter();
        var fieldPublic = property.isPublicField();
        return newPropertyConstant(type, constType, name, field, fieldPublic, getter, setter, record);
    }

    private static FieldSpec newPropertyConstant(TypeMirror propertyType,
                                                 ClassName constType, String constName,
                                                 VariableElement field, boolean isPublicField,
                                                 ExecutableElement getter, ExecutableElement setter,
                                                 RecordComponentElement record) {
        var uniqueNames = new HashSet<String>();
        var beanParamName = getUniqueName("bean", uniqueNames);
        var valueParamName = getUniqueName("value", uniqueNames);

        var getterArgCode = getGetterArgCode(beanParamName, isPublicField, field, record, getter);
        var setterArgCode = getSetterArgCode(beanParamName, valueParamName, isPublicField, field, record, setter);

        var typeName = TypeName.get(propertyType);
        return FieldSpec.builder(
                ParameterizedTypeName.get(constType, getUnboxedTypeVarName(typeName)), constName, PUBLIC, FINAL, STATIC
        ).initializer(newInstanceCall(constType, enumConstructorArgs(constName, dotClass(typeName),
                getterArgCode, setterArgCode))).build();
    }

    public static CodeBlock getSetterArgCode(String beanParamName, String valueParamName, boolean isPublicField,
                                             VariableElement field, RecordComponentElement record,
                                             ExecutableElement setter) {
        var setterName = ofNullable(setter).map(ExecutableElement::getSimpleName).orElse(null);
        if (setterName != null) {
            return CodeBlock.builder().add("($L, $L) -> $L.$L($L)", beanParamName, valueParamName, beanParamName,
                    setterName, valueParamName).build();
        } else if (record == null && (field != null && isPublicField)) {
            return CodeBlock.builder().add("($L, $L) -> $L.$L = $L",
                    beanParamName, valueParamName, beanParamName, field.getSimpleName(), valueParamName
            ).build();
        }
        return null;
    }

    public static CodeBlock getGetterArgCode(
            String paramName, boolean isPublicField, VariableElement field,
            RecordComponentElement record, ExecutableElement getter
    ) {
        var getterName = ofNullable(getter).map(ExecutableElement::getSimpleName).orElse(null);
        var callName = getterName != null ? getterName + "()" : record != null
                ? record.getAccessor().getSimpleName() + "()" : field != null && isPublicField
                ? field.getSimpleName().toString() : null;
        return callName != null ? CodeBlock.builder().add("$L -> $L.$L", paramName, paramName, callName).build() : null;
    }

    public static TypeName getUnboxedTypeVarName(TypeName type) {
        return type.isPrimitive() ? type.box() : type;
    }

    static FieldSpec listField(String name, TypeName type, CodeBlock init, Modifier... modifiers) {
        return builder(ParameterizedTypeName.get(ClassName.get(List.class), type), name, modifiers)
                .initializer(CodeBlock.builder().add("$T.of($L)", List.class, init).build())
                .build();
    }

    static FieldSpec mapField(String name, ClassName key, ClassName value, CodeBlock init, Modifier... modifiers) {
        return builder(
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

}
