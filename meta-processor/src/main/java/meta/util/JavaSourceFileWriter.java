package meta.util;

import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.JavaFile;
import io.jbock.javapoet.ParameterSpec;
import io.jbock.javapoet.ParameterizedTypeName;
import io.jbock.javapoet.TypeSpec;
import meta.MetaBean;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static io.jbock.javapoet.MethodSpec.methodBuilder;
import static io.jbock.javapoet.TypeSpec.classBuilder;
import static java.util.Arrays.stream;
import static java.util.Optional.ofNullable;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static meta.util.JavaPoetUtils.generatedAnnotation;
import static meta.util.JavaPoetUtils.initMapByEntries;
import static meta.util.JavaPoetUtils.instanceField;
import static meta.util.JavaPoetUtils.mapField;
import static meta.util.JavaPoetUtils.newMetaTypeBuilder;
import static meta.util.MetaCustomizerUtils.instantiate;

public class JavaSourceFileWriter extends AbstractFileWriter<TypeSpec> {

    public JavaSourceFileWriter(ProcessingEnvironment processingEnv, Collection<? extends Element> rootElements) {
        super(processingEnv, rootElements);
    }

    @Override
    protected TypeSpec newClassSpec(MetaBean bean) {
        var builderClass = TypeSpec.Builder.class;
        var customizers = ofNullable(bean.getMeta())
                .stream()
                .flatMap(m -> stream(m.customizers()))
                .map(customizerInfo -> instantiate(customizerInfo, builderClass, getClass().getClassLoader()))
                .filter(Objects::nonNull)
                .toList();
        return newMetaTypeBuilder(processingEnv.getMessager(), bean, customizers).build();
    }

    @Override
    protected TypeSpec newAggregateClassSpec(String typeName, List<String> mapParts) {
        return classBuilder(typeName)
                .addModifiers(PUBLIC, FINAL)
                .addAnnotation(generatedAnnotation())
                .addField(instanceField(typeName))
                .addField(mapField("metas", ClassName.get(Class.class), ClassName.get(MetaModel.class),
                        initMapByEntries(mapParts).build(), PRIVATE, FINAL))
                .addMethod(methodBuilder("of")
                        .addModifiers(PUBLIC)
                        .addParameter(ParameterSpec.builder(ClassName.get(Class.class), "type").build())
                        .returns(MetaModel.class)
                        .addCode(CodeBlock.builder()
                                .addStatement("return metas.get(type)")
                                .build())
                        .build())
                .build();
    }

    @Override
    protected boolean isInheritMetamodel(MetaBean bean, TypeSpec classSpec) {
        return classSpec.superinterfaces.stream().anyMatch(s -> {
            var expected = ClassName.get(MetaModel.class);
            return s instanceof ParameterizedTypeName p ? p.rawType.equals(expected) : s.equals(expected);
        });
    }

    @Override
    protected void writeClassFile(TypeSpec classSpec, String outPackageName, String outClassName) {
        var outFilePath = (outPackageName == null || outPackageName.isEmpty() ? "" : outPackageName + ".") + outClassName;
        try (
                var dest = processingEnv.getFiler().createSourceFile(outFilePath).openWriter();
                var src = JavaFile.builder(outPackageName != null ? outPackageName : "", classSpec)
                        .build().toJavaFileObject().openReader(true);
        ) {
            src.transferTo(dest);
        } catch (IOException e) {
            throw new WriteFileException(e);
        }
    }

}
