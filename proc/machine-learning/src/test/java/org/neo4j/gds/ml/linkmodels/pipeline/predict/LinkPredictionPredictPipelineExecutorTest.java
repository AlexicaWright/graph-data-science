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
package org.neo4j.gds.ml.linkmodels.pipeline.predict;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.BaseProcTest;
import org.neo4j.gds.GdsCypher;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.TestProcedureRunner;
import org.neo4j.gds.TestProgressTracker;
import org.neo4j.gds.api.DefaultValue;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.schema.GraphSchema;
import org.neo4j.gds.catalog.GraphProjectProc;
import org.neo4j.gds.catalog.GraphStreamNodePropertiesProc;
import org.neo4j.gds.compat.Neo4jProxy;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.loading.GraphStoreCatalog;
import org.neo4j.gds.core.model.Model;
import org.neo4j.gds.core.model.ModelCatalog;
import org.neo4j.gds.core.utils.mem.MemoryRange;
import org.neo4j.gds.core.utils.progress.EmptyTaskRegistryFactory;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.extension.Neo4jGraph;
import org.neo4j.gds.extension.Neo4jModelCatalogExtension;
import org.neo4j.gds.ml.core.functions.Weights;
import org.neo4j.gds.ml.core.tensor.Matrix;
import org.neo4j.gds.ml.decisiontree.DecisionTreePredictor;
import org.neo4j.gds.ml.decisiontree.TreeNode;
import org.neo4j.gds.ml.metrics.ModelCandidateStats;
import org.neo4j.gds.ml.models.logisticregression.ImmutableLogisticRegressionData;
import org.neo4j.gds.ml.models.logisticregression.LogisticRegressionClassifier;
import org.neo4j.gds.ml.models.logisticregression.LogisticRegressionTrainConfig;
import org.neo4j.gds.ml.models.randomforest.ImmutableRandomForestClassifierData;
import org.neo4j.gds.ml.models.randomforest.RandomForestClassifier;
import org.neo4j.gds.ml.pipeline.NodePropertyStepFactory;
import org.neo4j.gds.ml.pipeline.linkPipeline.LinkPredictionModelInfo;
import org.neo4j.gds.ml.pipeline.linkPipeline.LinkPredictionPredictPipeline;
import org.neo4j.gds.ml.pipeline.linkPipeline.linkfunctions.L2FeatureStep;
import org.neo4j.gds.ml.pipeline.linkPipeline.train.LinkPredictionTrain;
import org.neo4j.gds.ml.pipeline.linkPipeline.train.LinkPredictionTrainConfigImpl;
import org.neo4j.gds.test.TestProc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.gds.TestSupport.assertMemoryEstimation;
import static org.neo4j.gds.assertj.Extractors.removingThreadId;
import static org.neo4j.gds.assertj.Extractors.replaceTimings;
import static org.neo4j.gds.compat.TestLog.INFO;
import static org.neo4j.gds.ml.pipeline.linkPipeline.LinkPredictionTrainingPipeline.MODEL_TYPE;

@Neo4jModelCatalogExtension
class LinkPredictionPredictPipelineExecutorTest extends BaseProcTest {
    public static final String GRAPH_NAME = "g";

    @Neo4jGraph
    static String GDL = "CREATE " +
                        "  (n0:N {a: 1.0, b: 0.8, c: 1.0})" +
                        ", (n1:N {a: 2.0, b: 1.0, c: 1.0})" +
                        ", (n2:N {a: 3.0, b: 1.5, c: 1.0})" +
                        ", (n3:N {a: 0.0, b: 2.8, c: 1.0})" +
                        ", (n4:N {a: 1.0, b: 0.9, c: 1.0})" +
                        ", (n1)-[:T]->(n2)" +
                        ", (n3)-[:T]->(n4)" +
                        ", (n1)-[:T]->(n3)" +
                        ", (n2)-[:T]->(n4)";

    private GraphStore graphStore;

    @Inject
    private ModelCatalog modelCatalog;

    @BeforeEach
    void setup() throws Exception {
        registerProcedures(
            GraphProjectProc.class,
            GraphStreamNodePropertiesProc.class
        );
        String createQuery = GdsCypher.call(GRAPH_NAME)
            .graphProject()
            .withNodeLabel("N")
            .withRelationshipType("T", Orientation.UNDIRECTED)
            .withNodeProperties(List.of("a", "b", "c"), DefaultValue.DEFAULT)
            .yields();

        runQuery(createQuery);

        graphStore = GraphStoreCatalog.get(getUsername(), db.databaseId(), "g").graphStore();
    }

    @AfterEach
    void tearDown() {
        GraphStoreCatalog.removeAllLoadedGraphs();
    }

