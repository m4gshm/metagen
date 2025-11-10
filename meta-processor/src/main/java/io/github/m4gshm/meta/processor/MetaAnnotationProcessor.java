package io.github.m4gshm.meta.processor;

import io.github.m4gshm.meta.Meta;
import io.github.m4gshm.meta.processor.util.FileGenerateAnnotationProcessor;
import io.github.m4gshm.meta.processor.util.MetaBeanExtractor;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Objects;
import java.util.Set;

import static javax.lang.model.SourceVersion.RELEASE_17;

/**
 * The metadata generator
 */
@SupportedAnnotationTypes("io.github.m4gshm.meta.Meta")
@SupportedSourceVersion(RELEASE_17)
public class MetaAnnotationProcessor extends FileGenerateAnnotationProcessor {

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        var extractor = new MetaBeanExtractor(processingEnv.getMessager());
        writeFiles(roundEnv, roundEnv.getElementsAnnotatedWith(Meta.class).stream()
                .map(e -> e instanceof TypeElement type ? type : null)
                .filter(Objects::nonNull)
                .map(type -> extractor.getBean(type, null, null, type.getAnnotation(Meta.class)))
                .filter(Objects::nonNull));
        return true;
    }

}
