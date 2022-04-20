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
package org.neo4j.gds.ml.nodeClassification;

import org.jetbrains.annotations.Nullable;
import org.neo4j.gds.Algorithm;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.utils.TerminationFlag;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.gds.core.utils.mem.MemoryEstimations;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.core.utils.paged.HugeObjectArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.core.utils.progress.tasks.Tasks;
import org.neo4j.gds.ml.core.batch.BatchQueue;
import org.neo4j.gds.ml.models.Classifier;
import org.neo4j.gds.ml.models.ClassifierFactory;
import org.neo4j.gds.ml.models.Features;
import org.neo4j.gds.ml.models.TrainingMethod;
import org.neo4j.gds.ml.models.logisticregression.LogisticRegressionClassifier;

import java.util.Optional;

import static org.neo4j.gds.mem.MemoryUsage.sizeOfDoubleArray;

public class NodeClassificationPredict extends Algorithm<NodeClassificationPredict.NodeClassificationResult> {

    private final Classifier classifier;
    private final Features features;
    private final boolean produceProbabilities;
    private final ParallelNodeClassifier predictor;

    public NodeClassificationPredict(
        Classifier classifier,
        Features features,
        int batchSize,
        int concurrency,
        boolean produceProbabilities,
        ProgressTracker progressTracker,
        TerminationFlag terminationFlag
    ) {
        super(progressTracker);
        this.classifier = classifier;
        this.features = features;
        this.produceProbabilities = produceProbabilities;
        this.terminationFlag = terminationFlag;

        this.predictor = new ParallelNodeClassifier(
            classifier,
            features,
            batchSize,
            concurrency,
            terminationFlag,
            progressTracker
        );
    }

    public static Task progressTask(Graph graph) {
        return Tasks.leaf("Node classification predict", graph.nodeCount());
    }

    public static MemoryEstimation memoryEstimation(
        boolean produceProbabilities,
        int batchSize,
        int featureCount,
        int classCount
    ) {
        var builder = MemoryEstimations.builder(NodeClassificationPredict.class);
        if (produceProbabilities) {
            builder.perNode(
                "predicted probabilities",
                nodeCount -> HugeObjectArray.memoryEstimation(nodeCount, sizeOfDoubleArray(classCount))
            );
        }
        builder.perNode("predicted classes", HugeLongArray::memoryEstimation);
        builder.fixed(
            "computation graph",
            LogisticRegressionClassifier.sizeOfPredictionsVariableInBytes(
                batchSize,
                featureCount,
                classCount,
                classCount
            )
        );
        return builder.build();
    }

    public static MemoryEstimation memoryEstimationWithDerivedBatchSize(
        TrainingMethod method,
        boolean produceProbabilities,
        int minBatchSize,
        int featureCount,
        int classCount,
        boolean isReduced
    ) {
        var builder = MemoryEstimations.builder(NodeClassificationPredict.class);
        if (produceProbabilities) {
            builder.perNode(
                "predicted probabilities",
                nodeCount -> HugeObjectArray.memoryEstimation(nodeCount, sizeOfDoubleArray(classCount))
            );
        }
        builder.perNode("predicted classes", HugeLongArray::memoryEstimation);
        builder.perGraphDimension(
            "classifier runtime",
            (dim, threads) ->
                ClassifierFactory.runtimeOverheadMemoryEstimation(
                    method,
                    (int) Math.min(dim.nodeCount(), BatchQueue.computeBatchSize(dim.nodeCount(), minBatchSize, threads)),
                    classCount,
                    featureCount,
                    isReduced
                )
        );
        return builder.build();
    }


    @Override
    public NodeClassificationResult compute() {
        progressTracker.beginSubTask();
        var predictedProbabilities = initProbabilities();
        var predictedClasses = predictor.predict(predictedProbabilities);
        progressTracker.endSubTask();

        return NodeClassificationResult.of(predictedClasses, predictedProbabilities);
    }

    @Override
    public void release() {

    }

    private @Nullable HugeObjectArray<double[]> initProbabilities() {
        if (produceProbabilities) {
            var numberOfClasses = classifier.numberOfClasses();
            var predictions = HugeObjectArray.newArray(
                double[].class,
                features.size()
            );
            predictions.setAll(i -> new double[numberOfClasses]);
            return predictions;
        } else {
            return null;
        }
    }

    @ValueClass
    public interface NodeClassificationResult {

        HugeLongArray predictedClasses();

        Optional<HugeObjectArray<double[]>> predictedProbabilities();

        static NodeClassificationResult of(
            HugeLongArray classes,
            @Nullable HugeObjectArray<double[]> probabilities
        ) {
            return ImmutableNodeClassificationResult.builder()
                .predictedProbabilities(Optional.ofNullable(probabilities))
                .predictedClasses(classes)
                .build();
        }
    }
}
