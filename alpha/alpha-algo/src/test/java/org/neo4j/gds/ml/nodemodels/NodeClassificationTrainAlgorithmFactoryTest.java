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

import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.core.GraphDimensions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.fail;

class NodeClassificationTrainAlgorithmFactoryTest {

    @Test
    void should() {
        var config = ImmutableNodeClassificationTrainConfig.builder()
            .modelName("model")
            .targetProperty("target")
            .addAllMetrics(List.of())
            .holdoutFraction(0.2)
            .validationFolds(5)
            .params(List.of(Map.of("penalty", 1.0)))
            .build();
        var estimate = new NodeClassificationTrainAlgorithmFactory()
            .memoryEstimation(config)
            .estimate(GraphDimensions.of(42L, 1337L), 1);
        System.out.println(estimate.render());
        fail("TODO");
    }


    @Test
    void shouldHandleOneUpdaterPerThread() {
        // do it
    }

}
