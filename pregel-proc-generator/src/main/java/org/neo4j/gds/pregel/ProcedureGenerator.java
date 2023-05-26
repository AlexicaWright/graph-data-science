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
package org.neo4j.gds.pregel;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import org.jetbrains.annotations.NotNull;
import org.neo4j.gds.BaseProc;
import org.neo4j.gds.GraphAlgorithmFactory;
import org.neo4j.gds.beta.pregel.annotation.GDSMode;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.executor.ExecutionContext;
import org.neo4j.gds.executor.ExecutionMode;
import org.neo4j.gds.executor.GdsCallable;
import org.neo4j.gds.executor.validation.ValidationConfiguration;
import org.neo4j.gds.pregel.generator.TypeNames;
import org.neo4j.gds.pregel.proc.PregelBaseProc;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import javax.lang.model.element.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

abstract class ProcedureGenerator extends PregelGenerator {

    final PregelValidation.Spec pregelSpec;
    final TypeNames typeNames;

    ProcedureGenerator(Optional<AnnotationSpec> annotationSpec, PregelValidation.Spec pregelSpec, TypeNames typeNames) {
        super(annotationSpec);
        this.pregelSpec = pregelSpec;
        this.typeNames = typeNames;
    }

    static TypeSpec forMode(
        GDSMode mode,
        Optional<AnnotationSpec> generatedAnnotationSpec,
        PregelValidation.Spec pregelSpec,
        TypeNames typeNames
    ) {
        switch (mode) {
            case STREAM: return new StreamProcedureGenerator(generatedAnnotationSpec, pregelSpec, typeNames).typeSpec();
            case WRITE: return new WriteProcedureGenerator(generatedAnnotationSpec, pregelSpec, typeNames).typeSpec();
            case MUTATE: return new MutateProcedureGenerator(generatedAnnotationSpec, pregelSpec, typeNames).typeSpec();
            case STATS: return new StatsProcedureGenerator(generatedAnnotationSpec, pregelSpec, typeNames).typeSpec();
            default: throw new IllegalArgumentException("Unsupported procedure mode: " + mode);
        }
    }

    abstract GDSMode procGdsMode();

    abstract Mode procExecMode();

    abstract Class<?> procBaseClass();

    abstract Class<?> procResultClass();

    abstract MethodSpec procResultMethod();

    TypeSpec typeSpec() {
        var configTypeName = typeNames.config();
        var procedureClassName = typeNames.procedure(procGdsMode());
        var algorithmClassName = typeNames.algorithm();

        var typeSpecBuilder = getTypeSpecBuilder(configTypeName, procedureClassName, algorithmClassName);

        addGeneratedAnnotation(typeSpecBuilder);

        typeSpecBuilder.addMethod(procMethod());
        typeSpecBuilder.addMethod(procEstimateMethod());
        typeSpecBuilder.addMethod(procResultMethod());
        typeSpecBuilder.addMethod(newConfigMethod());
        typeSpecBuilder.addMethod(algorithmFactoryMethod());

        if (pregelSpec.requiresInverseIndex()) {
            typeSpecBuilder.addMethod(validationConfigMethod());
        }

        return typeSpecBuilder.build();
    }

