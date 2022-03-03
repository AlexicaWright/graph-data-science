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
package org.neo4j.gds.ml.nodemodels.logisticregression;

import org.neo4j.gds.api.Graph;
import org.neo4j.gds.ml.core.batch.LazyBatch;
import org.neo4j.gds.ml.core.features.FeatureExtractionBaseTest;
import org.neo4j.gds.ml.core.functions.Weights;
import org.neo4j.gds.ml.core.subgraph.LocalIdMap;
import org.neo4j.gds.ml.core.tensor.Matrix;

import java.util.List;

public class NodeLogisticRegressionPredictorFailTest extends FeatureExtractionBaseTest {

    @Override
    public void makeExtractions(Graph graph) {
        var weights = new Weights<>(Matrix.create(0.0, 5, 4));
        var classIdMap = new LocalIdMap();
        var featureProperties = List.of("a", "b");
        var modelData = ImmutableNodeLogisticRegressionData.builder().weights(weights).classIdMap(classIdMap).build();
        var predictor = new NodeLogisticRegressionPredictor(modelData, featureProperties);
        var batch = new LazyBatch(0, 4, 4);
        predictor.predict(graph, batch);
    }
}
