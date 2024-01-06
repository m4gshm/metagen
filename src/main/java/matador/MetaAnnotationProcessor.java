package matador;

import io.jbock.javapoet.*;
import lombok.SneakyThrows;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.util.*;

import static io.jbock.javapoet.FieldSpec.builder;
import static io.jbock.javapoet.MethodSpec.methodBuilder;
import static io.jbock.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.SourceVersion.RELEASE_17;
import static javax.lang.model.element.Modifier.*;
import static matador.JavaPoetUtils.initMapByEntries;
import static matador.MetaBeanUtils.getAggregatorName;
import static matador.MetaBeanUtils.getBean;

@SupportedAnnotationTypes("matador.Meta")
@SupportedSourceVersion(RELEASE_17)
public class MetaAnnotationProcessor extends AbstractProcessor {

    @Override
    @SneakyThrows
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        var elements = roundEnv.getElementsAnnotatedWith(Meta.class);
        var beans = elements.stream()
                .map(e -> e instanceof TypeElement type ? type : null).filter(Objects::nonNull)
                .filter(type -> type.getEnclosingElement() instanceof PackageElement)
                .map(type -> getBean(this.processingEnv.getMessager(), type, null, type.getAnnotation(Meta.class))).toList();

        record AggregatorParts(String package_, String name, List<String> mapParts) {

        }

        var aggregators = new HashMap<String, AggregatorParts>();
        for (var bean : beans) {
            var beanPackage = bean.getPackageName();
            var name = bean.getName();

            var typeSpec = JavaPoetUtils.newTypeBean(bean);
            var inheritMetamodel = typeSpec.superinterfaces.stream().anyMatch(s -> {
                var expected = ClassName.get(MetaModel.class);
                return s instanceof ParameterizedTypeName p ? p.rawType.equals(expected) : s.equals(expected);
            });

            if (inheritMetamodel) {
                var aggParts = aggregators.computeIfAbsent(beanPackage,
                        bp -> new AggregatorParts(bp, getAggregatorName(bp), new ArrayList<>())
                );
                var mapParts = aggParts.mapParts;
                if (mapParts.size() > 1) {
                    mapParts.add(",\n");
                }
                mapParts.add(JavaPoetUtils.mapEntry(bean.getClassName() + ".class", "new " + name + "()").toString());
            }

            var javaFileObject = JavaFile.builder(beanPackage, typeSpec).build().toJavaFileObject();
            writeFile(beanPackage, name, javaFileObject);
        }

        aggregators.forEach((pack, parts) -> {
            var mapParts = parts.mapParts();
            var typeName = parts.name;
            var typeSpec = classBuilder(typeName)
                    .addModifiers(PUBLIC, FINAL)
                    .addField(
                            builder(ClassName.get("", typeName), "instance", PUBLIC, STATIC, FINAL)
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
            var javaFile = JavaFile.builder(pack, typeSpec).build().toJavaFileObject();
            writeFile(pack, typeName, javaFile);
        });

        return true;
    }

    @SneakyThrows
    private void writeFile(String pack, String name, JavaFileObject javaFileObject) {
        var sourceFile = processingEnv.getFiler().createSourceFile(pack + "." + name);
        try (var out = new PrintWriter(sourceFile.openWriter());
             var reader = javaFileObject.openReader(true)) {
            reader.transferTo(out);
        }
    }

}
