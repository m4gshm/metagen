package metagen;

import lombok.RequiredArgsConstructor;
import metagen.MetaBean.BeanBuilder;

import javax.annotation.processing.Messager;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import java.util.*;

import static java.beans.Introspector.decapitalize;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.BOOLEAN;
import static javax.tools.Diagnostic.Kind.WARNING;

@RequiredArgsConstructor
public class MetaBeanExtractor {

    public static final String METAS = "Metas";

    private final Messager messager;

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

    static TypeElement getExternalClass(TypeElement type) {
        var enclosingElement = type.getEnclosingElement();
        return enclosingElement instanceof TypeElement te ? te : null;
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

    static void updateType(MetaBean.Property property, TypeMirror propType,
                           List<MetaBean.Param> beanParameters,
                           List<? extends AnnotationMirror> annotations) {
        var existType = property.getType();
        if (existType == null) {
            property.setType(propType);
            var evaluatedType = evalType(propType, beanParameters);
            property.setEvaluatedType(evaluatedType);
        }
        var propAnnotations = property.getAnnotations();
        if (propAnnotations == null) {
            propAnnotations = new ArrayList<>(annotations);
        } else {
            propAnnotations.addAll(annotations);
        }
        property.setAnnotations(propAnnotations);
    }

    static boolean isObjectType(TypeElement type) {
        return "java.lang.Object".equals(type.getQualifiedName().toString());
    }

    static TypeMirror evalType(TypeMirror type) {
        return evalType(type, List.of());
    }

    static TypeMirror evalType(TypeMirror type, List<MetaBean.Param> beanParameters) {
        return type instanceof TypeVariable typeVariable ? evalType(typeVariable, beanParameters)
                : type instanceof IntersectionType intersectionType ? evalType(intersectionType, beanParameters)
//                : type instanceof DeclaredType dt ? dt.asElement().asType()
                : type instanceof DeclaredType || type instanceof ArrayType || type instanceof PrimitiveType ? type : null;
    }

    private static TypeMirror evalType(IntersectionType intersectionType, List<MetaBean.Param> beanParameters) {
        return evalType(intersectionType.getBounds().get(0), beanParameters);
    }

    private static TypeMirror evalType(TypeVariable typeVariable, List<MetaBean.Param> beanParameters) {
        var collect = beanParameters != null
                ? beanParameters.stream().collect(toMap(p -> p.getName().asType(), MetaBean.Param::getType))
                : Map.<TypeMirror, TypeMirror>of();
        var type = collect.get(typeVariable);
        if (type != null && !isEquals(type, typeVariable)) {
            return evalType(type, beanParameters);
        } else {
            return evalType(typeVariable.getUpperBound(), beanParameters);
        }
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
                            && isEquals(beanType, e.getEnclosingElement())
                    ).findFirst().orElse(null);

            if (builderType == null) {
                messager.printMessage(WARNING, "cannot determine builder class '" + builderClassName + "'", beanType);
                return null;
            } else if (builderType.asType().getKind() == TypeKind.ERROR) {
                messager.printMessage(WARNING, "invalid builder class '" + builderClassName + "'", beanType);
                return null;
            } else {

                var builderMethodName = getAnnotationValue("builderMethodName", values, defaultValues);
                var buildMethodName = getAnnotationValue("buildMethodName", values, defaultValues);
                var setterPrefix = getAnnotationValue("setterPrefix", values, defaultValues);

//                messager.printMessage(NOTE, "detect builder class '" + builderClassName +
//                        "', annotation '" + builderAnnotation + "', for '" + beanType + "'");

                var setters = getBuilderSetters(messager, builderType, isInheritor ? superBuilder : null);

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

    public static boolean isEquals(Element element1, Element element2) {
        return element1 != null && element1.equals(element2) || element2 == null;
    }

    public static boolean isEquals(TypeMirror type1, TypeMirror type2) {
        return type1 instanceof DeclaredType dt1 && type2 instanceof DeclaredType dt2
                ? isEquals(dt1.asElement(), dt2.asElement()) && isListEquals(dt1.getTypeArguments(), dt2.getTypeArguments())
                : type1 != null && type1.equals(type2) || type2 == null;
    }

    private static boolean isListEquals(List<? extends TypeMirror> ta1, List<? extends TypeMirror> ta2) {
        if (ta1 != null && ta2 != null) {
            if (ta1.size() != ta2.size()) {
                return false;
            }
            for (int i = 0; i < ta1.size(); i++) {
                if (!isEquals(ta1.get(i), ta2.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return ta1 == ta2;
    }

    private static ArrayList<BeanBuilder.Setter> getBuilderSetters(
            Messager messager, TypeElement typeElement, BeanBuilder superBuilder) {
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
                    var returnSame = isEquals(builderType, returnType);
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
                    if (dt.getKind() == TypeKind.ERROR) {
                        //actual to the Lombok @SuperBuilder
                        messager.printMessage(WARNING, "builder superclass is invalid, builder '" + element +
                                "', superlcass '" + te + "', superbuilder info " + superBuilder);
                        if (superBuilder != null) {
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
            if (isEquals(typeVariable, typeParameter)) {
                return i;
            }
        }
        return -1;
    }

    private static String getAnnotationValue(String attributeName, Map<String, Object> values, Map<String, Object> defaultValues) {
        return ofNullable(values.getOrDefault(attributeName, defaultValues.get(attributeName))).map(Object::toString).orElse("");
    }

    public MetaBean getBean(TypeElement type) {
        return getBean(type, null, type.getAnnotation(Meta.class));
    }

    private MetaBean getBean(TypeElement type, DeclaredType declaredType, Meta meta) {
        if (type == null || isObjectType(type)) {
            return null;
        }

        var typeParameters = extractGenericParams(type, declaredType);

        var isRecord = type.getRecordComponents() != null;

        var properties = new LinkedHashMap<String, MetaBean.Property>();
//        var nestedTypes = new LinkedHashMap<String, MetaBean>();
        var nestedTypes = new ArrayList<TypeElement>();
        var recordComponents = type.getRecordComponents();
        if (recordComponents != null) {
            for (var recordComponent : recordComponents) {
                var recordName = recordComponent.getSimpleName();
                var propType = recordComponent.asType();
                var annotationMirrors = recordComponent.getAnnotationMirrors();
                var property = getProperty(properties, recordName.toString());
                property.setRecordComponent(recordComponent);
                updateType(property, propType, typeParameters, annotationMirrors);
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

                    var property = getProperty(properties, propName);
                    if (setter) {
                        property.setSetter(ee);
                    }
                    if (getter || boolGetter) {
                        property.setGetter(ee);
                    }
                    updateType(property, propType, typeParameters, annotationMirrors);
                }
            } else if (!isStatic && enclosedElement instanceof VariableElement ve) {
                var propType = ve.asType();
                var propName = ve.getSimpleName().toString();
                var property = getProperty(properties, propName);
                property.setField(ve);
                property.setPublicField(isPublic);
                updateType(property, propType, typeParameters, annotationMirrors);
            } else if (enclosedElement instanceof TypeElement te) {
                nestedTypes.add(te);
//                var enclosedMeta = enclosedElement.getAnnotation(Meta.class);
//                if (enclosedMeta != null) {
//                    var nestedBean = getBean(te, null, enclosedMeta);
//                    nestedTypes.putIfAbsent(nestedBean.getClassName(), nestedBean);
//                }
            }
        }

        var superBean = ofNullable(getTypeInfo(type.getSuperclass())).map(superclass ->
                getBean(superclass.typeElement, superclass.declaredType, meta)
        ).orElse(null);

        var interfaceBeans = type.getInterfaces().stream().map(MetaBeanExtractor::getTypeInfo)
                .filter(Objects::nonNull).map(iface -> getBean(iface.typeElement, iface.declaredType, meta))
                .toList();

        var name = type.getSimpleName().toString();

        var owner = type.getEnclosingElement();
        var beanPackage = "";
        StringBuilder prefix = new StringBuilder();
        if (owner instanceof PackageElement packageElement) {
            beanPackage = packageElement.getQualifiedName().toString();
        } else if (owner instanceof TypeElement externalClass) {
            prefix = new StringBuilder(externalClass.getSimpleName().toString());
            TypeElement superExternalClass;
            while (null != (superExternalClass = getExternalClass(externalClass))) {
                prefix.insert(0, superExternalClass.getSimpleName().toString());
                externalClass = superExternalClass;
            }

            if (externalClass.getEnclosingElement() instanceof PackageElement packageElement) {
                beanPackage = packageElement.getQualifiedName().toString();
            }
        }

        var suffix = ofNullable(meta).map(Meta::suffix).map(String::trim).filter(m -> !m.isEmpty()).orElse(Meta.META);
        var builder = ofNullable(meta).map(Meta::builder);
        var superBuilderInfo = superBean != null ? superBean.getBeanBuilderInfo() : null;
        var beanBuilder = builder.map(Meta.Builder::detect).orElse(false)
                ? newBeanBuilder(messager, type, typeParameters,
                builder.map(Meta.Builder::className).orElse(Meta.Builder.CLASS_NAME), superBuilderInfo) : null;

        return MetaBean.builder()
                .isRecord(isRecord)
                .type(type)
                .meta(meta)
                .name(prefix + name + suffix)
                .packageName(beanPackage)
                .superclass(superBean)
                .interfaces(interfaceBeans)
                .nestedTypes(nestedTypes)
//                .nestedTypes(new ArrayList<>(nestedTypes.values()))
                .properties(new ArrayList<>(properties.values()))
                .typeParameters(typeParameters)
                .beanBuilderInfo(beanBuilder)
                .build();
    }

    record TypeInfo(DeclaredType declaredType, TypeElement typeElement) {
        public TypeInfo(TypeElement typeElement) {
            this(null, typeElement);
        }
    }
}
