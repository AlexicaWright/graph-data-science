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
package org.neo4j.gds.ml.pipeline.node.regression.predict;


import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.core.utils.paged.HugeDoubleArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.core.utils.progress.tasks.Tasks;
import org.neo4j.gds.executor.ExecutionContext;
import org.neo4j.gds.ml.models.Features;
import org.neo4j.gds.ml.models.FeaturesFactory;
import org.neo4j.gds.ml.models.Regressor;
import org.neo4j.gds.ml.models.RegressorFactory;
import org.neo4j.gds.ml.nodePropertyPrediction.regression.NodeRegressionPredict;
import org.neo4j.gds.ml.pipeline.ImmutableGraphFilter;
import org.neo4j.gds.ml.pipeline.PipelineExecutor;
import org.neo4j.gds.ml.pipeline.nodePipeline.NodePropertyPredictPipeline;
import org.neo4j.gds.utils.StringJoining;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public class NodeRegressionPredictPipelineExecutor extends PipelineExecutor<
    NodeRegressionPredictPipelineBaseConfig,
    NodePropertyPredictPipeline,
    HugeDoubleArray
    > {
    private final Regressor.RegressorData modelData;

    public NodeRegressionPredictPipelineExecutor(
        NodePropertyPredictPipeline pipeline,
        NodeRegressionPredictPipelineBaseConfig config,
        ExecutionContext executionContext,
        GraphStore graphStore,
        String graphName,
        ProgressTracker progressTracker,
        Regressor.RegressorData modelData
    ) {
        super(pipeline, config, executionContext, graphStore, graphName, progressTracker);
        this.modelData = modelData;
    }

    public static Task progressTask(String taskName, NodePropertyPredictPipeline pipeline, GraphStore graphStore) {
        return Tasks.task(
            taskName,
            Tasks.iterativeFixed(
                "Execute node property steps",
                () -> List.of(Tasks.leaf("Step")),
                pipeline.nodePropertySteps().size()
            ),
            NodeRegressionPredict.progressTask(graphStore.nodeCount())
        );
    }

    @Override
    public Map<DatasetSplits, GraphFilter> splitDataset() {
        // For prediction, we don't split the input graph but generate the features and predict over the whole graph
        return Map.of(
            DatasetSplits.FEATURE_INPUT,
            ImmutableGraphFilter.of(
                config.nodeLabelIdentifiers(graphStore),
                config.internalRelationshipTypes(graphStore)
            )
        );
    }

    @Override
    protected HugeDoubleArray execute(Map<DatasetSplits, GraphFilter> dataSplits) {
        var graph = graphStore.getGraph(
            config.nodeLabelIdentifiers(graphStore),
            config.internalRelationshipTypes(graphStore),
            Optional.empty()
        );
        Features features = FeaturesFactory.extractLazyFeatures(graph, pipeline.featureProperties());

        if (features.featureDimension() != modelData.featureDimension()) {
            throw new IllegalArgumentException(formatWithLocale(
                "Model expected features %s to have a dimension of `%d`, but got `%d`.",
                StringJoining.join(pipeline.featureProperties()),
                modelData.featureDimension(),
                features.featureDimension()
            ));
        }

        return new NodeRegressionPredict(
            RegressorFactory.create(modelData),
            features,
            config.concurrency(),
            progressTracker,
            terminationFlag
        ).compute();
    }
}
