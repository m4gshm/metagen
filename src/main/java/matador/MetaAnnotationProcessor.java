package matador;

import io.jbock.javapoet.*;
import lombok.SneakyThrows;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.util.*;

import static io.jbock.javapoet.MethodSpec.methodBuilder;
import static io.jbock.javapoet.TypeSpec.classBuilder;
import static java.util.stream.Collectors.groupingBy;
import static javax.lang.model.SourceVersion.RELEASE_17;
import static javax.lang.model.element.Modifier.*;
import static matador.JavaPoetUtils.initMapByEntries;
import static matador.MetaBeanExtractor.getAggregatorName;

@SupportedAnnotationTypes("matador.Meta")
@SupportedSourceVersion(RELEASE_17)
public class MetaAnnotationProcessor extends AbstractProcessor {

    private static boolean isInheritMetamodel(TypeSpec typeSpec) {
        return typeSpec.superinterfaces.stream().anyMatch(s -> {
            var expected = ClassName.get(MetaModel.class);
            return s instanceof ParameterizedTypeName p ? p.rawType.equals(expected) : s.equals(expected);
        });
    }

    @Override
    @SneakyThrows
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        var elements = roundEnv.getElementsAnnotatedWith(Meta.class);
        var messager = this.processingEnv.getMessager();
        var extractor = new MetaBeanExtractor(messager);
        var beansByPackage = elements.stream()
                .map(e -> e instanceof TypeElement type ? type : null)
                .filter(Objects::nonNull)
                .map(extractor::getBean)
                .filter(Objects::nonNull)
                .collect(groupingBy(MetaBean::getPackageName));

//        record AggregatorParts(String package_, String name, List<String> mapParts) {
//
//        }

//        var aggregators = new HashMap<String, AggregatorParts>();
        var metamodels = new HashMap<String, List<MetaBean>>();
        beansByPackage.forEach((pack, beans) -> {
//            var aggParts = new AggregatorParts(pack, getAggregatorName(pack), new ArrayList<>());
//            aggregators.put(pack, aggParts);
            var packMetamodels = new ArrayList<MetaBean>();
            metamodels.put(pack, packMetamodels);
            for (var bean : beans) {
                var typeSpec = JavaPoetUtils.newTypeBuilder(bean).build();
                if (isInheritMetamodel(typeSpec)) {
                    packMetamodels.add(bean);
                }

                var javaFileObject = JavaFile.builder(bean.getPackageName(), typeSpec).build().toJavaFileObject();
                writeFile(bean.getPackageName(), bean.getName(), javaFileObject);
            }
        });

        metamodels.forEach((pack, beans) -> {
            var aggregate = beans.stream().filter(b -> b.getMeta().aggregate()).toList();
            if (!aggregate.isEmpty()) {
                var mapParts = aggregate.stream().map(bean -> JavaPoetUtils.mapEntry(
                        bean.getType().getQualifiedName().toString() + ".class", "new " + bean.getName() + "()"
                ).toString()).toList();

                var typeName = getAggregatorName(pack);
                var typeSpec = classBuilder(typeName)
                        .addModifiers(PUBLIC, FINAL)
                        .addField(
                                FieldSpec.builder(ClassName.get("", typeName), "instance", PUBLIC, STATIC, FINAL)
                                        .initializer(
                                                CodeBlock.builder()
                                                        .addStatement("new " + typeName + "()")
                                                        .build()
                                        )
                                        .build()
                        )
                        .addField(JavaPoetUtils.mapField("metas", ClassName.get(Class.class), ClassName.get(MetaModel.class),
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
                writeFile(pack, typeName, JavaFile.builder(pack, typeSpec).build().toJavaFileObject());
            }
        });

        return true;
    }

    @SneakyThrows
    private void writeFile(String pack, String name, JavaFileObject javaFileObject) {
        var sourceFile = processingEnv.getFiler().createSourceFile((pack == null || pack.isEmpty() ? "" : pack + ".") + name);
        try (var writer = sourceFile.openWriter();
             var out = new PrintWriter(writer);
             var reader = javaFileObject.openReader(true)) {
            reader.transferTo(out);
        }
    }

}
