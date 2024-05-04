package meta;

import meta.Module.DestinationPackage;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Stream.ofNullable;
import static javax.lang.model.SourceVersion.RELEASE_17;
import static meta.MetaBeanExtractor.PackageAndPrefix.newPackageAndPrefix;
import static meta.Module.DestinationPackage.ModuleNameBased;
import static meta.WriteClassFileUtils.writeFiles;

@SupportedAnnotationTypes("meta.Module")
@SupportedSourceVersion(RELEASE_17)
public class ModuleAnnotationProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var elements = roundEnv.getElementsAnnotatedWith(Module.class);
        var messager = this.processingEnv.getMessager();
        var extractor = new MetaBeanExtractor(messager);
        var beansByPackage = elements.stream()
                .map(e -> e instanceof TypeElement type ? type : null)
                .filter(Objects::nonNull)
                .flatMap(type -> {
                    var module = Optional.ofNullable(type.getAnnotation(Module.class));
                    var destinationPackage = module.map(Module::destinationPackage).orElse(ModuleNameBased);
                    var modulePackageName = type.getSimpleName().toString().toLowerCase();
                    var rootPackage = newPackageAndPrefix(type).beanPackage();
                    var enclosedElements = type.getEnclosedElements();
                    return ofNullable(enclosedElements)
                            .flatMap(Collection::stream)
                            .map(element -> {
                                if (element instanceof VariableElement field &&
                                        field.asType() instanceof DeclaredType declaredType &&
                                        declaredType.asElement() instanceof TypeElement typeElement) {
                                    var packageName = switch (destinationPackage) {
                                        case ModuleNameBased ->  rootPackage + "." + modulePackageName;
                                        case OfModule -> rootPackage;
                                        case OfClass -> null;
                                    };
                                    return extractor.getBean(typeElement, declaredType, packageName,
                                            field.getAnnotation(Meta.class));
                                } else {
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull);
                });
        writeFiles(processingEnv, roundEnv.getRootElements(), beansByPackage);
        return true;
    }
}
