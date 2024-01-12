package metagen;

import lombok.experimental.UtilityClass;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import java.util.function.Supplier;

@UtilityClass
public class ClassLoadUtility {

    public static <T> Class<T> load(Supplier<Class<T>> classProvider) {
        Class<T> customizerClass;
        try {
            customizerClass = classProvider.get();
        } catch (MirroredTypeException e) {
            var typeMirror = e.getTypeMirror();
            if (typeMirror instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
                customizerClass = (Class<T>) load(te);
            } else {
                throw new MetaCustomizerException(typeMirror, e);
            }
        }
        return customizerClass;
    }

    public static Class<?> load(TypeElement typeElement) {
        return load(typeElement.getQualifiedName().toString());
    }

    public static Class<?> load(String fullClassName) {
        try {
            return MetaAnnotationProcessor.class.getClassLoader().loadClass(fullClassName);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }
}
