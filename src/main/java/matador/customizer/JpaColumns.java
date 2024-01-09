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
import static java.util.Collections.reverse;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static javax.lang.model.element.Modifier.*;
import static matador.JavaPoetUtils.*;

@RequiredArgsConstructor
public class JpaColumns implements MetaCustomizer<TypeSpec.Builder> {

    public static final String OPT_CLASS_NAME = "className";
    public static final String DEFAULT_CLASS_NAME = "JpaColumn";

    private final String className;

    public JpaColumns(Map<String, String> opts) {
        this((opts != null ? opts : Map.<String, String>of()).getOrDefault(OPT_CLASS_NAME, DEFAULT_CLASS_NAME));
    }

    private static Map<String, Map<String, Object>> getAnnotationElements(
            List<? extends AnnotationMirror> annotations
    ) {
        return annotations.stream().collect(toMap(
                annotationMirror -> evalName(annotationMirror),
                a -> evalValues(a.getElementValues()), (l, r) -> l
        ));
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

    @Override
    public TypeSpec.Builder customize(Messager messager, MetaBean bean, TypeSpec.Builder out) {
        var beanType = ClassName.get(bean.getType());
        var beanAnnotations = getAnnotationElements(bean.getType().getAnnotationMirrors());
        var entity = beanAnnotations.get("javax.persistence.Entity");
        if (entity != null) {
            var columnOverrides = getColumnOverrides(beanAnnotations.get("javax.persistence.AttributeOverrides"));
            var columns = getColumns(messager, bean, columnOverrides);
            var className = ClassName.get("", this.className);
            var typeVariable = TypeVariableName.get("T");
            var jpaColumnsClass = typeAwareClass(className, typeVariable).addModifiers(FINAL);
            var readWriteInterface = ParameterizedTypeName.get(ClassName.get(ReadWrite.class), beanType, typeVariable);
            jpaColumnsClass.addSuperinterface(readWriteInterface);
            var columnNames = new LinkedHashSet<String>();
            for (var column : columns) {
                final CodeBlock getterArgCode;
                final CodeBlock setterArgCode;
                final TypeName type;
                var path = column.path();
                var pathAmount = path.size();
                if (pathAmount > 1) {
                    var uniqueNames = new HashSet<String>();

                    var valueParam = getUniqueName("value", uniqueNames);
                    var beanParam = getUniqueName("bean", uniqueNames);

                    var callableName = getUniqueName("bean", uniqueNames);
                    type = TypeName.get(path.get(0).getType());
                    var straightPath = new ArrayList<>(path);
                    reverse(straightPath);
                    var getterArgCodeB = CodeBlock.builder();
                    var setterArgCodeB = CodeBlock.builder();
                    for (int i = 0; i < pathAmount; i++) {
                        var property = straightPath.get(i);
                        var field = property.getField();
                        var publicField = property.isPublicField();
                        var propertyName = property.getName();
                        var getterPartCode = requireNonNull(getGetterArgCode(
                                callableName, publicField, field,
                                property.getRecordComponent(), property.getGetter()
                        ), () -> "No public read accessor part '" + propertyName + "' for column '" + column.name() + "'");
                        getterArgCodeB.add(".map(").add(getterPartCode).add(")");
                        if (i == pathAmount - 1) {
                            var propParam = getUniqueName("prop", uniqueNames);
                            var setter = property.getSetter();
                            var setCall = setter != null
                                    ? CodeBlock.builder().add("$L($L)", setter.getSimpleName().toString(), valueParam).build().toString()
                                    : (field != null && publicField) ? CodeBlock.builder().add("$L.$L = $L", propParam, field.getSimpleName().toString(), valueParam).build().toString()
                                    : null;
                            requireNonNull(setCall, () -> "No public write accessor part '" + propertyName + "' for column '" + column.name() + "'");

                            setterArgCodeB.add(".ifPresent($L -> $L.$L)", propParam, propParam, setCall);
                        } else {
                            setterArgCodeB.add(".map(").add(getterPartCode).add(")");
                        }
                        callableName = getUniqueName(propertyName, uniqueNames);
                    }

                    getterArgCode = CodeBlock.builder().add("$L -> $T.of($L)$L.orElse(null)", beanParam,
                            Optional.class, beanParam, getterArgCodeB.build()).build();
                    setterArgCode = CodeBlock.builder().add("($L, $L) -> $T.of($L)$L", beanParam, valueParam,
                            Optional.class, beanParam, setterArgCodeB.build()).build();
                } else {
                    var property = path.get(0);
                    type = TypeName.get(property.getType());

                    var uniqueNames = new HashSet<String>();
                    var beanParamName = getUniqueName("bean", uniqueNames);
                    var valueParamName = getUniqueName("value", uniqueNames);

                    var publicField = property.isPublicField();
                    var field = property.getField();
                    var record = property.getRecordComponent();
                    getterArgCode = getGetterArgCode(beanParamName, publicField, field, record, property.getGetter());
                    setterArgCode = getSetterArgCode(
                            beanParamName, valueParamName, publicField, field, record, property.getSetter()
                    );
                }

                var fieldType = ParameterizedTypeName.get(className, getUnboxedTypeVarName(type));
                var name = column.name();

                jpaColumnsClass.addField(FieldSpec.builder(fieldType, name, PUBLIC, STATIC, FINAL)
                        .initializer(newInstanceCall(className, enumConstructorArgs(name, dotClass(type),
                                getterArgCode, setterArgCode)))
                        .build());

                columnNames.add(name);
            }

            var uniqueNames = new HashSet<>(columnNames);

            var constructor = constructorBuilder();
            var constructorBody = CodeBlock.builder();

            var nameArgType = ClassName.get(String.class);
            var typeArgType = ParameterizedTypeName.get(ClassName.get(Class.class), typeVariable);

            var nameFieldName = getUniqueName("name", uniqueNames);
            var typeFieldName = getUniqueName("type", uniqueNames);

            var getterFieldName = getUniqueName("getter", uniqueNames);
            var setterFieldName = getUniqueName("setter", uniqueNames);

            var getterType = getFunctionType(beanType, typeVariable);
            var setterType = getBiConsumerType(beanType, typeVariable);

            populateConstructor(constructor, constructorBody, nameArgType, nameFieldName, typeArgType, typeFieldName);
            constructor
                    .addParameter(getterType, "getter")
                    .addParameter(setterType, "setter");
            constructorBody
                    .addStatement("this." + getterFieldName + " = " + "getter")
                    .addStatement("this." + setterFieldName + " = " + "setter");

            jpaColumnsClass.addMethod(constructor.addCode(constructorBody.build()).build());

            populateTypeAwareClass(jpaColumnsClass, nameFieldName, typeFieldName, nameArgType, typeArgType);

            addGetter(jpaColumnsClass, beanType, typeVariable, getterType, getterFieldName);
            addSetter(jpaColumnsClass, beanType, typeVariable, setterType, setterFieldName);

            addValues(jpaColumnsClass, className, columnNames, uniqueNames);

            out.addType(jpaColumnsClass.build());
        }
        return out;
    }

    private List<Column> getColumns(Messager messager, MetaBean bean, Map<String, String> columnOverrides) {
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
                var embeddedColumns = getEmbeddedColumns(messager, property,
                        getColumnOverrides(propAnnotations.get("javax.persistence.AttributeOverrides")));
                columns.addAll(embeddedId != null
                        ? embeddedColumns
                        : embeddedColumns.stream().map(c -> c.toBuilder().pk(true).build()).toList());
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

    private List<Column> getEmbeddedColumns(Messager messager, MetaBean.Property property, Map<String, String> columnOverrides) {
        if (property.getEvaluatedType() instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
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
        } else {
            //todo log
        }
        return List.of();
    }

    private String toColumnName(String name) {
        //convert camel to snake case
        return name;
    }

    @Builder(toBuilder = true)
    public record Column(String name, boolean pk, List<MetaBean.Property> path) {
        public MetaBean.Property property() {
            if (path != null && !path.isEmpty()) {
                return path.get(0);
            }
            throw new IllegalCallerException("empty path, column '" + name + "'");
        }
    }
}
