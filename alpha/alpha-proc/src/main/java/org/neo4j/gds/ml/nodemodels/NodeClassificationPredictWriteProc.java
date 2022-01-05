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
package org.neo4j.gds.ml.nodemodels;

import org.neo4j.gds.GraphAlgorithmFactory;
import org.neo4j.gds.WriteProc;
import org.neo4j.gds.api.nodeproperties.DoubleArrayNodeProperties;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.model.ModelCatalog;
import org.neo4j.gds.core.write.NodeProperty;
import org.neo4j.gds.ml.nodemodels.logisticregression.NodeClassificationResult;
import org.neo4j.gds.pipeline.ComputationResult;
import org.neo4j.gds.pipeline.ExecutionContext;
import org.neo4j.gds.pipeline.GdsCallable;
import org.neo4j.gds.pipeline.validation.ValidationConfiguration;
import org.neo4j.gds.result.AbstractResultBuilder;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.gds.pipeline.ExecutionMode.WRITE_NODE_PROPERTY;
import static org.neo4j.procedure.Mode.READ;
import static org.neo4j.procedure.Mode.WRITE;

@GdsCallable(name = "gds.alpha.ml.nodeClassification.predict.write", description = "Predicts classes for all nodes based on a previously trained model", executionMode = WRITE_NODE_PROPERTY)
public class NodeClassificationPredictWriteProc extends WriteProc<NodeClassificationPredict, NodeClassificationResult, NodeClassificationPredictWriteProc.Result, NodeClassificationPredictWriteConfig> {

    @Context
    public ModelCatalog modelCatalog;

    @Procedure(name = "gds.alpha.ml.nodeClassification.predict.write", mode = WRITE)
    @Description("Predicts classes for all nodes based on a previously trained model")
    public Stream<Result> write(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        var result = compute(graphName, configuration);
        return write(result);
    }

    @Procedure(name = "gds.alpha.ml.nodeClassification.predict.write.estimate", mode = READ)
    @Description("Predicts classes for all nodes based on a previously trained model")
    public Stream<MemoryEstimateResult> estimate(
        @Name(value = "graphNameOrConfiguration") Object graphNameOrConfiguration,
        @Name(value = "algoConfiguration") Map<String, Object> algoConfiguration
    ) {
        return computeEstimate(graphNameOrConfiguration, algoConfiguration);
    }

    @Override
    protected List<NodeProperty> nodePropertyList(ComputationResult<NodeClassificationPredict, NodeClassificationResult, NodeClassificationPredictWriteConfig> computationResult) {
        var config = computationResult.config();
        var writeProperty = config.writeProperty();
        var result = computationResult.result();
        var classProperties = result.predictedClasses().asNodeProperties();
        var nodeProperties = new ArrayList<NodeProperty>();
        nodeProperties.add(NodeProperty.of(writeProperty, classProperties));
        result.predictedProbabilities().ifPresent((probabilityProperties) -> {
            var properties = new DoubleArrayNodeProperties() {
                @Override
                public long size() {
                    return computationResult.graph().nodeCount();
                }

                @Override
                public double[] doubleArrayValue(long nodeId) {
                    return probabilityProperties.get(nodeId);
                }
            };

            nodeProperties.add(NodeProperty.of(
                config.predictedProbabilityProperty().orElseThrow(),
                properties
            ));
        });

        return nodeProperties;
    }

    @Override
    public ValidationConfiguration<NodeClassificationPredictWriteConfig> validationConfig() {
        return NodeClassificationCompanion.getValidationConfig(modelCatalog);
    }

    @Override
    protected NodeClassificationPredictWriteConfig newConfig(String username, CypherMapWrapper config) {
        return NodeClassificationPredictWriteConfig.of(username, config);
    }

    @Override
    public GraphAlgorithmFactory<NodeClassificationPredict, NodeClassificationPredictWriteConfig> algorithmFactory() {
        return new NodeClassificationPredictAlgorithmFactory<>(modelCatalog);
    }

    @Override
    protected AbstractResultBuilder<Result> resultBuilder(
        ComputationResult<NodeClassificationPredict, NodeClassificationResult, NodeClassificationPredictWriteConfig> computeResult,
        ExecutionContext executionContext
    ) {
        return new Result.Builder();
    }

    public static class Result {

        public final long writeMillis;
        public final long nodePropertiesWritten;
        public final long preProcessingMillis;
        public final long computeMillis;
        public final Map<String, Object> configuration;

        public Result(
            long preProcessingMillis,
            long computeMillis,
            long writeMillis,
            long nodePropertiesWritten,
            Map<String, Object> configuration
        ) {
            this.preProcessingMillis = preProcessingMillis;
            this.computeMillis = computeMillis;
            this.writeMillis = writeMillis;
            this.nodePropertiesWritten = nodePropertiesWritten;
            this.configuration = configuration;
        }
        static class Builder extends AbstractResultBuilder<NodeClassificationPredictWriteProc.Result> {

            @Override
            public NodeClassificationPredictWriteProc.Result build() {
                return new NodeClassificationPredictWriteProc.Result(
                    preProcessingMillis,
                    computeMillis,
                    writeMillis,
                    nodePropertiesWritten,
                    config.toMap()
                );
            }
        }
    }


}
