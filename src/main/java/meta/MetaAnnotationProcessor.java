package meta;

import lombok.SneakyThrows;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Objects;
import java.util.Set;

import static javax.lang.model.SourceVersion.RELEASE_17;
import static meta.WriteClassFileUtils.writeFiles;

/**
 * The metadata generator
 */
@SupportedAnnotationTypes("meta.Meta")
@SupportedSourceVersion(RELEASE_17)
public class MetaAnnotationProcessor extends AbstractProcessor {

    @Override
    @SneakyThrows
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        var extractor = new MetaBeanExtractor(processingEnv.getMessager());
        var beansByPackage = roundEnv.getElementsAnnotatedWith(Meta.class).stream()
                .map(e -> e instanceof TypeElement type ? type : null)
                .filter(Objects::nonNull)
                .map(type -> extractor.getBean(type, null, null, type.getAnnotation(Meta.class)))
                .filter(Objects::nonNull);
        writeFiles(processingEnv, roundEnv.getRootElements(), beansByPackage);
        return true;
    }

}
