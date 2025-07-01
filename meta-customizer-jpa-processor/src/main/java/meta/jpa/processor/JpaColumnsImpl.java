package meta.jpa.processor;

import io.jbock.javapoet.*;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import meta.Meta;
import meta.MetaBean;
import meta.MetaBean.BeanBuilder;
import meta.MetaBean.BeanBuilder.Setter;
import meta.MetaCustomizer;
import meta.jpa.customizer.JpaColumns;
import meta.jpa.customizer.JpaColumns.GeneratedColumnNamePostProcess.PostProcessors;
import meta.util.ClassLoadUtility;
import meta.util.JavaPoetUtils;
import meta.util.MetaBeanExtractor;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Stream;

import static java.lang.Boolean.TRUE;
import static java.lang.Character.isLowerCase;
import static java.lang.Character.isUpperCase;
import static java.util.Arrays.stream;
import static java.util.Collections.reverse;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static javax.lang.model.element.Modifier.*;
import static javax.tools.Diagnostic.Kind.OTHER;

/**
 * Generates JPA based metadata like column name, primary key, embedded types.
 * Provides limited support of @Column @Id, @Transient, @Embedded, @EmbeddedId, @AttributeOverrides annotations.
 */
@Log
@RequiredArgsConstructor
public class JpaColumnsImpl implements JpaColumns, MetaCustomizer<TypeSpec.Builder> {
    private String className;
    private GeneratedColumnNamePostProcess columnNamePostProcess;
    private boolean withSuperclassColumns;
    private boolean checkForEntityAnnotation;
    private List<Class> implementInterfaces;

    private static Map<String, Map<String, Object>> getAnnotationElements(List<? extends AnnotationMirror> annotations) {
        return annotations.stream().collect(toMap(JpaColumnsImpl::evalName, a -> evalValues(a.getElementValues()), (l, r) -> l));
    }

    private static String evalName(AnnotationMirror annotationMirror) {
        return annotationMirror.getAnnotationType().asElement().toString();
    }

    private static Map<String, Object> evalValues(
            Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues
    ) {
        var result = new LinkedHashMap<String, Object>();
        elementValues.forEach((executableElement, annotationValue) -> result.put(
                executableElement.getSimpleName().toString(), evalValue(annotationValue.getValue())
        ));
        return result;
    }

    private static Object evalValue(Object value) {
        return value instanceof AnnotationMirror annotationMirror
                ? evalValues(annotationMirror.getElementValues())
                : value instanceof Collection<?> collection
                ? collection.stream().map(JpaColumnsImpl::evalValue).toList()
                : value;
    }

