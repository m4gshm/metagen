package io.github.m4gshm.meta.processor.util;

import io.github.m4gshm.meta.processor.MetaCustomizer;

import javax.lang.model.type.TypeMirror;

public class MetaCustomizerException extends RuntimeException {
    public MetaCustomizerException(String message) {
        super(message);
    }

    public MetaCustomizerException(Class<? extends MetaCustomizer<?>> customizerClass, Exception e) {
        super(customizerClass.getName(), e);
    }

    public MetaCustomizerException(TypeMirror typeMirror, Exception e) {
        super(typeMirror != null ? typeMirror.toString() : null, e);
    }
}
