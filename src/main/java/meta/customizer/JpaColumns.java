package meta.customizer;

import io.jbock.javapoet.*;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import meta.*;
import meta.MetaBean.BeanBuilder;
import meta.MetaBean.BeanBuilder.Setter;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import java.lang.annotation.Retention;
import java.util.*;
import java.util.stream.Stream;

import static io.jbock.javapoet.MethodSpec.constructorBuilder;
import static io.jbock.javapoet.MethodSpec.methodBuilder;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Character.*;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import static java.util.Arrays.stream;
import static java.util.Collections.reverse;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static javax.lang.model.element.Modifier.*;
import static javax.tools.Diagnostic.Kind.OTHER;
import static meta.JavaPoetUtils.*;
import static meta.MetaBeanExtractor.isEquals;

@RequiredArgsConstructor
public class JpaColumns implements MetaCustomizer<TypeSpec.Builder> {

    public static final String OPT_CLASS_NAME = "className";
    public static final String OPT_IMPLEMENTS = "implements";
    public static final String OPT_WITH_SUPERCLASS_COLUMNS = "withSuperclassColumns";
    public static final String OPT_CHECK_FOR_ENTITY_ANNOTATION = "checkForEntityAnnotation";
    public static final String[] DEFAULT_WITH_SUPERCLASS_COLUMNS = new String[]{TRUE.toString()};
    public static final String[] DEFAULT_CHECK_FOR_ENTITY_ANNOTATION = new String[]{FALSE.toString()};
    public static final String[] DEFAULT_CLASS_NAME = new String[]{"JpaColumn"};
    public static final Class[] DEFAULT_IMPLEMENTS = new Class[]{meta.jpa.Column.class};
    private final String className;
    private final boolean withSuperclassColumns;
    private final boolean checkForEntityAnnotation;
    private final List<Class> implementInterfaces;

    public JpaColumns(Map<String, String[]> opts) {
        opts = opts != null ? opts : Map.of();
        this.className = opts.getOrDefault(OPT_CLASS_NAME, DEFAULT_CLASS_NAME)[0];
        this.withSuperclassColumns = opts.getOrDefault(
                OPT_WITH_SUPERCLASS_COLUMNS, DEFAULT_WITH_SUPERCLASS_COLUMNS
        )[0].equals(TRUE.toString());
        this.checkForEntityAnnotation = opts.getOrDefault(
                OPT_CHECK_FOR_ENTITY_ANNOTATION, DEFAULT_CHECK_FOR_ENTITY_ANNOTATION
        )[0].equals(TRUE.toString());

        var impls = Stream.ofNullable(opts.get(OPT_IMPLEMENTS)).flatMap(Arrays::stream)
                .map(fullClassName -> (Class) ClassLoadUtility.load(fullClassName)).toList();
        this.implementInterfaces = impls.isEmpty() ? stream(DEFAULT_IMPLEMENTS).toList() : impls;
    }

