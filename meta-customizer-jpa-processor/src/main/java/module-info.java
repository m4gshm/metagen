module io.github.m4gshm.meta.jpa.processor {
    requires io.github.m4gshm.meta;
    requires io.jbock.javapoet;
    requires io.github.m4gshm.meta.processor;
    requires java.compiler;
    requires static lombok;
    requires io.github.m4gshm.meta.jpa;
    exports io.github.m4gshm.meta.jpa.processor;

    provides io.github.m4gshm.meta.jpa.customizer.JpaColumns with io.github.m4gshm.meta.jpa.processor.JpaColumnsImpl;
}
