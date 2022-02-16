/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.collections.hsal;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import org.neo4j.gds.collections.HugeSparseArrayList;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public class HugeSparseArrayListStep implements BasicAnnotationProcessor.Step {

    private static final Class<HugeSparseArrayList> HSAL_ANNOTATION = HugeSparseArrayList.class;

    private final Messager messager;
    private final Filer filer;

    private final HugeSparseArrayListValidation validation;
    private final Path sourcePath;

    public HugeSparseArrayListStep(ProcessingEnvironment processingEnv) {
        this.validation = new HugeSparseArrayListValidation(
            processingEnv.getTypeUtils(),
            processingEnv.getElementUtils(),
            processingEnv.getMessager()
        );

        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.sourcePath = fetchSourcePath();
    }

    @Override
    public Set<String> annotations() {
        return Set.of(HSAL_ANNOTATION.getCanonicalName());
    }

    @Override
    public Set<? extends Element> process(ImmutableSetMultimap<String, Element> elementsByAnnotation) {
        var elements = elementsByAnnotation.get(HSAL_ANNOTATION.getCanonicalName());

        ImmutableSet.Builder<Element> elementsToRetry = ImmutableSet.builder();

        for (Element element : elements) {
            if (process(element) == ProcessResult.RETRY) {
                elementsToRetry.add(element);
            }
        }

        return elementsToRetry.build();
    }

    private ProcessResult process(Element element) {
        var validationResult = validation.validate(element);

        if (validationResult.isEmpty()) {
            return ProcessResult.INVALID;
        }

        var spec = validationResult.get();

        var typeSpec = HugeSparseArrayListGenerator.generate(spec);
        var mainFile = javaFile(spec.rootPackage().toString(), typeSpec);

        var result = writeFile(element, mainFile);
        if (result != ProcessResult.PROCESSED) {
            return result;
        }

        var testTypeSpec = HugeSparseArrayListTestGenerator.generate(spec);
        var testFile = javaFile(spec.rootPackage().toString(), testTypeSpec);

        return writeTestFile(element, testFile);
    }

    private static JavaFile javaFile(String rootPackage, TypeSpec typeSpec) {
        return JavaFile
            .builder(rootPackage, typeSpec)
            .indent("    ")
            .skipJavaLangImports(true)
            .build();
    }

    private ProcessResult writeTestFile(Element element, JavaFile file) {
        var testSourcePath = sourcePath.resolve("test");

        try {
            file.writeTo(testSourcePath);
            return ProcessResult.PROCESSED;
        } catch (IOException e) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                "Could not write HugeSparseArray java file: " + e.getMessage(),
                element
            );
            return ProcessResult.RETRY;
        }
    }

    private Path fetchSourcePath() {
        JavaFileObject tmpFile = null;
        try {
            // We want to retrieve the path for generated source files managed
            // by the filer object and its underlying FileManager. In order to
            // retrieve it, we create a temporary file, convert it into a Path
            // object and navigate to the parent directories.
            tmpFile = filer.createSourceFile("tmpFile");
            // the new file is open for writing; we don't do that so we close it
            tmpFile.openWriter().close();
            // build/generated/sources/annotationProcessor/java/main/tmpFile
            var tmpFilePath = Path.of(tmpFile.toUri());
            // build/generated/sources/annotationProcessor/java/main
            var mainPath = tmpFilePath.getParent();
            // build/generated/sources/annotationProcessor/java
            return mainPath.getParent();
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Unable to determine source path");
        } finally {
            if (tmpFile != null) {
                tmpFile.delete();
            }
        }

        return null;
    }

    private ProcessResult writeFile(Element element, JavaFile file) {
        try {
            file.writeTo(filer);
            return ProcessResult.PROCESSED;
        } catch (IOException e) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                "Could not write HugeSparseArray java file: " + e.getMessage(),
                element
            );
            return ProcessResult.RETRY;
        }
    }

    enum ProcessResult {
        PROCESSED,
        INVALID,
        RETRY
    }
}
