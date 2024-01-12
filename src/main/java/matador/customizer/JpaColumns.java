package matador.customizer;

import io.jbock.javapoet.*;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import matador.MetaBean;
import matador.MetaBeanExtractor;
import matador.MetaCustomizer;
import matador.ReadWrite;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.util.*;

import static io.jbock.javapoet.MethodSpec.constructorBuilder;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Character.*;
import static java.util.Collections.reverse;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static javax.lang.model.element.Modifier.*;
import static matador.JavaPoetUtils.*;

@RequiredArgsConstructor
public class JpaColumns implements MetaCustomizer<TypeSpec.Builder> {

    public static final String OPT_CLASS_NAME = "className";
    public static final String OPT_GET_SUPERCLASS_COLUMNS = "getSuperclassColumns";
    public static final String OPT_CHECK_FOR_ENTITY_ANNOTATION = "checkForEntityAnnotation";
    public static final String[] DEFAULT_GET_SUPERCLASS_COLUMNS = new String[]{TRUE.toString()};
    public static final String[] DEFAULT_CHECK_FOR_ENTITY_ANNOTATION = new String[]{FALSE.toString()};
    public static final String[] DEFAULT_CLASS_NAME = new String[]{"JpaColumn"};

    private final String className;
    private final boolean getSuperclassColumns;
    private final boolean checkForEntityAnnotation;

    public JpaColumns(Map<String, String[]> opts) {
        opts = opts != null ? opts : Map.of();
        this.className = opts.getOrDefault(OPT_CLASS_NAME, DEFAULT_CLASS_NAME)[0];
        this.getSuperclassColumns = opts.getOrDefault(
                OPT_GET_SUPERCLASS_COLUMNS, DEFAULT_GET_SUPERCLASS_COLUMNS
        )[0].equals(TRUE.toString());
        this.checkForEntityAnnotation = opts.getOrDefault(
                OPT_CHECK_FOR_ENTITY_ANNOTATION, DEFAULT_CHECK_FOR_ENTITY_ANNOTATION
        )[0].equals(TRUE.toString());
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
        if (value instanceof AnnotationMirror annotationMirror) {
            return evalValues(annotationMirror.getElementValues());
        } else if (value instanceof Collection<?> collection) {
            return collection.stream().map(JpaColumns::evalValue).toList();
        } else {
            return value;
        }
    }

