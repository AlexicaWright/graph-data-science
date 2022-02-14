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
package org.neo4j.gds.collections.hsl;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import org.neo4j.gds.collections.HugeSparseList;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public class HugeSparseListStep implements BasicAnnotationProcessor.Step {

    private static final Class<HugeSparseList> HSAL_ANNOTATION = HugeSparseList.class;

    private final Messager messager;
    private final Filer filer;

    private final HugeSparseListValidation validation;
    private final Path sourcePath;

    public HugeSparseListStep(ProcessingEnvironment processingEnv, Path sourcePath) {
        this.validation = new HugeSparseListValidation(
            processingEnv.getTypeUtils(),
            processingEnv.getElementUtils(),
            processingEnv.getMessager()
        );

        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.sourcePath = sourcePath;
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

        var typeSpec = HugeSparseListGenerator.generate(spec);
        var mainFile = javaFile(spec.rootPackage().toString(), typeSpec);

        var result = writeFile(element, mainFile);
        if (result != ProcessResult.PROCESSED) {
            return result;
        }

        var testTypeSpec = HugeSparseListTestGenerator.generate(spec);
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
