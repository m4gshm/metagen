package meta.util;

import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.JavaFile;
import io.jbock.javapoet.ParameterSpec;
import io.jbock.javapoet.ParameterizedTypeName;
import io.jbock.javapoet.TypeSpec;
import meta.MetaCustomizer;
import meta.MetaModel;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

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

public class JavaSourceFileWriter extends AbstractFileWriter<TypeSpec> {

    public JavaSourceFileWriter(ProcessingEnvironment processingEnv, Collection<? extends Element> rootElements) {
        super(processingEnv, rootElements);
    }

    @Override
    protected JavaFileObject toJavaFileObject(String pack, TypeSpec typeSpec) {
        return JavaFile.builder(pack, typeSpec).build().toJavaFileObject();
    }

    @Override
    protected TypeSpec newClassSpec(MetaBean bean) {
        return newMetaTypeBuilder(processingEnv.getMessager(), bean, ofNullable(bean.getMeta()).stream()
                .flatMap(m -> stream(m.customizers()))
                .map(MetaCustomizer::instantiate)
                .toList()).build();
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
    protected JavaFileObject createOutputFile(String outFilePath) {
        try {
            return processingEnv.getFiler().createSourceFile(outFilePath);
        } catch (IOException e) {
            throw new CreateFileException(e);
        }
    }

}
