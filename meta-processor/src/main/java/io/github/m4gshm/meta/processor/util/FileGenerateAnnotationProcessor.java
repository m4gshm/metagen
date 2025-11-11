package io.github.m4gshm.meta.processor.util;

import io.github.m4gshm.meta.processor.MetaBean;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import java.util.stream.Stream;

public abstract class FileGenerateAnnotationProcessor extends AbstractProcessor {

    protected void writeFiles(RoundEnvironment roundEnv, Stream<MetaBean> beans) {
        new JavaSourceFileWriter(processingEnv, roundEnv.getRootElements()).writeFiles(beans);
    }
}