    private static Map<String, Map<String, Object>> getAnnotationElements(List<? extends AnnotationMirror> annotations) {
        return annotations.stream().collect(toMap(JpaColumns::evalName, a -> evalValues(a.getElementValues()), (l, r) -> l));
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
                ? collection.stream().map(JpaColumns::evalValue).toList()
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

    private static String addColumnConst(Column column, ClassName className, TypeSpec.Builder jpaColumnsClass,
                                         boolean addSetter, boolean allBuildable) {
        final CodeBlock getterArgCode, setterArgCode;
        var type = TypeName.get(column.property().getEvaluatedType());
        var beanBuilder = column.beanBuilder();
        var builderType = beanBuilder != null ? beanBuilder.getType() : null;
        var parametrizedBuilderType = beanBuilder != null ? wildcardParametrized(beanBuilder.getType()) : null;

        var path = column.path();
        var straightPath = new ArrayList<>(path);
        reverse(straightPath);

        var pathAmount = straightPath.size();
        if (pathAmount > 1) {
            var uniqueNames = new HashSet<String>();

            var valueParam = getUniqueName("value", uniqueNames);
            var beanParam = getUniqueName("bean", uniqueNames);
            var callableName = getUniqueName("bean", uniqueNames);

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
                var getterPartCode = requireNonNull(getGetterCallCode(
                        callableName, publicField, field, record, getter
                ), () -> "No public read accessor part '" + propertyName + "' for column '" + column.name() + "'");
                getterArgCodeB.add(".map(").add(getterPartCode).add(")");
                if (i == pathAmount - 1) {
                    var propParam = getUniqueName("prop", uniqueNames);
                    var setCall = setter != null
                            ? CodeBlock.builder().add("$L($L)", setter.getSimpleName(), valueParam).build()
                            : (field != null && publicField)
                            ? CodeBlock.builder().add("$L.$L = $L", propParam, field.getSimpleName(), valueParam).build()
                            : null;
                    requireNonNull(setCall, () -> "no public write accessor part '" + propertyName + "' for column '" + column.name() + "'");

                    setterArgCodeB.add(".ifPresent($L -> $L.$L)", propParam, propParam, setCall);
                } else {
//                    var code = getGetterCallCode(callableName, publicField, field, record, getter);
                    var code = getGetterCallInitByNewCode(
                            "v", callableName, publicField, field, getter, setter, uniqueNames
                    );
                    var intermediateGetterPartCode = requireNonNull(code, () -> "No public read accessor part '" +
                            propertyName + "' for column '" + column.name() + "'"
                    );
                    setterArgCodeB.add(".map(").add(intermediateGetterPartCode).add(")");
                }
                callableName = getUniqueName(propertyName, uniqueNames);
            }

            getterArgCode = CodeBlock.builder().add("$L -> $T.of($L)$L.orElse(null)", beanParam,
                    Optional.class, beanParam, getterArgCodeB.build()).build();
            setterArgCode = CodeBlock.builder().add("($L, $L) -> $T.of($L)$L", beanParam, valueParam,
                    Optional.class, beanParam, setterArgCodeB.build()).build();
        } else {
            var property = column.property();

            var uniqueNames = new HashSet<String>();
            var beanParamName = getUniqueName("bean", uniqueNames);
            var valueParamName = getUniqueName("value", uniqueNames);

            var publicField = property.isPublicField();
            var field = property.getField();
            var record = property.getRecordComponent();
            getterArgCode = getGetterCallCode(beanParamName, publicField, field, record, property.getGetter());
            setterArgCode = getSetterCallCode(beanParamName, valueParamName, publicField, field, record, property.getSetter());
        }

        var fieldType = allBuildable
                ? ParameterizedTypeName.get(className, unboxedTypeVarName(type), parametrizedBuilderType)
                : ParameterizedTypeName.get(className, unboxedTypeVarName(type));

        var name = column.name();
        var strPath = straightPath.stream().map(MetaBean.Property::getName).reduce((l, r) -> l + "." + r).orElse("");
        var uniqueNames = new HashSet<String>();

        var builderParamName = getUniqueName("builder", uniqueNames);
        var valueParamName = getUniqueName("value", uniqueNames);

        var constructorArgs = enumConstructorArgs(name, dotClass(type), getterArgCode, addSetter ? setterArgCode : null);
        if (allBuildable) {
            constructorArgs.add(", $L", dotClass(ClassName.get(builderType)));

            var setter = column.setter();
            var builderSetter = getSetterCallCode(
                    builderParamName, valueParamName, false, null, null, setter.getSetter()
            );
            constructorArgs.add(", ").add(builderSetter);
        }

        var enumConstructorArgs = CodeBlock.builder().add(" " + column.pk() + ", ").add("\"" + strPath + "\", ").add(
                constructorArgs.build()
        ).build();

        jpaColumnsClass.addField(FieldSpec.builder(fieldType, name, PUBLIC, STATIC, FINAL)
                .initializer(allBuildable
                        ? newInstanceCall(className, unboxedTypeVarName(type), parametrizedBuilderType, enumConstructorArgs)
                        : newInstanceCall(className, unboxedTypeVarName(type), enumConstructorArgs)
                )
                .build());

        return name;
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
            builder.append(toUpperCase(current));
        }

