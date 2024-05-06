package meta.util;

import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.JavaFile;
import io.jbock.javapoet.ParameterSpec;
import io.jbock.javapoet.ParameterizedTypeName;
import io.jbock.javapoet.TypeSpec;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import meta.MetaCustomizer;
import meta.MetaModel;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.jbock.javapoet.MethodSpec.methodBuilder;
import static io.jbock.javapoet.TypeSpec.classBuilder;
import static java.util.Arrays.stream;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static meta.util.JavaPoetUtils.dotClass;
import static meta.util.JavaPoetUtils.generatedAnnotation;
import static meta.util.JavaPoetUtils.initMapByEntries;
import static meta.util.JavaPoetUtils.instanceField;
import static meta.util.JavaPoetUtils.mapField;
import static meta.util.JavaPoetUtils.newMetaTypeBuilder;
import static meta.util.MetaBeanExtractor.getAggregatorName;
import static meta.util.MetaBeanExtractor.getPackageClass;

@UtilityClass
public class WriteClassFileUtils {

    public static void writeFiles(ProcessingEnvironment processingEnv,
                                  Collection<? extends Element> rootElements,
                                  Stream<MetaBean> beanStream) {
        var written = writeClassFiles(processingEnv.getMessager(),
                processingEnv.getFiler(), beanStream);
        var metamodels = written.entrySet().stream()
                .filter(entry -> isInheritMetamodel(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(groupingBy(MetaBean::getPackageName));
        var clasNamePerPack = rootElements.stream().collect(groupingBy(
                element -> ofNullable(getPackageClass(element)).map(Object::toString).orElse(""),
                mapping(MetaBeanExtractor::getClassName, toList())
        ));
        writeAggregateFile(metamodels, clasNamePerPack, processingEnv.getFiler());
    }

    private static boolean isInheritMetamodel(TypeSpec typeSpec) {
        return typeSpec.superinterfaces.stream().anyMatch(s -> {
            var expected = ClassName.get(MetaModel.class);
            return s instanceof ParameterizedTypeName p ? p.rawType.equals(expected) : s.equals(expected);
        });
    }

    @SneakyThrows
    private static void writeFile(String pack, String name, JavaFileObject javaFileObject, Filer filer) {
        var sourceFile = filer.createSourceFile((pack == null || pack.isEmpty() ? "" : pack + ".") + name);
        try (
                var out = new PrintWriter(sourceFile.openWriter());
                var reader = javaFileObject.openReader(true);
        ) {
            reader.transferTo(out);
        }
    }

    private static Map<MetaBean, TypeSpec> writeClassFiles(Messager messager, Filer filer, Stream<MetaBean> beanStream) {
        return beanStream.collect(toMap(bean -> bean, bean -> {
            var typeSpec = newMetaTypeBuilder(messager, bean, ofNullable(bean.getMeta()).stream()
                    .flatMap(m -> stream(m.customizers()))
                    .map(MetaCustomizer::instantiate)
                    .toList()).build();
            var packageName = bean.getPackageName();
            writeFile(packageName, bean.getName(), JavaFile.builder(packageName, typeSpec).build().toJavaFileObject(), filer);
            return typeSpec;
        }));
    }

    private static void writeAggregateFile(Map<String, List<MetaBean>> metamodels,
                                           Map<String, List<String>> clasNamePerPack,
                                           Filer filer
    ) {
        metamodels.forEach((pack, beans) -> {
            var aggregate = beans.stream().filter(b -> {
                var meta = b.getMeta();
                return meta == null || meta.aggregate();
            }).toList();
            if (!aggregate.isEmpty()) {
                var mapParts = aggregate.stream().map(bean -> JavaPoetUtils.mapEntry(
                        dotClass(ClassName.get(bean.getType())), "new " + bean.getName() + "()"
                ).toString()).toList();

                var typeName = getAggregatorName(pack, clasNamePerPack);
                var typeSpec = classBuilder(typeName)
                        .addModifiers(PUBLIC, FINAL)
                        .addAnnotation(generatedAnnotation())
                        .addField(instanceField(typeName))
                        .addField(mapField("metas", ClassName.get(Class.class), ClassName.get(MetaModel.class),
                                initMapByEntries(mapParts).build(), PRIVATE, FINAL))
                        .addMethod(methodBuilder("of")
                                .addModifiers(PUBLIC)
                                .addParameter(
                                        ParameterSpec.builder(ClassName.get(Class.class), "type").build()
                                )
                                .returns(MetaModel.class)
                                .addCode(
                                        CodeBlock.builder()
                                                .addStatement("return metas.get(type)")
                                                .build()
                                )
                                .build()
                        )
                        .build();
                writeFile(pack, typeName, JavaFile.builder(pack, typeSpec).build().toJavaFileObject(), filer);
            }
        });
    }
}