    @Test
    void shouldPredict() {
        TestProcedureRunner.applyOnProcedure(db, TestProc.class, caller -> {
            var config = LinkPredictionPredictPipelineStreamConfig.of(
                "",
                CypherMapWrapper.empty()
                    .withEntry("modelName", "model")
                    .withEntry("topN", 3)
                    .withEntry("graphName", GRAPH_NAME)
            );

            var pipeline = LinkPredictionPredictPipeline.from(
                Stream.of(),
                Stream.of(new L2FeatureStep(List.of("a", "b", "c")))
            );

            var modelData = ImmutableLogisticRegressionData.of(
                LinkPredictionTrain.makeClassIdMap(),
                new Weights<>(
                    new Matrix(
                        new double[]{2.0, 1.0, -3.0},
                        1,
                        3
                    )),
                Weights.ofVector(0.0)
            );

            var pipelineExecutor = new LinkPredictionPredictPipelineExecutor(
                pipeline,
                LogisticRegressionClassifier.from(modelData),
                config,
                caller.executionContext(),
                graphStore,
                GRAPH_NAME,
                ProgressTracker.NULL_TRACKER
            );

            var predictionResult = pipelineExecutor.compute();


            var predictedLinks = predictionResult.stream().collect(Collectors.toList());
            assertThat(predictedLinks).hasSize(3);

            assertThat(graphStore.relationshipTypes()).containsExactlyElementsOf(RelationshipType.listOf("T"));
            assertThat(graphStore.hasNodeProperty(graphStore.nodeLabels(), "degree")).isFalse();
        });
    }

    @Test
    void shouldPredictWithRandomForest() {
        TestProcedureRunner.applyOnProcedure(db, TestProc.class, caller -> {
            var config = LinkPredictionPredictPipelineStreamConfig.of(
                "",
                CypherMapWrapper.empty()
                    .withEntry("modelName", "model")
                    .withEntry("topN", 3)
                    .withEntry("graphName", GRAPH_NAME)
            );

            var pipeline = LinkPredictionPredictPipeline.from(
                Stream.of(),
                Stream.of(new L2FeatureStep(List.of("a", "b", "c")))
            );

            var root = new TreeNode<>(0);
            var modelData = ImmutableRandomForestClassifierData
                .builder()
                .addDecisionTree(new DecisionTreePredictor<>(root))
                .featureDimension(3)
                .classIdMap(LinkPredictionTrain.makeClassIdMap())
                .build();

            var pipelineExecutor = new LinkPredictionPredictPipelineExecutor(
                pipeline,
                new RandomForestClassifier(modelData),
                config,
                caller.executionContext(),
                graphStore,
                GRAPH_NAME,
                ProgressTracker.NULL_TRACKER
            );

            var predictionResult = pipelineExecutor.compute();


            var predictedLinks = predictionResult.stream().collect(Collectors.toList());
            assertThat(predictedLinks).hasSize(3);

            assertThat(graphStore.relationshipTypes()).containsExactlyElementsOf(RelationshipType.listOf("T"));
            assertThat(graphStore.hasNodeProperty(graphStore.nodeLabels(), "degree")).isFalse();
        });
    }

    @Test
    void shouldPredictWithNodePropertySteps() {
        TestProcedureRunner.applyOnProcedure(db, TestProc.class, caller -> {
            var config = LinkPredictionPredictPipelineStreamConfig.of(
                "",
                CypherMapWrapper.empty()
                    .withEntry("modelName", "model")
                    .withEntry("topN", 3)
                    .withEntry("graphName", GRAPH_NAME)
            );

            var pipeline = LinkPredictionPredictPipeline.from(
                Stream.of(NodePropertyStepFactory.createNodePropertyStep(
                    "degree",
                    Map.of("mutateProperty", "degree")
                )),
                Stream.of(new L2FeatureStep(List.of("a", "b", "c", "degree")))
            );

            var modelData = ImmutableLogisticRegressionData.of(
                LinkPredictionTrain.makeClassIdMap(),
                new Weights<>(
                    new Matrix(
                        new double[]{2.0, 1.0, -3.0, -1.0},
                        1,
                        4
                    )),
                Weights.ofVector(0.0)
            );

            var pipelineExecutor = new LinkPredictionPredictPipelineExecutor(
                pipeline,
                LogisticRegressionClassifier.from(modelData),
                config,
                caller.executionContext(),
                graphStore,
                GRAPH_NAME,
                ProgressTracker.NULL_TRACKER
            );

            var predictionResult = pipelineExecutor.compute();
            var predictedLinks = predictionResult.stream().collect(Collectors.toList());
            assertThat(predictedLinks).hasSize(3);

            assertThat(graphStore.relationshipTypes()).containsExactlyElementsOf(RelationshipType.listOf("T"));
            assertThat(graphStore.hasNodeProperty(graphStore.nodeLabels(), "degree")).isFalse();
        });
    }