    private static Map<String, String> getColumnOverrides(Map<String, Object> attributeOverrides) {
        return (attributeOverrides != null ? attributeOverrides.get("value") : null
        ) instanceof Collection<?> values ? values.stream().map(v -> v instanceof Map<?, ?> m ? m : null
        ).filter(Objects::nonNull).map(m -> {
            if (m.get("column") instanceof Map<?, ?> mc) {
                var name = m.get("name");
                var colName = mc.get("name");
                return name != null && colName != null ? Map.entry(name.toString(), colName.toString()) : null;
            }
            return null;
        }).filter(Objects::nonNull).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)) : Map.of();
    }

    private static String addColumnConstToClassBuilder(TypeSpec.Builder jpaColumnsClass,
                                                       Column column, ClassName className,
                                                       boolean addSetter, boolean allBuildable) {
        final CodeBlock getterArgCode, setterArgCode;
        var type = TypeName.get(column.property().getEvaluatedType());
        var parametrizedBuilderType = getParametrizedBuilderType(column.beanBuilder());

        var path = column.path();
        var straightPath = new ArrayList<>(path);
        reverse(straightPath);

        var pathAmount = straightPath.size();
        if (pathAmount > 1) {
            var uniqueNames = new HashSet<String>();

            var valueParam = JavaPoetUtils.getUniqueName("value", uniqueNames);
            var beanParam = JavaPoetUtils.getUniqueName("bean", uniqueNames);
            var callableName = JavaPoetUtils.getUniqueName("bean", uniqueNames);

            var getterArgCodeB = CodeBlock.builder();
            var setterArgCodeB = CodeBlock.builder();
            for (int i = 0; i < pathAmount; i++) {
                var property = straightPath.get(i);
                var field = property.getField();
                var publicField = property.isPublicField();
                var propertyName = property.getName();
                var record = property.getRecordComponent();
                var getter = property.getGetter();
                var setter = property.getSetter();
                var getterPartCode = requireNonNull(JavaPoetUtils.getGetterCallCode(
                        callableName, publicField, field, record, getter
                ), () -> "No public read accessor part '" + propertyName + "' for column '" + column.name() + "'");
                getterArgCodeB.add(".map(").add(getterPartCode).add(")");
                if (i == pathAmount - 1) {
                    var propParam = JavaPoetUtils.getUniqueName("prop", uniqueNames);
                    var setCall = setter != null
                            ? CodeBlock.builder().add("$L($L)", setter.getSimpleName(), valueParam).build()
                            : (field != null && publicField)
                            ? CodeBlock.builder().add("$L.$L = $L", propParam, field.getSimpleName(), valueParam).build()
                            : null;
                    requireNonNull(setCall, () -> "no public write accessor part '" + propertyName + "' for column '" + column.name() + "'");

                    setterArgCodeB.add(".ifPresent($L -> $L.$L)", propParam, propParam, setCall);
                } else {
//                    var code = getGetterCallCode(callableName, publicField, field, record, getter);
                    var code = JavaPoetUtils.getGetterCallInitByNewCode(
                            "v", callableName, publicField, field, getter, setter, uniqueNames
                    );
                    var intermediateGetterPartCode = requireNonNull(code, () -> "No public read accessor part '" +
                            propertyName + "' for column '" + column.name() + "'"
                    );
                    setterArgCodeB.add(".map(").add(intermediateGetterPartCode).add(")");
                }
                callableName = JavaPoetUtils.getUniqueName(propertyName, uniqueNames);
            }

            getterArgCode = CodeBlock.builder().add("$L -> $T.of($L)$L.orElse(null)", beanParam,
                    Optional.class, beanParam, getterArgCodeB.build()).build();
            setterArgCode = CodeBlock.builder().add("($L, $L) -> $T.of($L)$L", beanParam, valueParam,
                    Optional.class, beanParam, setterArgCodeB.build()).build();
        } else {
            var property = column.property();

            var uniqueNames = new HashSet<String>();
            var beanParamName = JavaPoetUtils.getUniqueName("bean", uniqueNames);
            var valueParamName = JavaPoetUtils.getUniqueName("value", uniqueNames);

            var publicField = property.isPublicField();
            var field = property.getField();
            var record = property.getRecordComponent();
            getterArgCode = JavaPoetUtils.getGetterCallCode(beanParamName, publicField, field, record, property.getGetter());
            setterArgCode = JavaPoetUtils.getSetterCallCode(beanParamName, valueParamName, publicField, field, record, property.getSetter());
        }

        var fieldType = /*allBuildable
                ? ParameterizedTypeName.get(className, JavaPoetUtils.unboxedTypeVarName(type), parametrizedBuilderType)
                : */ParameterizedTypeName.get(className, JavaPoetUtils.unboxedTypeVarName(type));

        var name = column.name();
        var strPath = straightPath.stream().map(MetaBean.Property::getName).reduce((l, r) -> l + "." + r).orElse("");
        var uniqueNames = new HashSet<String>();

        var constructorArgs = JavaPoetUtils.enumConstructorArgs(name, JavaPoetUtils.dotClass(type), getterArgCode, addSetter ? setterArgCode : null);
        if (allBuildable) {
            var builderParamName = JavaPoetUtils.getUniqueName("builder", uniqueNames);
            var valueParamName = JavaPoetUtils.getUniqueName("value", uniqueNames);


            if (parametrizedBuilderType != null) {
                constructorArgs.add(", $L", JavaPoetUtils.dotClass(parametrizedBuilderType));

                var setter = column.setter();
                var builderSetter = JavaPoetUtils.getSetterCallCode(
                        builderParamName, valueParamName, false, null, null, setter.getSetter()
                );
                constructorArgs.add(", ").add(builderSetter);
            }
        }

        var enumConstructorArgs = CodeBlock.builder().add(" " + column.pk() + ", ").add("\"" + strPath + "\", ").add(
                constructorArgs.build()
        ).build();

        var columnField = FieldSpec.builder(fieldType, name, PUBLIC, STATIC, FINAL)
                .initializer(/*allBuildable
                        ? JavaPoetUtils.newInstanceCall(className, JavaPoetUtils.unboxedTypeVarName(type), parametrizedBuilderType, enumConstructorArgs)
                        : */JavaPoetUtils.newInstanceCall(className, JavaPoetUtils.unboxedTypeVarName(type), enumConstructorArgs)
                )
                .build();
        jpaColumnsClass.addField(columnField);

        return columnField.name;
    }

    private static TypeName getParametrizedBuilderType(BeanBuilder beanBuilder) {
        var builderType = ofNullable(beanBuilder).map(BeanBuilder::getType);
        var parametrizedBuilderType = builderType.map(JavaPoetUtils::wildcardParametrized).orElse(null);
        return parametrizedBuilderType;
    }

    private static String toColumnName(String name) {
        if (name == null) {
            return null;
        }

        var builder = new StringBuilder(name.length());
        var chars = name.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            var current = chars[i];
            if (i > 0 && i < chars.length - 1 && isLowerCase(chars[i - 1]) && isUpperCase(current) && isLowerCase(chars[i + 1])) {
                builder.append('_');
            }
            builder.append(current);
        }

        return builder.toString();
    }

    private static Map<String, Object> getJpaAnnotation(Map<String, Map<String, Object>> annotations, String javax, String jakarta) {
        return ofNullable(annotations.get(javax)).orElse(annotations.get(jakarta));
    }

    private static boolean isExcluded(MetaBean.Property property) {
        return property.isExcluded() || property.getAnnotation(JpaColumnsImpl.Exclude.class) != null;
    }

    private static GeneratedColumnNamePostProcess getColumnNamePostProcess(Map<String, String[]> optsMap) {
        var name = optsMap.getOrDefault(OPT_GENERATED_COLUMN_NAME_POST_PROCESS, DEFAULT_OPT_GENERATED_COLUMN_NAME_POST_PROCESS)[0];
        return Stream.of(PostProcessors.values()).filter(p -> {
            return p.name().equals(name);
        }).map(p -> (GeneratedColumnNamePostProcess) p).findFirst().orElseGet(() -> {
            Class<?> aClass;
            try {
                aClass = Class.forName(name);
            } catch (ClassNotFoundException e) {
                log.info("ColumnNamePostProcess class not found: " + name);
                aClass = null;
            }
            if (aClass != null) try {
                Object instance = aClass.getDeclaredConstructor().newInstance();
                if (instance instanceof GeneratedColumnNamePostProcess columnNamePostProcess2) {
                    return columnNamePostProcess2;
                } else {
                    log.info("Unmatched to ColumnNamePostProcess type: " + name);
                }
            } catch (NoSuchMethodException e) {
                log.info("Class doesn't have no-args public constructor: " + name);
            } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
                log.info("ColumnNamePostProcess instantiate error: " + name + ",  error: " + e.getMessage());
            }
            return PostProcessors.noop;
        });
    }

    private List<Column> getColumns(Messager messager, MetaBean bean, Map<String, String> columnOverrides,
                                    BeanBuilder builderInfo
    ) {
        var builderSetters = builderInfo != null ? builderInfo.getSetters() : List.<Setter>of();
        var setters = builderInfo != null
                ? builderSetters.stream().collect(toMap(Setter::getName, s -> s))
                : Map.<String, Setter>of();

        return bean.getProperties().stream().filter(property -> !isExcluded(property)).flatMap(property -> {
            var name = property.getName();
            var type = property.getEvaluatedType();

            var possibleBuilderSetter = setters.get(name);
            if (possibleBuilderSetter != null && !MetaBeanExtractor.isEquals(possibleBuilderSetter.getEvaluatedType(), type)) {
                possibleBuilderSetter = null;
            }

            var annotations = getAnnotationElements(property.getAnnotations());
            var _transient = getJpaAnnotation(annotations, "javax.persistence.Transient", "jakarta.persistence.Transient");
            var embedded = getJpaAnnotation(annotations, "javax.persistence.Embedded", "jakarta.persistence.Embedded");
            var embeddedId = getJpaAnnotation(annotations, "javax.persistence.EmbeddedId", "jakarta.persistence.EmbeddedId");

            if (_transient != null) {
                return Stream.empty();
            } else if (embedded != null || embeddedId != null) {
                var embeddedColumns = getEmbeddedColumns(messager, property, getColumnOverrides(getJpaAnnotation(
                        annotations, "javax.persistence.AttributeOverrides", "jakarta.persistence.AttributeOverrides"
                )));
                return embeddedId == null ? embeddedColumns.stream()
                        : embeddedColumns.stream().map(c -> c.toBuilder().pk(true).build());
            } else {
                var pk = getJpaAnnotation(annotations, "javax.persistence.Id", "jakarta.persistence.Id") != null;
                var columnAttributes = getJpaAnnotation(annotations, "javax.persistence.Column", "jakarta.persistence.Column");
                return Stream.of(newColumn(pk, property, builderInfo, possibleBuilderSetter, columnAttributes, columnOverrides));
            }
        }).toList();
    }

    private Column newColumn(boolean pk, MetaBean.Property property,
                             BeanBuilder builder, Setter setter,
                             Map<String, Object> columnAttributes,
                             Map<String, String> columnOverrides) {
        return Column.builder()
                .pk(pk).setter(setter).beanBuilder(builder)
                .name(getColumnName(property.getName(), columnAttributes, columnOverrides))
                .path(List.of(property))
                .build();
    }

    private String getColumnName(String propertyName,
                                 Map<String, Object> columnAttributes,
                                 Map<String, String> columnOverrides) {
        return ofNullable(columnAttributes).map(c -> c.get("name"))
                .map(Object::toString)
                .or(() -> ofNullable(columnOverrides).map(m -> m.get(propertyName)))
                .orElseGet(() -> {
                    return this.columnNamePostProcess.apply(toColumnName(propertyName));
                });
    }

    private List<Column> getEmbeddedColumns(
            Messager messager, MetaBean.Property property, Map<String, String> columnOverrides
    ) {
        return ofNullable(property.getBean()).map(embeddedBean -> getColumns(messager, embeddedBean,
                columnOverrides, embeddedBean.getBeanBuilderInfo())
                .stream().map(embeddedColumn -> {
                    var oldPath = embeddedColumn.path();
                    var newPath = new ArrayList<MetaBean.Property>();
                    if (oldPath != null) {
                        newPath.addAll(oldPath);
                    }
                    newPath.add(property);
                    return embeddedColumn.toBuilder().path(newPath).build();
                }).toList()
        ).orElseThrow(() -> new UnsupportedOperationException("unsupported embedded type, property '" +
                property.getName() + "', type '" + property.getType() + "'"));
    }

    @Override
    public void init(Meta.Extend.Opt... opts) {
        var optsMap = Arrays.stream(opts).collect(toMap(Meta.Extend.Opt::key, Meta.Extend.Opt::value, (l, r) -> l));
        this.className = optsMap.getOrDefault(OPT_CLASS_NAME, DEFAULT_CLASS_NAME)[0];
        this.columnNamePostProcess = getColumnNamePostProcess(optsMap);
        this.withSuperclassColumns = optsMap.getOrDefault(
                OPT_WITH_SUPERCLASS_COLUMNS, DEFAULT_WITH_SUPERCLASS_COLUMNS
        )[0].equals(TRUE.toString());
        this.checkForEntityAnnotation = optsMap.getOrDefault(
                OPT_CHECK_FOR_ENTITY_ANNOTATION, DEFAULT_CHECK_FOR_ENTITY_ANNOTATION
        )[0].equals(TRUE.toString());

        var impls = Stream.ofNullable(optsMap.get(OPT_IMPLEMENTS)).flatMap(Arrays::stream)
                .map(fullClassName -> (Class) ClassLoadUtility.load(fullClassName)).toList();
        this.implementInterfaces = impls.isEmpty() ? stream(DEFAULT_IMPLEMENTS).toList() : impls;
    }

    @Override
    public Class<TypeSpec.Builder> builderType() {
        return TypeSpec.Builder.class;
    }

    @Override
    public TypeSpec.Builder customize(Messager messager, MetaBean bean, TypeSpec.Builder classBuilder) {
        var beanType = bean.getType();
        var beanClass = ClassName.get(beanType);
        var beanAnnotations = getAnnotationElements(beanType.getAnnotationMirrors());
        if (this.checkForEntityAnnotation && getJpaAnnotation(beanAnnotations,
                "javax.persistence.Entity", "jakarta.persistence.Entity") != null) {
            return classBuilder;
        }
        var className = ClassName.get("", this.className);
        var typeVariable = TypeVariableName.get("T");

        var jpaColumnsClass = JavaPoetUtils.typeAwareClass(className, typeVariable).addModifiers(FINAL);

        implementInterfaces.stream().map(iface -> ParameterizedTypeName.get(ClassName.get(iface), typeVariable))
                .forEach(jpaColumnsClass::addSuperinterface);

        var columnOverrides = getColumnOverrides(getJpaAnnotation(beanAnnotations,
                "javax.persistence.AttributeOverrides", "jakarta.persistence.AttributeOverrides")
        );

        var columnNames = new LinkedHashSet<String>();
        var builderInfo = bean.getBeanBuilderInfo();
        var columns = new ArrayList<>(getColumns(messager, bean, columnOverrides, builderInfo));

        if (this.withSuperclassColumns) {
            var parentColumnOverrides = new LinkedHashMap<>(columnOverrides);
            var superclass = bean.getSuperclass();
            while (superclass != null) {
                var superclassAnnotations = getAnnotationElements(
                        superclass.getType().getAnnotationMirrors()
                );
                var superclassColumnOverrides = getColumnOverrides(getJpaAnnotation(
                        superclassAnnotations, "javax.persistence.AttributeOverrides",
                        "jakarta.persistence.AttributeOverrides"
                ));
                parentColumnOverrides.putAll(superclassColumnOverrides);
                var superclassColumns = getColumns(messager, superclass, parentColumnOverrides, builderInfo);
                superclass = superclass.getSuperclass();
                columns.addAll(superclassColumns);
            }
        }

        var allWriteable = true;
        var allBuildable = true;
        for (var column : columns) {
            allWriteable &= JavaPoetUtils.isWriteable(column.property());
            var setter = column.setter();
            var settable = setter != null;
            if (!settable) {
                messager.printMessage(OTHER, "column no settable, " + column.name + ", " + bean.getName());
            }
            allBuildable &= settable;
        }

        allBuildable &= !allWriteable;

        jpaColumnsClass.addSuperinterface(allWriteable
                ? JavaPoetUtils.readWriteInterface(beanClass, typeVariable, null, null)
                : JavaPoetUtils.readInterface(beanClass, typeVariable, null)
        );

        for (var column : columns) {
            columnNames.add(addColumnConstToClassBuilder(jpaColumnsClass, column, className, allWriteable, allBuildable));
        }

        var uniqueNames = new HashSet<>(columnNames);

        var constructor = MethodSpec.constructorBuilder();
        var constructorBody = CodeBlock.builder();

        var pkArgType = TypeName.get(boolean.class);
        var pathArgType = ClassName.get(String.class);
        var nameArgType = ClassName.get(String.class);
        var typeArgType = ParameterizedTypeName.get(ClassName.get(Class.class), typeVariable);

        var pkFieldName = JavaPoetUtils.getUniqueName("pk", uniqueNames);
        var pathFieldName = JavaPoetUtils.getUniqueName("path", uniqueNames);
        var nameFieldName = JavaPoetUtils.getUniqueName("name", uniqueNames);
        var typeFieldName = JavaPoetUtils.getUniqueName("type", uniqueNames);

        var getterFieldName = JavaPoetUtils.getUniqueName("getter", uniqueNames);
        var setterFieldName = JavaPoetUtils.getUniqueName("setter", uniqueNames);
        var builderTypeFieldName = JavaPoetUtils.getUniqueName("builderType", uniqueNames);
        var builderSetterFieldName = JavaPoetUtils.getUniqueName("builderSetter", uniqueNames);

        var getterType = JavaPoetUtils.getFunctionType(beanClass, typeVariable, null);
        var setterType = JavaPoetUtils.getBiConsumerType(beanClass, typeVariable, null);
        var builderType = getParametrizedBuilderType(builderInfo);
        var builderSetterType = JavaPoetUtils.getBiConsumerType(builderType, typeVariable, null);

        constructor
                .addParameter(pkArgType, "pk")
                .addParameter(pathArgType, "path");
        constructorBody
                .addStatement("this." + pkFieldName + " = " + "pk")
                .addStatement("this." + pathFieldName + " = " + "path");

        JavaPoetUtils.populateConstructor(constructor, constructorBody, nameArgType, nameFieldName, typeArgType, typeFieldName);

        constructor.addParameter(getterType, "getter");
        constructorBody.addStatement("this." + getterFieldName + " = " + "getter");

        if (allWriteable) {
            constructor.addParameter(setterType, "setter");
            constructor.addStatement("this." + setterFieldName + " = " + "setter");
        }
        if (allBuildable) {
            jpaColumnsClass.addField(FieldSpec.builder(JavaPoetUtils.typeClassOf(builderType), builderTypeFieldName)
                    .addModifiers(PUBLIC, FINAL).build());

            constructor.addParameter(JavaPoetUtils.typeClassOf(builderType), "builderType");
            constructor.addStatement("this." + builderTypeFieldName + " = " + "builderType");

            constructor.addParameter(builderSetterType, "builderSetter");
            constructor.addStatement("this." + builderSetterFieldName + " = " + "builderSetter");
        }

        jpaColumnsClass.addMethod(constructor.addCode(constructorBody.build()).build());

        JavaPoetUtils.addFieldWithReadAccessor(jpaColumnsClass, pkFieldName, pkArgType, "pk", false);
        JavaPoetUtils.addFieldWithReadAccessor(jpaColumnsClass, pathFieldName, pathArgType, "path", false);

        JavaPoetUtils.populateTypeAwareClass(jpaColumnsClass, nameFieldName, typeFieldName, nameArgType, typeArgType);

        JavaPoetUtils.addGetter(jpaColumnsClass, beanClass, typeVariable, getterType, getterFieldName, null);
        if (allWriteable) {
            JavaPoetUtils.addSetter(jpaColumnsClass, beanClass, typeVariable, setterType, setterFieldName, "set", "bean", true, null);
        }
        if (allBuildable) {
            var typeGetter = MethodSpec.methodBuilder("builderType")
                    .addModifiers(PUBLIC, FINAL)
                    .returns(JavaPoetUtils.typeClassOf(builderType))
                    .addCode(CodeBlock.builder()
                            .addStatement("return " + builderTypeFieldName)
                            .build());
            jpaColumnsClass.addMethod(typeGetter.build());
            JavaPoetUtils.addSetter(jpaColumnsClass, builderType, typeVariable, builderSetterType, builderSetterFieldName, "apply", "builder", false, null);
        }

        JavaPoetUtils.addValues(jpaColumnsClass, className, columnNames, /*allBuildable ? 2 : */1, uniqueNames);

        classBuilder.addType(jpaColumnsClass.build());
        return classBuilder;
    }

    @Builder(toBuilder = true)
    public record Column(String name, boolean pk, List<MetaBean.Property> path, BeanBuilder beanBuilder,
                         Setter setter) {
        public MetaBean.Property property() {
            return path.get(0);
        }

    }
}
