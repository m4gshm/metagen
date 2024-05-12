package meta.util;

import meta.MetaCustomizer;

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
