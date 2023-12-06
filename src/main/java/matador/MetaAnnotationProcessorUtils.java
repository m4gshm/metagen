package matador;

import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.TypeSpec;
import lombok.experimental.UtilityClass;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.jbock.javapoet.FieldSpec.builder;
import static io.jbock.javapoet.MethodSpec.constructorBuilder;
import static io.jbock.javapoet.TypeSpec.anonymousClassBuilder;
import static io.jbock.javapoet.TypeSpec.enumBuilder;
import static java.beans.Introspector.decapitalize;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.type.TypeKind.BOOLEAN;

@UtilityClass
public class MetaAnnotationProcessorUtils {

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

    static MetaAnnotationProcessor.TypInfo getTypeInfo(TypeMirror typeMirror) {
        return typeMirror instanceof DeclaredType declaredType
                && declaredType.asElement() instanceof TypeElement typeElement
                && !isObjectType(typeElement) ? new MetaAnnotationProcessor.TypInfo(declaredType, typeElement) : null;
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

    static ClassName classType() {
        return ClassName.get("", "Class<?>");
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

    private static TypeSpec.Builder typesEnumBuilder(String enumName) {
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

    static void populateTypeParameters(TypeSpec.Builder builder, String enumName, List<MetaBean.Param> typeParameters) {
        var typesBuilder = typesEnumBuilder(enumName);
        for (var param : typeParameters) {
            typesBuilder.addEnumConstant(param.getName(), enumConstructor(dotClass(getStrType(param.getType()))));
        }
        if (!typeParameters.isEmpty()) {
            builder.addType(typesBuilder.build());
        }
    }
}