    @NotNull
    private TypeSpec.Builder getTypeSpecBuilder(
        TypeName configTypeName,
        ClassName procedureClassName,
        ClassName algorithmClassName
    ) {

        ExecutionMode executionMode;
        switch (procGdsMode()) {
            case STATS: executionMode = ExecutionMode.STATS; break;
            case WRITE: executionMode = ExecutionMode.WRITE_NODE_PROPERTY; break;
            case MUTATE: executionMode = ExecutionMode.MUTATE_NODE_PROPERTY; break;
            case STREAM: executionMode = ExecutionMode.STREAM; break;
            default: throw new IllegalArgumentException("Unsupported procedure mode: " + procGdsMode());
        }

        var gdsCallableAnnotationBuilder = AnnotationSpec
            .builder(GdsCallable.class)
            .addMember("name", "$S", formatWithLocale("%s.%s", pregelSpec.procedureName(), procGdsMode().lowerCase()))
            .addMember("executionMode", "$T.$L", ExecutionMode.class, executionMode);
        pregelSpec.description().ifPresent(description ->
            gdsCallableAnnotationBuilder.addMember("description", "$S", description)
        );

        return TypeSpec
            .classBuilder(procedureClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .superclass(ParameterizedTypeName.get(
                ClassName.get(procBaseClass()),
                algorithmClassName,
                configTypeName
            ))
            .addAnnotation(gdsCallableAnnotationBuilder.build());
    }

    private MethodSpec procMethod() {
        var methodBuilder = procMethodSignature(procExecMode());
        pregelSpec.description().ifPresent(description -> methodBuilder.addAnnotation(
            AnnotationSpec.builder(Description.class)
                .addMember("value", "$S", description)
                .build()
        ));
        return methodBuilder
            .addStatement("return $L(compute(graphName, configuration))", procGdsMode().lowerCase())
            .returns(ParameterizedTypeName.get(Stream.class, procResultClass()))
            .build();
    }

    private MethodSpec procEstimateMethod() {
        return estimateMethodSignature()
            .addAnnotation(AnnotationSpec.builder(Description.class)
                .addMember("value", "$T.ESTIMATE_DESCRIPTION", BaseProc.class)
                .build()
            )
            .addStatement("return computeEstimate(graphNameOrConfiguration, algoConfiguration)", procGdsMode().lowerCase())
            .returns(ParameterizedTypeName.get(Stream.class, MemoryEstimateResult.class))
            .build();
    }

    private MethodSpec.@NotNull Builder procMethodSignature(Mode procExecMode) {
        return MethodSpec.methodBuilder(procGdsMode().lowerCase())
            .addAnnotation(AnnotationSpec.builder(Procedure.class)
                .addMember(
                    "name",
                    "$S",
                    formatWithLocale("%s.%s", pregelSpec.procedureName(), procGdsMode().lowerCase())
                )
                .addMember("mode", "$T.$L", Mode.class, procExecMode)
                .build()
            )
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ParameterSpec.builder(String.class, "graphName")
                .addAnnotation(AnnotationSpec.builder(Name.class)
                    .addMember("value", "$S", "graphName")
                    .build())
                .build())
            .addParameter(ParameterSpec
                .builder(ParameterizedTypeName.get(Map.class, String.class, Object.class), "configuration")
                .addAnnotation(AnnotationSpec.builder(Name.class)
                    .addMember("value", "$S", "configuration")
                    .addMember("defaultValue", "$S", "{}")
                    .build())
                .build());
    }

    @NotNull
    private MethodSpec.Builder estimateMethodSignature() {
        return MethodSpec.methodBuilder("estimate")
            .addAnnotation(AnnotationSpec.builder(Procedure.class)
                .addMember(
                    "name",
                    "$S",
                    formatWithLocale("%s.%s.estimate", pregelSpec.procedureName(), procGdsMode().lowerCase())
                )
                .addMember("mode", "$T.$L", Mode.class, Mode.READ)
                .build()
            )
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ParameterSpec.builder(Object.class, "graphNameOrConfiguration")
                .addAnnotation(AnnotationSpec.builder(Name.class)
                    .addMember("value", "$S", "graphNameOrConfiguration")
                    .build())
                .build())
            .addParameter(ParameterSpec
                .builder(ParameterizedTypeName.get(Map.class, String.class, Object.class), "algoConfiguration")
                .addAnnotation(AnnotationSpec.builder(Name.class)
                    .addMember("value", "$S", "algoConfiguration")
                    .build())
                .build());
    }

    private MethodSpec newConfigMethod() {
        return MethodSpec.methodBuilder("newConfig")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PROTECTED)
            .addParameter(String.class, "username")
            .addParameter(CypherMapWrapper.class, "config")
            .returns(typeNames.config())
            .addStatement("return $T.of(config)", typeNames.config())
            .build();
    }

    private MethodSpec algorithmFactoryMethod() {
        return MethodSpec.methodBuilder("algorithmFactory")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ClassName.get(ExecutionContext.class), "executionContext")
            .returns(ParameterizedTypeName.get(
                ClassName.get(GraphAlgorithmFactory.class),
                typeNames.algorithm(),
                typeNames.config()
            ))
            .addStatement("return new $T()", typeNames.algorithmFactory())
            .build();
    }

    private MethodSpec validationConfigMethod() {
        return MethodSpec.methodBuilder("validationConfig")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ClassName.get(ExecutionContext.class), "executionContext")
            .returns(ParameterizedTypeName.get(
                ClassName.get(ValidationConfiguration.class),
                typeNames.config()
            ))
            .addStatement("return $T.ensureIndexValidation(executionContext.log(), executionContext.taskRegistryFactory())", PregelBaseProc.class)
            .build();
    }
}
