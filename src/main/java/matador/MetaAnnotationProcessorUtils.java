package matador;

import io.jbock.javapoet.*;
import lombok.experimental.UtilityClass;

import javax.annotation.processing.Messager;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.*;
import java.util.*;

import static io.jbock.javapoet.FieldSpec.builder;
import static io.jbock.javapoet.MethodSpec.constructorBuilder;
import static io.jbock.javapoet.MethodSpec.methodBuilder;
import static io.jbock.javapoet.TypeSpec.*;
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

    static MetaBean.Property getProperty(Map<String, MetaBean.Property> properties, String propName) {
        return properties.computeIfAbsent(propName, name -> MetaBean.Property.builder().name(name).build());
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

    static TypeSpec enumConstructor(String value) {
        return anonymousClassBuilder(CodeBlock.builder().add(value).build()).build();
    }

    static String dotClass(String type) {
        return (type != null && !type.isEmpty() ? type : "Object") + ".class";
    }

    static String getStrType(TypeMirror type, List<MetaBean.Param> typeParameters) {
        return type instanceof TypeVariable typeVariable ? getStrType(typeVariable, typeParameters)
                : type instanceof IntersectionType intersectionType ? getStrType(intersectionType, typeParameters)
                : type instanceof ArrayType || type instanceof DeclaredType || type instanceof PrimitiveType
                ? type.toString() : null;
    }

    private static String getStrType(IntersectionType intersectionType, List<MetaBean.Param> typeParameters) {
        return getStrType(intersectionType.getBounds().get(0), typeParameters);
    }

    private static String getStrType(TypeVariable typeVariable, List<MetaBean.Param> typeParameters) {
        var collect = typeParameters != null
                ? typeParameters.stream().collect(toMap(p -> p.getName().asType(), p -> p.getType()))
                : Map.<TypeMirror, TypeMirror>of();
        var type = collect.get(typeVariable);
        if (type != null  && !type.equals(typeVariable)) {
            return getStrType(type, typeParameters);
        } else {
            return getStrType(typeVariable.getUpperBound(), typeParameters);
        }
    }

//    static MetaBean getParentBean(Map<String, MetaBean> nestedTypes, String typeName) {
//        var parentBean = nestedTypes.get(typeName);
//        if (parentBean == null) {
//            parentBean = MetaBean.builder().class_(typeName).isRecord(false).build();
//            nestedTypes.put(typeName, parentBean);
//        }
//        return parentBean;
//    }

    static void populateTypeParameters(TypeSpec.Builder builder, String enumName, List<MetaBean.Param> typeParameters) {
        var typesBuilder = typesEnumBuilder(enumName);
        for (var param : typeParameters) {
            var name = param.getName().getSimpleName().toString();
            typesBuilder.addEnumConstant(name, enumConstructor(dotClass(getStrType(param.getType(), typeParameters))));
        }
        builder.addType(typesBuilder.build());
    }

    static TypeSpec.Builder fieldsEnumBuilder(String enumName) {
        return typeAwareEnum(enumName);
    }

    private static TypeSpec.Builder typesEnumBuilder(String enumName) {
        return typeAwareEnum(enumName);
    }

    private static TypeSpec.Builder typeAwareEnum(String enumName) {
        var typeType = ClassName.get("", "Class<?>");
        return enumBuilder(enumName)
                .addSuperinterface(Typed.class)
                .addModifiers(PUBLIC)
                .addField(builder(typeType, "type").addModifiers(PUBLIC, FINAL).build())
                .addMethod(
                        methodBuilder("type")
                                .addAnnotation(Override.class)
                                .addModifiers(PUBLIC).returns(typeType)
                                .addCode("return this.type;")
                                .build()
                )
                .addMethod(
                        constructorBuilder()
                                .addParameter(typeType, "type")
                                .addCode(CodeBlock.builder()
                                        .add("this." + "type" + " = " + "type" + ";")
                                        .build())
                                .build()
                );
    }

    static MetaBean getBean(TypeElement type, DeclaredType declaredType, Messager messager) {
        if (type == null || isObjectType(type)) {
            return null;
        }
        var isRecord = type.getRecordComponents() != null;
        var meta = type.getAnnotation(Meta.class);


        var properties = new LinkedHashMap<String, MetaBean.Property>();
        var nestedTypes = new LinkedHashMap<String, MetaBean>();
        var recordComponents = type.getRecordComponents();
        if (recordComponents != null) {
            for (var recordComponent : recordComponents) {
                var name1 = recordComponent.getSimpleName();
                var propType = recordComponent.asType();
                var property = getProperty(properties, name1.toString());
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
                var bean1 = getBean(te, null, messager);
                var nestedBeanClassName = bean1.getClassName();
                var exists = nestedTypes.get(nestedBeanClassName);
                if (exists == null) {
                    nestedTypes.put(nestedBeanClassName, bean1);
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

    static MethodSpec enumValuesMethod(String name, ClassName typeClassName, boolean overr) {
        var builder = methodBuilder(name)

                .addModifiers(PUBLIC)
                .returns(ArrayTypeName.of(typeClassName))
                .addCode(
                        CodeBlock.builder()
                                .addStatement("return $T.values()", typeClassName)
                                .build()
                );
        if (overr) {
            builder.addAnnotation(Override.class);
        }
        return builder.build();
    }

    private static TypeSpec newTypeSpec(String interfaceName, String enumName, MetaBean interfaceMeta) {
        var builder = classBuilder(interfaceName);
        populateTypeParameters(builder, enumName, interfaceMeta.getTypeParameters());
        return builder.build();
    }

    static TypeSpec newTypeSpec(MetaBean bean) {
        var meta = ofNullable(bean.getMeta());
        var fields = meta.map(Meta::properties);
        var parameters = meta.map(Meta::params);

        var addFieldsEnum = fields.map(Meta.Properties::enumerate).orElse(false);
        var addParamsEnum = parameters.map(Meta.Parameters::enumerate).orElse(false);
        var inheritMetamodel = addFieldsEnum && addParamsEnum;

        var className = ClassName.get(bean.getType());

        var name = bean.getName();
        var typeFieldType = ParameterizedTypeName.get(ClassName.get(Class.class), className);
        var typeGetter = methodBuilder("type")
                .addModifiers(PUBLIC, FINAL)
                .returns(typeFieldType)
                .addCode(CodeBlock.builder()
                        .addStatement("return type")
                        .build());

        if (inheritMetamodel) {
            typeGetter.addAnnotation(Override.class);
        }
        var typeField = builder(
                typeFieldType, "type", PUBLIC, FINAL)
                .initializer(CodeBlock.builder().addStatement("$T.class", className).build())
                .build();

        var builder = classBuilder(name)
                .addMethod(constructorBuilder().build())
                .addField(typeField).addMethod(typeGetter.build())
                .addModifiers(FINAL);


        if (inheritMetamodel) {
            builder.addSuperinterface(
                    ParameterizedTypeName.get(
                            ClassName.get(MetaModel.class),
                            className)
            );
        }

        var typeParameters = bean.getTypeParameters();
        if (addParamsEnum) {
            var typeName = meta.get().params().className();
            populateTypeParameters(builder, typeName, typeParameters);

            builder.addMethod(
                    enumValuesMethod("parameters", ClassName.get("", typeName), inheritMetamodel)
            );
        }

        if (addFieldsEnum) {
            var typeName = fields.get().className();
            var fieldsBuilder = fieldsEnumBuilder(typeName);
            var propertyNames = new HashSet<String>();
            var properties = bean.getProperties();
            for (var property : properties) {
                var propertyName = property.getName();
                if (!propertyNames.add(propertyName)) {
                    throw new IllegalStateException("property already handled, " + propertyName);
                }
                getAddEnumConstant(fieldsBuilder, bean.getTypeParameters(), propertyName, property.getType());
            }

            var superclass = bean.getSuperclass();
            if (superclass != null) {
                var superProperties = superclass.getProperties();
                for (var property : superProperties) {
                    var propertyName = property.getName();
                    if (!propertyNames.contains(propertyName)) {
                        getAddEnumConstant(fieldsBuilder, superclass.getTypeParameters(), propertyName, property.getType());
                    }
                }
            }

            builder.addType(fieldsBuilder.build());
            builder.addMethod(
                    enumValuesMethod("fields", ClassName.get("", typeName), inheritMetamodel)
            );
        }
        var modifiers = ofNullable(bean.getModifiers()).orElse(Set.of());
        var accessLevel = modifiers.contains(PRIVATE) ? PRIVATE
                : modifiers.contains(PROTECTED) ? PROTECTED
                : modifiers.contains(PUBLIC) ? PUBLIC : null;
        if (accessLevel != null) {
            builder.addModifiers(accessLevel);
        }

        var nestedTypeNames = new HashSet<String>();
        var nestedTypes = ofNullable(bean.getNestedTypes()).orElse(List.of());
        for (var nestedBean : nestedTypes) {
            var nestedName = nestedBean.getClassName();
            if (!nestedTypeNames.add(nestedName)) {
                throw new IllegalStateException("Unexpectedly nested type has been already handled, " + nestedName);
            }
            builder.addType(newTypeSpec(nestedBean));
        }

        if (addParamsEnum) {
            var interfaces = bean.getInterfaces();
            if (interfaces != null) interfaces.forEach((interfaceMeta) -> {
                var interfaceName = interfaceMeta.getClassName();
                while (nestedTypeNames.contains(interfaceName)) {
                    interfaceName = "_" + interfaceName;
                }
                builder.addType(newTypeSpec(interfaceName, meta.get().params().className(), interfaceMeta));
            });
        }

        return builder.build();
    }

    private static void getAddEnumConstant(Builder fieldsBuilder, List<MetaBean.Param> typeParameters,
                                           String propertyName, TypeMirror propertyType) {
        fieldsBuilder.addEnumConstant(
                propertyName, enumConstructor(dotClass(getStrType(propertyType, typeParameters)))
        );
    }

    record TypeInfo(DeclaredType declaredType, TypeElement typeElement) {

    }
}