    private static Map<String, String> getColumnOverrides(Map<String, Object> attributeOverrides) {
        return (
                attributeOverrides != null ? attributeOverrides.get("value") : null
        ) instanceof Collection<?> values ? values.stream().map(v -> v instanceof Map<?, ?> m ? m : null
        ).filter(Objects::nonNull).map(m -> {
            if (m.get("column") instanceof Map<?, ?> mc) {
                Object name = m.get("name");
                Object colName = mc.get("name");
                return name != null && colName != null ? Map.entry(name.toString(), colName.toString()) : null;
            }
            return null;
        }).filter(Objects::nonNull).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)) : Map.of();
    }

    private static String addColumnConst(Column column, ClassName className, TypeSpec.Builder jpaColumnsClass) {
        final CodeBlock getterArgCode;
        final CodeBlock setterArgCode;
        final TypeName type;

        var path = column.path();
        var straightPath = new ArrayList<>(path);
        reverse(straightPath);

        var pathAmount = path.size();
        if (pathAmount > 1) {
            var uniqueNames = new HashSet<String>();

            var valueParam = getUniqueName("value", uniqueNames);
            var beanParam = getUniqueName("bean", uniqueNames);

            var callableName = getUniqueName("bean", uniqueNames);
            type = TypeName.get(path.get(0).getEvaluatedType());

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
                            "v", callableName, publicField, field,
                            getter, setter, uniqueNames
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
            var property = path.get(0);
            type = TypeName.get(property.getEvaluatedType());

            var uniqueNames = new HashSet<String>();
            var beanParamName = getUniqueName("bean", uniqueNames);
            var valueParamName = getUniqueName("value", uniqueNames);

            var publicField = property.isPublicField();
            var field = property.getField();
            var record = property.getRecordComponent();
            getterArgCode = getGetterCallCode(beanParamName, publicField, field, record, property.getGetter());
            setterArgCode = getSetterCallCode(
                    beanParamName, valueParamName, publicField, field, record, property.getSetter()
            );
        }

        var fieldType = ParameterizedTypeName.get(className, getUnboxedTypeVarName(type));
        var name = column.name();
        var strPath = straightPath.stream().map(MetaBean.Property::getName).reduce((l, r) -> l + "." + r).orElse("");

        var enumConstructorArgs = CodeBlock.builder().add(" " + column.pk() + ", ").add("\"" + strPath + "\", ").add(
                enumConstructorArgs(name, dotClass(type), getterArgCode, setterArgCode)
        ).build();

        jpaColumnsClass.addField(FieldSpec.builder(fieldType, name, PUBLIC, STATIC, FINAL)
                .initializer(newInstanceCall(className, enumConstructorArgs))
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

    private static List<Column> getColumns(Messager messager, MetaBean bean, Map<String, String> columnOverrides) {
        var columns = new ArrayList<Column>();
        for (var property : bean.getProperties()) {
            var propAnnotations = getAnnotationElements(property.getAnnotations());
            var _transient = propAnnotations.get("javax.persistence.Transient");
            var embedded = propAnnotations.get("javax.persistence.Embedded");
            var embeddedId = propAnnotations.get("javax.persistence.EmbeddedId");
            var id = propAnnotations.get("javax.persistence.Id");
            var column = propAnnotations.get("javax.persistence.Column");

            if (_transient != null) {
                continue;
            }

            if (embedded != null || embeddedId != null) {
                var embeddedColumns = getEmbeddedColumns(messager, property, getColumnOverrides(
                        propAnnotations.get("javax.persistence.AttributeOverrides"))
                );
                columns.addAll(embeddedId == null ? embeddedColumns
                        : embeddedColumns.stream().map(c -> c.toBuilder().pk(true).build()).toList()
                );
            } else {
                var columnBuilder = Column.builder();
                if (id != null) {
                    columnBuilder.pk(true);
                }
                var name = ofNullable(column).map(c -> c.get("name"))
                        .map(Object::toString)
                        .or(() -> ofNullable(columnOverrides).map(m -> m.get(property.getName())))
                        .orElseGet(() -> toColumnName(property.getName()));
                columnBuilder.name(name);
                columnBuilder.path(List.of(property));
                columns.add(columnBuilder.build());
            }
        }
        return columns;
    }

    private static List<Column> getEmbeddedColumns(
            Messager messager, MetaBean.Property property, Map<String, String> columnOverrides
    ) {
        var type = property.getEvaluatedType();
        if (type instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
            var embeddedBean = new MetaBeanExtractor(messager).getBean(te);
            return getColumns(messager, embeddedBean, columnOverrides).stream().map(embeddedColumn -> {
                var oldPath = embeddedColumn.path();
                var newPath = new ArrayList<MetaBean.Property>();
                if (oldPath != null) {
                    newPath.addAll(oldPath);
                }
                newPath.add(property);
                return embeddedColumn.toBuilder().path(newPath).build();
            }).toList();
        }
        throw new UnsupportedOperationException("unsupported embedded type, property '" + property.getName() + "', type '" + type + "'");
    }

    @Override
    public TypeSpec.Builder customize(Messager messager, MetaBean bean, TypeSpec.Builder out) {
        var beanType = ClassName.get(bean.getType());
        var beanAnnotations = getAnnotationElements(bean.getType().getAnnotationMirrors());
        if (this.checkForEntityAnnotation && beanAnnotations.get("javax.persistence.Entity") != null) {
            return out;
        }
        var className = ClassName.get("", this.className);
        var typeVariable = TypeVariableName.get("T");

        var jpaColumnsClass = typeAwareClass(className, typeVariable)
                .addModifiers(FINAL)
                .addSuperinterface(
                        ParameterizedTypeName.get(ClassName.get(matador.jpa.Column.class), typeVariable)
                )
                .addSuperinterface(
                        ParameterizedTypeName.get(ClassName.get(ReadWrite.class), beanType, typeVariable)
                );

        var columnOverrides = getColumnOverrides(beanAnnotations.get("javax.persistence.AttributeOverrides"));

        var columnNames = new LinkedHashSet<String>();
        var columns = getColumns(messager, bean, columnOverrides);

        if (this.getSuperclassColumns) {
            var parentColumnOverrides = new LinkedHashMap<>(columnOverrides);
            var superclass = bean.getSuperclass();
            while (superclass != null) {
                var superclassAnnotations = getAnnotationElements(superclass.getType().getAnnotationMirrors());
                var superclassColumnOverrides = getColumnOverrides(superclassAnnotations.get("javax.persistence.AttributeOverrides"));
                parentColumnOverrides.putAll(superclassColumnOverrides);
                var superclassColumns = getColumns(messager, superclass, parentColumnOverrides);
                superclass = superclass.getSuperclass();
                columns.addAll(superclassColumns);
            }
        }

        for (var column : columns) {
            columnNames.add(addColumnConst(column, className, jpaColumnsClass));
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

        var getterType = getFunctionType(beanType, typeVariable);
        var setterType = getBiConsumerType(beanType, typeVariable);

        constructor
                .addParameter(pkArgType, "pk")
                .addParameter(pathArgType, "path");
        constructorBody
                .addStatement("this." + pkFieldName + " = " + "pk")
                .addStatement("this." + pathFieldName + " = " + "path");

        populateConstructor(constructor, constructorBody, nameArgType, nameFieldName, typeArgType, typeFieldName);
        constructor
                .addParameter(getterType, "getter")
                .addParameter(setterType, "setter");

        constructorBody
                .addStatement("this." + getterFieldName + " = " + "getter")
                .addStatement("this." + setterFieldName + " = " + "setter");

        jpaColumnsClass.addMethod(constructor.addCode(constructorBody.build()).build());

        addFieldWithReadAccessor(jpaColumnsClass, pkFieldName, pkArgType, "pk", false);
        addFieldWithReadAccessor(jpaColumnsClass, pathFieldName, pathArgType, "path", false);

        populateTypeAwareClass(jpaColumnsClass, nameFieldName, typeFieldName, nameArgType, typeArgType);

        addGetter(jpaColumnsClass, beanType, typeVariable, getterType, getterFieldName);
        addSetter(jpaColumnsClass, beanType, typeVariable, setterType, setterFieldName);

        addValues(jpaColumnsClass, className, columnNames, uniqueNames);

        out.addType(jpaColumnsClass.build());
        return out;
    }

    @Builder(toBuilder = true)
    public record Column(String name, boolean pk, List<MetaBean.Property> path) {
    }
}
