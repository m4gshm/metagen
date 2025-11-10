module io.github.m4gshm.meta.processor {
    requires io.github.m4gshm.meta;
    requires java.compiler;
    requires io.jbock.javapoet;
    requires static lombok;
    requires java.desktop;

    exports io.github.m4gshm.meta.processor;
    exports io.github.m4gshm.meta.processor.util;

    provides javax.annotation.processing.Processor with
            io.github.m4gshm.meta.processor.MetaAnnotationProcessor,
            io.github.m4gshm.meta.processor.ModuleAnnotationProcessor;

}
