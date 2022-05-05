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
package org.neo4j.gds.ml.pipeline.linkPipeline;

import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.config.ToMapConvertible;
import org.neo4j.gds.ml.metrics.CandidateStats;
import org.neo4j.gds.ml.metrics.Metric;

import java.util.Map;

@ValueClass
public interface LinkPredictionModelInfo extends ToMapConvertible {

    Map<Metric, Double> testMetrics();
    Map<Metric, Double> outerTrainMetrics();
    CandidateStats bestCandidate();

    LinkPredictionPredictPipeline pipeline();

    @Override
    default Map<String, Object> toMap() {
        return Map.of(
            "bestParameters", bestCandidate().trainerConfig().toMapWithTrainerMethod(),
            "metrics", bestCandidate().renderMetrics(testMetrics(), outerTrainMetrics()),
            "pipeline", pipeline().toMap()
        );
    }

    static LinkPredictionModelInfo of(
        Map<Metric, Double> testMetrics,
        Map<Metric, Double> outerTrainMetrics,
        CandidateStats bestCandidate,
        LinkPredictionPredictPipeline pipeline
    ) {
        return ImmutableLinkPredictionModelInfo.of(testMetrics, outerTrainMetrics, bestCandidate, pipeline);
    }
}
