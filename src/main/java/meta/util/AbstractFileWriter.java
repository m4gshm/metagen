package meta.util;

import io.jbock.javapoet.ClassName;
import lombok.RequiredArgsConstructor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static meta.util.JavaPoetUtils.dotClass;
import static meta.util.MetaBeanExtractor.getAggregatorName;
import static meta.util.MetaBeanExtractor.getPackageClass;

@RequiredArgsConstructor
public abstract class AbstractFileWriter<T> {
    protected final ProcessingEnvironment processingEnv;
    protected final Collection<? extends Element> rootElements;

    protected static Map<String, List<String>> groupClasNamePerPack(Collection<? extends Element> rootElements) {
        return rootElements.stream().collect(groupingBy(
                element -> ofNullable(getPackageClass(element)).map(Object::toString).orElse(""),
                mapping(MetaBeanExtractor::getClassName, toList())
        ));
    }

    public void writeFiles(Stream<MetaBean> beanStream) {
        writeAggregateFile(beanStream.map(bean -> {
            var type = newClassSpec(bean);
            writeFile(bean.getPackageName(), bean.getName(), type);
            return isInheritMetamodel(bean, type) ? bean : null;
        }).filter(Objects::nonNull).collect(groupingBy(MetaBean::getPackageName)));
    }

    protected abstract T newClassSpec(MetaBean bean);

    protected abstract T newAggregateClassSpec(String typeName, List<String> mapParts);

    protected abstract boolean isInheritMetamodel(MetaBean bean, T classSpec);

    protected abstract JavaFileObject createOutputFile(String outFilePath);

    protected abstract JavaFileObject toJavaFileObject(String pack, T type);

    protected void writeAggregateFile(Map<String, List<MetaBean>> metamodels
    ) {
        var classNamePerPack = groupClasNamePerPack(rootElements);
        metamodels.forEach((pack, beans) -> {
            var aggregate = beans.stream().filter(b -> {
                var meta = b.getMeta();
                return meta == null || meta.aggregate();
            }).toList();
            if (!aggregate.isEmpty()) {
                var mapParts = aggregate.stream().map(bean -> JavaPoetUtils.mapEntry(
                        dotClass(ClassName.get(bean.getType())), "new " + bean.getName() + "()"
                ).toString()).toList();

                var typeName = getAggregatorName(pack, classNamePerPack);
                writeFile(pack, typeName, newAggregateClassSpec(typeName, mapParts));
            }
        });
    }

    protected void writeFile(String packageName, String beanName, T type) {
        var srcFile = toJavaFileObject(packageName, type);
        var outFilePath = (packageName == null || packageName.isEmpty() ? "" : packageName + ".") + beanName;
        var outFile = createOutputFile(outFilePath);
        try (
                var out = new PrintWriter(outFile.openWriter());
                var reader = srcFile.openReader(true);
        ) {
            reader.transferTo(out);
        } catch (IOException e) {
            throw new WriteFileException(e);
        }
    }

}