        return builder.toString();
    }

    private static List<Column> getColumns(Messager messager, MetaBean bean, Map<String, String> columnOverrides, BeanBuilder builderInfo) {
        var builderSetters = builderInfo != null ? builderInfo.getSetters() : List.<Setter>of();
        var setters = builderInfo != null
                ? builderSetters.stream().collect(toMap(Setter::getName, s -> s))
                : Map.<String, Setter>of();

        return bean.getProperties().stream().filter(property -> !isExcluded(property)).flatMap(property -> {
            var name = property.getName();
            var type = property.getEvaluatedType();

            var possibleBuilderSetter = setters.get(name);
            if (possibleBuilderSetter != null && !isEquals(possibleBuilderSetter.getEvaluatedType(), type)) {
                possibleBuilderSetter = null;
            }

            var annotations = getAnnotationElements(property.getAnnotations());
            var _transient = getJpaAnnotation(annotations, "javax.persistence.Transient", "jakarta.persistence.Transient");
            var embedded = getJpaAnnotation(annotations, "javax.persistence.Embedded", "jakarta.persistence.Embedded");
            var embeddedId = getJpaAnnotation(annotations, "javax.persistence.EmbeddedId", "jakarta.persistence.EmbeddedId");

            if (_transient != null) {
                return Stream.empty();
            } else if (embedded != null || embeddedId != null) {
                var embeddedColumns = getEmbeddedColumns(messager, property, getColumnOverrides(
                        getJpaAnnotation(annotations, "javax.persistence.AttributeOverrides", "jakarta.persistence.AttributeOverrides")
                ));
                return embeddedId == null ? embeddedColumns.stream()
                        : embeddedColumns.stream().map(c -> c.toBuilder().pk(true).build());
            } else {
                return Stream.of(newColumn(
                        getJpaAnnotation(annotations, "javax.persistence.Id", "jakarta.persistence.Id") != null,
                        property, builderInfo, possibleBuilderSetter,
                        getJpaAnnotation(annotations, "javax.persistence.Column", "jakarta.persistence.Column"),
                        columnOverrides));
            }
        }).toList();
    }

    private static Map<String, Object> getJpaAnnotation(Map<String, Map<String, Object>> annotations, String javax, String jakarta) {
        return ofNullable(annotations.get(javax)).orElse(annotations.get(jakarta));
    }

    private static boolean isExcluded(MetaBean.Property property) {
        return property.isExcluded() || property.getAnnotation(JpaColumns.Exclude.class) != null;
    }

    private static Column newColumn(boolean pk, MetaBean.Property property,
                                    BeanBuilder builder, Setter setter,
                                    Map<String, Object> columnAttributes,
                                    Map<String, String> columnOverrides) {
        return Column.builder()
                .pk(pk).setter(setter).beanBuilder(builder)
                .name(getColumnName(property.getName(), columnAttributes, columnOverrides))
                .path(List.of(property))
                .build();
    }

    private static String getColumnName(String propertyName,
                                        Map<String, Object> columnAttributes,
                                        Map<String, String> columnOverrides) {
        return ofNullable(columnAttributes).map(c -> c.get("name"))
                .map(Object::toString)
                .or(() -> ofNullable(columnOverrides).map(m -> m.get(propertyName)))
                .orElseGet(() -> toColumnName(propertyName));
    }

    private static List<Column> getEmbeddedColumns(
            Messager messager, MetaBean.Property property, Map<String, String> columnOverrides
    ) {
        return ofNullable(property.getBean()).map(embeddedBean -> getColumns(messager, embeddedBean, columnOverrides, embeddedBean.getBeanBuilderInfo())
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
    public TypeSpec.Builder customize(Messager messager, MetaBean bean, TypeSpec.Builder out) {
        var beanType = bean.getType();
        var beanClass = ClassName.get(beanType);
        var beanAnnotations = getAnnotationElements(beanType.getAnnotationMirrors());
        if (this.checkForEntityAnnotation && getJpaAnnotation(beanAnnotations, "javax.persistence.Entity", "jakarta.persistence.Entity") != null) {
            return out;
        }
        var className = ClassName.get("", this.className);
        var typeVariable = TypeVariableName.get("T");
        var builderTypeVariable = TypeVariableName.get("B");

        var jpaColumnsClass = typeAwareClass(className, typeVariable).addModifiers(FINAL);

        implementInterfaces.stream().map(iface -> ParameterizedTypeName.get(ClassName.get(iface), typeVariable))
                .forEach(jpaColumnsClass::addSuperinterface);

        var columnOverrides = getColumnOverrides(getJpaAnnotation(beanAnnotations, "javax.persistence.AttributeOverrides", "jakarta.persistence.AttributeOverrides"));

        var columnNames = new LinkedHashSet<String>();
        var builderInfo = bean.getBeanBuilderInfo();
        var columns = new ArrayList<>(getColumns(messager, bean, columnOverrides, builderInfo));

        if (this.withSuperclassColumns) {
            var parentColumnOverrides = new LinkedHashMap<>(columnOverrides);
            var superclass = bean.getSuperclass();
            while (superclass != null) {
                var superclassAnnotations = getAnnotationElements(superclass.getType().getAnnotationMirrors());
                var superclassColumnOverrides = getColumnOverrides(getJpaAnnotation(superclassAnnotations, "javax.persistence.AttributeOverrides", "jakarta.persistence.AttributeOverrides"));
                parentColumnOverrides.putAll(superclassColumnOverrides);
                var superclassColumns = getColumns(messager, superclass, parentColumnOverrides, builderInfo);
                superclass = superclass.getSuperclass();
                columns.addAll(superclassColumns);
            }
        }

        var allWriteable = true;
        var allBuildable = true;
        for (var column : columns) {
            allWriteable &= isWriteable(column.property());
            var setter = column.setter();
            var settable = setter != null;
            if (!settable) {
                messager.printMessage(OTHER, "column no settable, " + column.name + ", " + bean.getName());
            }
            allBuildable &= settable;
        }

        allBuildable &= !allWriteable;

        if (allBuildable) {
            jpaColumnsClass.addTypeVariable(builderTypeVariable);
        }
        jpaColumnsClass.addSuperinterface(allWriteable
                ? ParameterizedTypeName.get(ClassName.get(ReadWrite.class), beanClass, typeVariable)
                : ParameterizedTypeName.get(ClassName.get(Read.class), beanClass, typeVariable));

        for (var column : columns) {
            columnNames.add(addColumnConst(column, className, jpaColumnsClass, allWriteable, allBuildable));
        }

        var uniqueNames = new HashSet<>(columnNames);

        var constructor = constructorBuilder();
        var constructorBody = CodeBlock.builder();

        var pkArgType = TypeName.get(boolean.class);
        var pathArgType = ClassName.get(String.class);
        var nameArgType = ClassName.get(String.class);
        var typeArgType = ParameterizedTypeName.get(ClassName.get(Class.class), typeVariable);

        var pkFieldName = getUniqueName("pk", uniqueNames);
        var pathFieldName = getUniqueName("path", uniqueNames);
        var nameFieldName = getUniqueName("name", uniqueNames);
        var typeFieldName = getUniqueName("type", uniqueNames);

        var getterFieldName = getUniqueName("getter", uniqueNames);
        var setterFieldName = getUniqueName("setter", uniqueNames);
        var builderTypeFieldName = getUniqueName("builderType", uniqueNames);
        var builderSetterFieldName = getUniqueName("builderSetter", uniqueNames);

        var getterType = getFunctionType(beanClass, typeVariable);
        var setterType = getBiConsumerType(beanClass, typeVariable);
        var builderType = builderTypeVariable;
        var builderSetterType = getBiConsumerType(builderTypeVariable, typeVariable);

        constructor
                .addParameter(pkArgType, "pk")
                .addParameter(pathArgType, "path");
        constructorBody
                .addStatement("this." + pkFieldName + " = " + "pk")
                .addStatement("this." + pathFieldName + " = " + "path");

        populateConstructor(constructor, constructorBody, nameArgType, nameFieldName, typeArgType, typeFieldName);

        constructor.addParameter(getterType, "getter");
        constructorBody.addStatement("this." + getterFieldName + " = " + "getter");

        if (allWriteable) {
            constructor.addParameter(setterType, "setter");
            constructor.addStatement("this." + setterFieldName + " = " + "setter");
        }
        if (allBuildable) {
            jpaColumnsClass.addField(FieldSpec.builder(typeClassOf(builderType), builderTypeFieldName)
                    .addModifiers(PUBLIC, FINAL).build());

            constructor.addParameter(typeClassOf(builderType), "builderType");
            constructor.addStatement("this." + builderTypeFieldName + " = " + "builderType");

            constructor.addParameter(builderSetterType, "builderSetter");
            constructor.addStatement("this." + builderSetterFieldName + " = " + "builderSetter");
        }

        jpaColumnsClass.addMethod(constructor.addCode(constructorBody.build()).build());

        addFieldWithReadAccessor(jpaColumnsClass, pkFieldName, pkArgType, "pk", false);
        addFieldWithReadAccessor(jpaColumnsClass, pathFieldName, pathArgType, "path", false);

        populateTypeAwareClass(jpaColumnsClass, nameFieldName, typeFieldName, nameArgType, typeArgType);

        addGetter(jpaColumnsClass, beanClass, typeVariable, getterType, getterFieldName);
        if (allWriteable) {
            addSetter(jpaColumnsClass, beanClass, typeVariable, setterType, setterFieldName, "set", "bean", true);
        }
        if (allBuildable) {
            var typeGetter = methodBuilder("builderType")
                    .addModifiers(PUBLIC, FINAL)
                    .returns(typeClassOf(builderType))
                    .addCode(CodeBlock.builder()
                            .addStatement("return " + builderTypeFieldName)
                            .build());
            jpaColumnsClass.addMethod(typeGetter.build());
            addSetter(jpaColumnsClass, builderTypeVariable, typeVariable, builderSetterType, builderSetterFieldName, "apply", "builder", false);
        }

        addValues(jpaColumnsClass, className, columnNames, allBuildable ? 2 : 1, uniqueNames);

        out.addType(jpaColumnsClass.build());
        return out;
    }

    @Retention(SOURCE)
    public @interface Exclude {

    }

    @Builder(toBuilder = true)
    public record Column(String name, boolean pk, List<MetaBean.Property> path, BeanBuilder beanBuilder,
                         Setter setter) {
        public MetaBean.Property property() {
            return path.get(0);
        }

    }
}
