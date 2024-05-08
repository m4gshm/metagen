package meta.util;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import java.util.stream.Stream;

public abstract class FileGenerateAnnotationProcessor extends AbstractProcessor {

    protected void writeFiles(RoundEnvironment roundEnv, Stream<MetaBean> beans) {
        WriteClassFileUtils.writeFiles(processingEnv, roundEnv.getRootElements(), beans);
    }
}
