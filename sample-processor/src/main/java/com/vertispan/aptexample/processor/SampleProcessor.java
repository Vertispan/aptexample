package com.vertispan.aptexample.processor;

import com.google.auto.service.AutoService;
import com.vertispan.aptexample.annotation.Sample;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * A simple processor for the sake of a demo, which watches for an interface annotated with
 * {@code @Sample}, and implements it. This annotated interface might have methods on it,
 * each returning {@code List<String>}, with a name matching a class in the same project.
 * Each of those lists will be the full list of methods in the matching class.
 * </p>
 * <p>
 * This annotation processor will mark the generated class as having "originated" from all
 * of the various classes used. In maven, we will see it re-run the annotation processor
 * any time that the project is compiled (excessive, but accurate), in Eclipse, we will see
 * the processor only run when the originating or annotated classes are edited (accurate,
 * slightly more efficient). In IntelliJ however, the annotation processor fails to run
 * when anything but the annotated class is edited (resulting in inaccurate code), unless
 * a clean and full build are performed.
 * </p>
 */
// for this line, I lean on google-auto's auto-service project to simply generate the required
// meta-inf/services file.
@AutoService(Processor.class)
public class SampleProcessor extends AbstractProcessor {

    // This is an optional method to override - without it, the default is java6 and a warning
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    // Indicate which annotations are handled by this processor. Can also return "*" to
    // request all files be passed through this processor.
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(Sample.class.getName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> typesToGenerate = roundEnv.getElementsAnnotatedWith(Sample.class);

        typesToGenerate.forEach(this::handle);

        return false;
    }

    private void handle(Element typeToImplement) {
        String packageName = processingEnv.getElementUtils().getPackageOf(typeToImplement).getQualifiedName().toString();
        String implName = typeToImplement.getSimpleName().toString() + "_Impl";
        String fqdnPlusImpl = packageName + "." + implName;

        // Typemmirror for List<String>, a frequently used type in this sample
        TypeMirror listOfString = processingEnv.getTypeUtils().getDeclaredType(
                processingEnv.getElementUtils().getTypeElement(List.class.getName()),
                processingEnv.getElementUtils().getTypeElement(String.class.getName()).asType()
        );

        Set<Element> members = new HashSet<>(typeToImplement.getEnclosedElements());

        // Decide which types to implement - if a method doesn't match this pattern, we should probably error out...
        Set<ExecutableElement> methods =
                ElementFilter.methodsIn(members).stream()
                        .filter(method -> method.getParameters().size() == 0)
                        .filter(method -> processingEnv.getTypeUtils().isAssignable(method.getReturnType(), listOfString))
                        .filter(method -> method.getReturnType().getKind() == TypeKind.DECLARED)
                        .collect(Collectors.toSet());

        // Grab a list of all the types that could change which will affect our generated code
        Set<Element> typesToWatch = methods.stream().map(
                method -> processingEnv.getElementUtils().getTypeElement(packageName + "." + method.getSimpleName().toString())).collect(Collectors.toSet()
        );

        // Log those types, so we can keep track
        for (Element toWatch : typesToWatch) {
            processingEnv.getMessager().printMessage(Kind.MANDATORY_WARNING, "originating element: " + toWatch.getSimpleName().toString());

        }
        try {
            // Create the new generated file, and mark that it should be rebuilt any time any of the watched types are altered
            JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(fqdnPlusImpl, typesToWatch.toArray(new Element[0]));

            // This is very nearly the worst possible way to generate java, but it keeps our list of dependencies down for a sample.
            // I'll do another sample later showing off some of the better toys available to us: javapoet, velocity, etc.
            // At the very least, if you follow this example, correctly escape strings before printing them!
            try (Writer writer = sourceFile.openWriter()) {
                writer.append("package ").append(packageName).append(";\n\n");
                writer.append("public final class ").append(implName).append(" implements ").append(typeToImplement.getSimpleName().toString()).append("{ \n");

                for (ExecutableElement method : methods) {
                    if (!method.getModifiers().contains(Modifier.ABSTRACT)) {
                        //skip non-abstract methods (like public static void main())
                        continue;
                    }
                    writer.append("  public ").append(method.getReturnType().toString()).append(" ").append(method.getSimpleName().toString()).append("() {\n");

                    writer.append("    return java.util.Arrays.asList(");
                    boolean first = true;
                    Set<ExecutableElement> methodsInType = ElementFilter.methodsIn(new HashSet<>(processingEnv.getElementUtils().getTypeElement(packageName + "." + method.getSimpleName().toString()).getEnclosedElements()));
                    for (ExecutableElement methodInType : methodsInType) {
                        if (!first) {
                            writer.append(",");
                        }
                        first = false;
                        writer.append("\n        \"").append(methodInType.getSimpleName().toString()).append("\"");
                    }
                    writer.append("\n    );\n");

                    writer.append("  }\n");
                }

                writer.append("}\n");
            }

        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}