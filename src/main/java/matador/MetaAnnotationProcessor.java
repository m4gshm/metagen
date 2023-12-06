package matador;

import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.JavaFile;
import io.jbock.javapoet.MethodSpec;
import io.jbock.javapoet.ParameterSpec;
import io.jbock.javapoet.ParameterizedTypeName;
import io.jbock.javapoet.TypeSpec;
import lombok.SneakyThrows;
import matador.Meta.Fields;
import matador.Meta.Parameters;
import matador.MetaBean.Property;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.jbock.javapoet.FieldSpec.builder;
import static io.jbock.javapoet.MethodSpec.constructorBuilder;
import static io.jbock.javapoet.TypeSpec.Builder;
import static io.jbock.javapoet.TypeSpec.classBuilder;
import static io.jbock.javapoet.TypeSpec.enumBuilder;
import static java.util.Optional.ofNullable;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static matador.MetaAnnotationProcessorUtils.classType;
import static matador.MetaAnnotationProcessorUtils.dotClass;
import static matador.MetaAnnotationProcessorUtils.enumConstructor;
import static matador.MetaAnnotationProcessorUtils.extractGenericParams;
import static matador.MetaAnnotationProcessorUtils.getPackage;
import static matador.MetaAnnotationProcessorUtils.getParentBean;
import static matador.MetaAnnotationProcessorUtils.getProperty;
import static matador.MetaAnnotationProcessorUtils.getPropertyName;
import static matador.MetaAnnotationProcessorUtils.getStrType;
import static matador.MetaAnnotationProcessorUtils.getTypeInfo;
import static matador.MetaAnnotationProcessorUtils.isBoolGetter;
import static matador.MetaAnnotationProcessorUtils.isGetter;
import static matador.MetaAnnotationProcessorUtils.isObjectType;
import static matador.MetaAnnotationProcessorUtils.isSetter;
import static matador.MetaAnnotationProcessorUtils.populateTypeParameters;
import static matador.MetaAnnotationProcessorUtils.updateType;

@SupportedAnnotationTypes("matador.Meta")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class MetaAnnotationProcessor extends AbstractProcessor {

    private static MetaBean getBean(TypeElement type, Messager messager) {
        var isRecord = type.getRecordComponents() != null;

        var properties = new LinkedHashMap<String, Property>();
        var nestedTypes = new LinkedHashMap<String, MetaBean>();

        var meta = type.getAnnotation(Meta.class);

        var typeParameters = new ArrayList<>(extractGenericParams(type, null));

        var interfaces = new LinkedHashMap<String, MetaBean.Interface>();
        extractPropertiesAndNestedTypes(type, properties, nestedTypes, interfaces, messager);

        var suffix = meta.suffix();
        var simpleName = type.getSimpleName();

        var packageElement = getPackage(type);

        var name = simpleName.toString();
        var metaName = name + suffix;
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
                                                        Map<String, Property> properties,
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

    private static String getAggregatorName(String beanPackage) {
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
        return !prefix.isEmpty() ? prefix : "Metas";
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
                .addMethod(constructorBuilder().build())
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
                .map(type -> getBean(type, processingEnv.getMessager())).toList();

        record AggregatorParts(String package_, String name, List<String> mapParts) {

        }

        var aggregators = new HashMap<String, AggregatorParts>();
        for (var bean : beans) {
            var beanPackage = bean.getPackage_();

            var aggParts = aggregators.computeIfAbsent(beanPackage,
                    bp -> new AggregatorParts(bp, getAggregatorName(bp), new ArrayList<>(List.of("Map.of(\n")))
            );

            var name = bean.getName();
            if (aggParts.mapParts.size() > 1) {
                aggParts.mapParts.add(",\n");
            }
            aggParts.mapParts.add(bean.getClass_() + ".class, new " + name + "()");

            var javaFileObject = JavaFile.builder(beanPackage, newTypeSpec(bean)).build().toJavaFileObject();
            writeFile(beanPackage, name, javaFileObject);
        }

        aggregators.forEach((pack, parts) -> {
            var className = ParameterizedTypeName.get(Map.class, Class.class, Object.class);
            var init = parts.mapParts.stream().reduce("", (l, r) -> l + r) + "\n)";
            var typeSpec = classBuilder(parts.name)
                    .addModifiers(PUBLIC, FINAL)
                    .addField(builder(className, "metas", PRIVATE, FINAL)
                            .initializer(CodeBlock.builder().add(init).build()).build()
                    )
                    .addMethod(MethodSpec.methodBuilder("of")
                            .addModifiers(PUBLIC)
                            .addParameter(
                                    ParameterSpec.builder(ClassName.get(Class.class), "type").build()
                            )
                            .returns(Object.class)
                            .addCode(
                                    CodeBlock.builder()
                                            .addStatement("return metas.get(type)")
                                            .build()
                            )
                            .build()
                    )
                    .build();
            var javaFile = JavaFile.builder(pack, typeSpec).build().toJavaFileObject();
            writeFile(pack, parts.name, javaFile);
        });

        return true;
    }

    @SneakyThrows
    private void writeFile(String pack, String name, JavaFileObject javaFileObject) {
        var sourceFile = processingEnv.getFiler().createSourceFile(pack + "." + name);
        try (var out = new PrintWriter(sourceFile.openWriter());
             var reader = javaFileObject.openReader(true)) {
            reader.transferTo(out);
        }
    }

    record TypInfo(DeclaredType declaredType, TypeElement typeElement) {

    }

}
