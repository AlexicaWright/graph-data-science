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
package org.neo4j.gds.ml.pipeline.nodePipeline.regression;

import org.immutables.value.Value;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.config.ToMapConvertible;
import org.neo4j.gds.ml.metrics.ModelCandidateStats;
import org.neo4j.gds.ml.metrics.Metric;
import org.neo4j.gds.ml.pipeline.nodePipeline.NodePropertyPredictPipeline;

import java.util.Map;

@ValueClass
public interface NodeRegressionPipelineModelInfo extends ToMapConvertible {

    Map<Metric, Double> testMetrics();
    Map<Metric, Double> outerTrainMetrics();
    ModelCandidateStats bestCandidate();

    NodePropertyPredictPipeline pipeline();

    @Override
    @Value.Auxiliary
    @Value.Derived
    default Map<String, Object> toMap() {
        return Map.of(
            "bestParameters", bestCandidate().trainerConfig().toMapWithTrainerMethod(),
            "pipeline", pipeline().toMap(),
            "metrics", bestCandidate().renderMetrics(testMetrics(), outerTrainMetrics())
        );
    }

    static ImmutableNodeRegressionPipelineModelInfo.Builder builder() {
        return ImmutableNodeRegressionPipelineModelInfo.builder();
    }

    static NodeRegressionPipelineModelInfo of(
        Map<Metric, Double> testMetrics,
        Map<Metric, Double> outerTrainMetrics,
        ModelCandidateStats bestCandidate,
        NodePropertyPredictPipeline pipeline
    ) {
        return ImmutableNodeRegressionPipelineModelInfo.of(testMetrics, outerTrainMetrics, bestCandidate, pipeline);
    }
}
