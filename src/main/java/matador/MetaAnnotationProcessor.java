package matador;

import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.JavaFile;
import io.jbock.javapoet.ParameterSpec;
import io.jbock.javapoet.ParameterizedTypeName;
import lombok.SneakyThrows;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.jbock.javapoet.FieldSpec.builder;
import static io.jbock.javapoet.MethodSpec.methodBuilder;
import static io.jbock.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static matador.MetaAnnotationProcessorUtils.getAggregatorName;
import static matador.MetaAnnotationProcessorUtils.newTypeSpec;

@SupportedAnnotationTypes("matador.Meta")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class MetaAnnotationProcessor extends AbstractProcessor {

    public static final String INDENT = "$>";
    public static final String UNINDENT = "$<";

    @Override
    @SneakyThrows
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        var elements = roundEnv.getElementsAnnotatedWith(Meta.class);
        var beans = elements.stream()
                .map(e -> e instanceof TypeElement type ? type : null).filter(Objects::nonNull)
                .filter(type -> type.getEnclosingElement() instanceof PackageElement)
                .map(type -> MetaAnnotationProcessorUtils.getBean(type, processingEnv.getMessager())).toList();

        record AggregatorParts(String package_, String name, List<String> mapParts) {

        }

        var aggregators = new HashMap<String, AggregatorParts>();
        for (var bean : beans) {
            var beanPackage = bean.getPackage_();
            var name = bean.getName();

            var typeSpec = newTypeSpec(bean);
            var inheritMetamodel = typeSpec.superinterfaces.stream()
                    .anyMatch(s -> s.equals(ClassName.get(MetaModel.class)));

            if (inheritMetamodel) {
                var aggParts = aggregators.computeIfAbsent(beanPackage,
                        bp -> new AggregatorParts(bp, getAggregatorName(bp), new ArrayList<>())
                );
                var mapParts = aggParts.mapParts;
                if (mapParts.size() > 1) {
                    mapParts.add(",\n");
                }
                mapParts.add(bean.getClass_() + ".class, new " + name + "()");
            }

            var javaFileObject = JavaFile.builder(beanPackage, typeSpec).build().toJavaFileObject();
            writeFile(beanPackage, name, javaFileObject);
        }

        aggregators.forEach((pack, parts) -> {
            var init = "Map.of(\n" + INDENT + parts.mapParts.stream().reduce("", (l, r) -> l + r) + UNINDENT + "\n)";
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
                    .addField(
                            builder(
                                    ParameterizedTypeName.get(Map.class, Class.class, MetaModel.class),
                                    "metas", PRIVATE, FINAL
                            )
                                    .initializer(CodeBlock.builder().add(init).build())
                                    .build()
                    )
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