    @Test
    void progressTracking() {
        TestProcedureRunner.applyOnProcedure(db, TestProc.class, caller -> {
            var config = LinkPredictionPredictPipelineStreamConfig.of(
                "",
                CypherMapWrapper.empty()
                    .withEntry("modelName", "model")
                    .withEntry("topN", 3)
                    .withEntry("graphName", GRAPH_NAME)
            );

            var pipeline = LinkPredictionPredictPipeline.from(
                Stream.of(NodePropertyStepFactory.createNodePropertyStep(
                    "degree",
                    Map.of("mutateProperty", "degree")
                )),
                Stream.of(new L2FeatureStep(List.of("a", "b", "c", "degree")))
            );

            var modelData = ImmutableLogisticRegressionData.of(
                LinkPredictionTrain.makeClassIdMap(),
                new Weights<>(
                    new Matrix(
                        new double[]{2.0, 1.0, -3.0, -1.0},
                        1,
                        4
                    )),
                Weights.ofVector(0.0)
            );

            modelCatalog.set(Model.of(
                getUsername(),
                "model",
                MODEL_TYPE,
                GraphSchema.empty(),
                modelData,
                LinkPredictionTrainConfigImpl.builder()
                    .username(getUsername())
                    .modelName("model")
                    .pipeline("DUMMY")
                    .graphName(GRAPH_NAME)
                    .negativeClassWeight(1.0)
                    .build(),
                LinkPredictionModelInfo.of(
                    Map.of(),
                    Map.of(),
                    ModelCandidateStats.of(LogisticRegressionTrainConfig.DEFAULT, Map.of(), Map.of()),
                    pipeline
                )
            ));

            var log = Neo4jProxy.testLog();
            var progressTracker = new TestProgressTracker(
                LinkPredictionPredictPipelineExecutor.progressTask(
                    "Link Prediction Predict Pipeline",
                    pipeline,
                    graphStore,
                    config
                ),
                log,
                1,
                EmptyTaskRegistryFactory.INSTANCE
            );

            var pipelineExecutor = new LinkPredictionPredictPipelineExecutor(
                pipeline,
                LogisticRegressionClassifier.from(modelData),
                config,
                caller.executionContext(),
                graphStore,
                GRAPH_NAME,
                progressTracker
            );

            pipelineExecutor.compute();

            var expectedMessages = new ArrayList<>(List.of(
                "Link Prediction Predict Pipeline :: Start",
                "Link Prediction Predict Pipeline :: Execute node property steps :: Start",
                "Link Prediction Predict Pipeline :: Execute node property steps :: Step 1 of 1 :: Start",
                "Link Prediction Predict Pipeline :: Execute node property steps :: Step 1 of 1 100%",
                "Link Prediction Predict Pipeline :: Execute node property steps :: Step 1 of 1 :: Finished",
                "Link Prediction Predict Pipeline :: Execute node property steps :: Finished",
                "Link Prediction Predict Pipeline :: Exhaustive link prediction :: Start",
                "Link Prediction Predict Pipeline :: Exhaustive link prediction 100%",
                "Link Prediction Predict Pipeline :: Exhaustive link prediction :: Finished",
                "Link Prediction Predict Pipeline :: Finished"
            ));

            assertThat(log.getMessages(INFO))
                .extracting(removingThreadId())
                .extracting(replaceTimings())
                .containsExactly(expectedMessages.toArray(String[]::new));
        });
    }

    @Test
    void shouldEstimateMemoryWithLogisticRegression() {
        var pipeline = LinkPredictionPredictPipeline.EMPTY;
        var modelData = ImmutableLogisticRegressionData.of(
            LinkPredictionTrain.makeClassIdMap(),
            new Weights<>(
                new Matrix(
                    new double[]{2.0, 1.0, -3.0, -1.0},
                    1,
                    4
                )),
            Weights.ofVector(0.0)
        );

        var config = new LinkPredictionPredictPipelineBaseConfigImpl.Builder()
            .concurrency(1)
            .graphName(GRAPH_NAME)
            .topN(10)
            .modelName("model")
            .username("user")
            .build();

        var memoryEstimation = LinkPredictionPredictPipelineExecutor.estimate(modelCatalog, pipeline, config, modelData);
        assertMemoryEstimation(
            () -> memoryEstimation,
            graphStore.nodeCount(),
            graphStore.relationshipCount(),
            config.concurrency(),
            MemoryRange.of(433)
        );
    }

    @Test
    void shouldEstimateMemoryWithRandomForest() {
        var pipeline = LinkPredictionPredictPipeline.EMPTY;
        var root = new TreeNode<>(0);
        var modelData = ImmutableRandomForestClassifierData
            .builder()
            .addDecisionTree(new DecisionTreePredictor<>(root))
            .featureDimension(2)
            .classIdMap(LinkPredictionTrain.makeClassIdMap())
            .build();

        var config = new LinkPredictionPredictPipelineBaseConfigImpl.Builder()
            .concurrency(1)
            .graphName(GRAPH_NAME)
            .topN(10)
            .modelName("model")
            .username("user")
            .build();

        var memoryEstimation = LinkPredictionPredictPipelineExecutor.estimate(modelCatalog, pipeline, config, modelData);
        assertMemoryEstimation(
            () -> memoryEstimation,
            graphStore.nodeCount(),
            graphStore.relationshipCount(),
            config.concurrency(),
            MemoryRange.of(489)
        );
    }
}
