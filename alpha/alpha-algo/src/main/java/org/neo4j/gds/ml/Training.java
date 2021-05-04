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
package org.neo4j.gds.ml;

import org.neo4j.gds.embeddings.graphsage.ddl4j.ComputationContext;
import org.neo4j.gds.embeddings.graphsage.ddl4j.Variable;
import org.neo4j.gds.embeddings.graphsage.ddl4j.tensor.Scalar;
import org.neo4j.gds.ml.batch.Batch;
import org.neo4j.gds.ml.batch.BatchQueue;
import org.neo4j.gds.ml.nodemodels.logisticregression.NodeLogisticRegressionObjective;
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;

import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.neo4j.graphalgo.core.utils.mem.MemoryUsage.sizeOfInstance;

public class Training {
    private final TrainingConfig config;
    private final ProgressLogger progressLogger;
    private final long trainSize;

    public Training(TrainingConfig config, ProgressLogger progressLogger, long trainSize) {
        this.config = config;
        this.progressLogger = progressLogger;
        this.trainSize = trainSize;
    }

    public static MemoryEstimation memoryEstimation(int batchSize, int numberOfFeatures) {
        return MemoryEstimations.builder(Training.class)
            .add("updater", estimateUpdater())
            .add("train epoch", estimateTrainEpoch(batchSize, numberOfFeatures))
            .add("evaluate loss", estimateEvaluateLoss())
            .build();
    }

    private static MemoryEstimation estimateUpdater() {
        throw new UnsupportedOperationException("TODO");
    }

    private static MemoryEstimation estimateTrainEpoch(int batchSize, int numberOfFeatures) {
        return MemoryEstimations.builder()
            .perThread("consumer", sizeOfInstance(ObjectiveUpdateConsumer.class))
            .perThread("calculating loss", ObjectiveUpdateConsumer.sizeOfBatchInBytes(batchSize, numberOfFeatures))

            /*
            loss
            ctx
            forward
            backward
            update
             */
            .build();
    }

    private static MemoryEstimation estimateEvaluateLoss() {
        throw new UnsupportedOperationException("TODO");
    }

    public void train(Objective<?> objective, Supplier<BatchQueue> queueSupplier, int concurrency) {
        Updater[] updaters = new Updater[concurrency];
        updaters[0] = Updater.defaultUpdater(objective.weights());
        for (int i = 1; i < concurrency; i++) {
            updaters[i] = config.sharedUpdater() ? updaters[0] : Updater.defaultUpdater(objective.weights());
        }
        int epoch = 0;
        TrainingStopper stopper = TrainingStopper.defaultStopper(config);
        double initialLoss = evaluateLoss(objective, queueSupplier.get(), concurrency);
        double lastLoss = initialLoss;
        while (!stopper.terminated()) {
            trainEpoch(objective, queueSupplier.get(), concurrency, updaters);
            lastLoss = evaluateLoss(objective, queueSupplier.get(), concurrency);
            stopper.registerLoss(lastLoss);
            epoch++;
            progressLogger.logProgress();
            progressLogger.getLog().debug("Loss: %s, After Epoch: %d", lastLoss, epoch);
        }
        progressLogger.getLog().debug(
            "Training %s after %d epochs. Initial loss: %s, Last loss: %s.%s",
            stopper.converged() ? "converged" : "terminated",
            epoch,
            initialLoss,
            lastLoss,
            stopper.converged() ? "" : " Did not converge"
        );
    }

    private double evaluateLoss(Objective<?> objective, BatchQueue batches, int concurrency) {
        DoubleAdder totalLoss = new DoubleAdder();

        batches.parallelConsume(
            new LossEvalConsumer(
                objective,
                totalLoss,
                trainSize
            ),
            concurrency
        );

        return totalLoss.doubleValue();
    }

    private void trainEpoch(
        Objective<?> objective,
        BatchQueue batches,
        int concurrency,
        Updater[] updaters
    ) {
        batches.parallelConsume(
            concurrency,
            jobId ->
                new ObjectiveUpdateConsumer(
                    objective,
                    updaters[jobId],
                    trainSize
                )
        );
    }

    static class ObjectiveUpdateConsumer implements Consumer<Batch> {
        private final Objective<?> objective;
        private final Updater updater;
        private final long trainSize;

        ObjectiveUpdateConsumer(Objective<?> objective, Updater updater, long trainSize) {
            this.objective = objective;
            this.updater = updater;
            this.trainSize = trainSize;
        }

        static long sizeOfBatchInBytes(int batchSize, int numberOfFeatures) {
            return NodeLogisticRegressionObjective.sizeOfBatchInBytes(batchSize, numberOfFeatures); // yeah yeah polymorphism :facepalm:
        }

        @Override
        public void accept(Batch batch) {
            Variable<Scalar> loss = objective.loss(batch, trainSize);
            ComputationContext ctx = new ComputationContext();
            ctx.forward(loss);
            ctx.backward(loss);
            updater.update(ctx);
        }
    }

    static class LossEvalConsumer implements Consumer<Batch> {
        private final Objective<?> objective;
        private final DoubleAdder totalLoss;
        private final long trainSize;

        LossEvalConsumer(Objective<?> objective, DoubleAdder lossAdder, long trainSize) {
            this.objective = objective;
            this.totalLoss = lossAdder;
            this.trainSize = trainSize;
        }

        @Override
        public void accept(Batch batch) {
            Variable<Scalar> loss = objective.loss(batch, trainSize);
            ComputationContext ctx = new ComputationContext();
            totalLoss.add(ctx.forward(loss).value());
        }

    }
}
