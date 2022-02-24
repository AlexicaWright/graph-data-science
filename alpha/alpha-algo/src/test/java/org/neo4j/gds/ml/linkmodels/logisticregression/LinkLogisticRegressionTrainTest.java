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
package org.neo4j.gds.ml.linkmodels.logisticregression;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.utils.TerminationFlag;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.ml.core.features.FeatureExtraction;
import org.neo4j.gds.ml.core.tensor.Matrix;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@GdlExtension
class LinkLogisticRegressionTrainTest {

    @GdlGraph
    private static final String DB_QUERY =
        "CREATE " +
        "  (n1:N {a: 2.0, b: 1.2})" +
        ", (n2:N {a: 1.3, b: 0.5})" +
        ", (n3:N {a: 0.0, b: 2.8})" +
        ", (n4:N {a: 1.0, b: 0.9})" +
        ", (n5:N {a: 1.0, b: 0.9})" +
        ", (n1)-[:T {label: 1.0}]->(n2)" +
        ", (n3)-[:T {label: 1.0}]->(n4)" +
        ", (n1)-[:T {label: 0.0}]->(n3)" +
        ", (n2)-[:T {label: 0.0}]->(n4)";

    @Inject
    private Graph graph;

    // not ported; don't need it
    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void shouldComputeWithDefaultAdamOptimizerAndStreakStopper(int concurrency) {
        var featureProperties = List.of("a", "b");
        var config = new LinkLogisticRegressionTrainConfigImpl(
            featureProperties,
            CypherMapWrapper.create(Map.of(
                "maxEpochs", 10_000,
                "tolerance", 1e-4
            ))
        );

        var extractors = FeatureExtraction.propertyExtractors(graph, featureProperties);
        var trainSet = HugeLongArray.newArray(graph.nodeCount());
        trainSet.setAll(i -> i);
        var linearRegression = new LinkLogisticRegressionTrain(
            graph,
            trainSet,
            extractors,
            config,
            ProgressTracker.NULL_TRACKER,
            TerminationFlag.RUNNING_TRUE,
            concurrency
        );

        var result = linearRegression.compute();

        var expected = new Matrix(new double[]{-1.0681821169962793, 1.0115009499444914, -0.1381213947059403}, 1, 3);
        assertThat(result.weights().data()).matches(matrix -> matrix.equals(expected, 1e-8));
    }

    // ported
    @Test
    void usingPenaltyShouldGiveSmallerAbsoluteValueWeights() {
        var featureProperties = List.of("a", "b");
        var config = new LinkLogisticRegressionTrainConfigImpl(
            featureProperties,
            CypherMapWrapper.create(Map.of(
                "maxEpochs", 100000,
                "tolerance", 1e-4,
                "penalty", 1
            ))
        );

        var extractors = FeatureExtraction.propertyExtractors(graph, featureProperties);

        var trainSet = HugeLongArray.newArray(graph.nodeCount());
        trainSet.setAll(i -> i);
        var linearRegression = new LinkLogisticRegressionTrain(graph,
            trainSet,
            extractors,
            config,
            ProgressTracker.NULL_TRACKER,
            TerminationFlag.RUNNING_TRUE,
            1
        );

        var result = linearRegression.compute();

        assertThat(result).isNotNull();

        var trainedWeights = result.weights();

        double[] weights = trainedWeights.data().data();
        assertThat(Math.pow(weights[0], 2) + Math.pow(weights[1], 2))
            .isLessThan(Math.pow(-1.0681821169962793, 2) + Math.pow(1.0115009499444914, 2));
    }
}
