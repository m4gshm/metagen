package matador;

import io.jbock.javapoet.ArrayTypeName;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.MethodSpec;
import io.jbock.javapoet.TypeSpec;
import lombok.experimental.UtilityClass;

import javax.annotation.processing.Messager;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.jbock.javapoet.FieldSpec.builder;
import static io.jbock.javapoet.MethodSpec.constructorBuilder;
import static io.jbock.javapoet.MethodSpec.methodBuilder;
import static io.jbock.javapoet.TypeSpec.anonymousClassBuilder;
import static io.jbock.javapoet.TypeSpec.classBuilder;
import static io.jbock.javapoet.TypeSpec.enumBuilder;
import static java.beans.Introspector.decapitalize;
import static java.util.Optional.ofNullable;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
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

    static TypInfo getTypeInfo(TypeMirror typeMirror) {
        return typeMirror instanceof DeclaredType declaredType
                && declaredType.asElement() instanceof TypeElement typeElement
                && !isObjectType(typeElement) ? new TypInfo(declaredType, typeElement) : null;
    }

    static List<MetaBean.Param> extractGenericParams(TypeElement typeElement, DeclaredType declaredType) {
        var arguments = declaredType != null ? declaredType.getTypeArguments() : null;
        var parameters = typeElement.getTypeParameters();

        var params = new ArrayList<MetaBean.Param>();
        for (int i = 0; i < parameters.size(); i++) {
            var paramName = parameters.get(i);
            var paramType = arguments != null ? arguments.get(i) : paramName.asType();
            params.add(MetaBean.Param.builder()
                    .name(paramName.getSimpleName().toString())
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

    static String getStrType(TypeMirror type) {
        return type instanceof TypeVariable typeVariable ? getStrType(typeVariable.getUpperBound())
                : type instanceof IntersectionType intersectionType ? getStrType(intersectionType.getBounds().get(0))
                : type instanceof ArrayType || type instanceof DeclaredType || type instanceof PrimitiveType
                ? type.toString() : null;
    }

    static MetaBean getParentBean(Map<String, MetaBean> nestedTypes, String typeName) {
        var parentBean = nestedTypes.get(typeName);
        if (parentBean == null) {
            parentBean = MetaBean.builder().name(typeName).isRecord(false).build();
            nestedTypes.put(typeName, parentBean);
        }
        return parentBean;
    }

    static void populateTypeParameters(TypeSpec.Builder builder, String enumName, List<MetaBean.Param> typeParameters) {
        var typesBuilder = typesEnumBuilder(enumName);
        for (var param : typeParameters) {
            typesBuilder.addEnumConstant(param.getName(), enumConstructor(dotClass(getStrType(param.getType()))));
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
                .addSuperinterface(TypeAware.class)
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

    static MetaBean getBean(TypeElement type, Messager messager) {
        var isRecord = type.getRecordComponents() != null;

        var properties = new LinkedHashMap<String, MetaBean.Property>();
        var nestedTypes = new LinkedHashMap<String, MetaBean>();

        var meta = type.getAnnotation(Meta.class);

        var typeParameters = new ArrayList<>(extractGenericParams(type, null));

        var interfaces = new LinkedHashMap<String, MetaBean.Interface>();
        extractPropertiesAndNestedTypes(type, properties, nestedTypes, interfaces, messager);

        var suffix = meta.suffix();
        var simpleName = type.getSimpleName();

        var packageElement = getPackage(type);

        var name = simpleName.toString();
        var metaName = name + (suffix == null || suffix.trim().isEmpty() ? Meta.META : suffix);
        var pack = packageElement != null ? packageElement.getQualifiedName().toString() : null;
        return MetaBean.builder()
                .meta(meta)
                .class_(name)
                .package_(pack)
                .modifiers(type.getModifiers())
                .isRecord(isRecord)
                .typeParameters(typeParameters)
                .interfaces(interfaces)
                .properties(new ArrayList<>(properties.values()))
                .nestedTypes(nestedTypes)
                .name(metaName)
                .build();
    }

    private static void extractPropertiesAndNestedTypes(TypeElement type,
                                                        Map<String, MetaBean.Property> properties,
                                                        Map<String, MetaBean> nestedTypes,
                                                        Map<String, MetaBean.Interface> interfaces, Messager messager) {
        if (type == null || isObjectType(type)) {
            return;
        }
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
                var bean = getBean(te, messager);
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
                superclass.typeElement, properties, nestedTypes, interfaces, messager)
        );

        type.getInterfaces().stream().map(MetaAnnotationProcessorUtils::getTypeInfo).filter(Objects::nonNull).forEach(iface -> {
            var params = extractGenericParams(iface.typeElement, iface.declaredType);
            extractPropertiesAndNestedTypes(iface.typeElement, properties, nestedTypes, interfaces, messager);

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

    private static TypeSpec newTypeSpec(String interfaceName, String enumName, MetaBean.Interface interfaceMeta) {
        var builder = classBuilder(interfaceName);
        populateTypeParameters(builder, enumName, interfaceMeta.getTypeParameters());
        return builder.build();
    }

    static TypeSpec newTypeSpec(MetaBean bean) {
        var meta = ofNullable(bean.getMeta());
        var fields = meta.map(Meta::fields);
        var parameters = meta.map(Meta::params);

        var addFieldsEnum = fields.map(Meta.Fields::enumerate).orElse(false);
        var addParamsEnum = parameters.map(Meta.Parameters::enumerate).orElse(false);

        var builder = classBuilder(bean.getName())
                .addMethod(constructorBuilder().build())
                .addModifiers(FINAL);

        var inheritMetamodel = addFieldsEnum && addParamsEnum;
        if (inheritMetamodel) {
            builder.addSuperinterface(ClassName.get(MetaModel.class));
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
            var properties = bean.getProperties();
            for (var property : properties) {
                fieldsBuilder.addEnumConstant(
                        property.getName(),
                        enumConstructor(dotClass(getStrType(property.getType())))
                );
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

    record TypInfo(DeclaredType declaredType, TypeElement typeElement) {

    }
}
