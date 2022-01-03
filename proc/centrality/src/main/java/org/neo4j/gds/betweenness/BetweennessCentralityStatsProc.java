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
package org.neo4j.gds.betweenness;

import org.jetbrains.annotations.Nullable;
import org.neo4j.gds.GraphAlgorithmFactory;
import org.neo4j.gds.StatsProc;
import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.utils.paged.HugeAtomicDoubleArray;
import org.neo4j.gds.pipeline.ExecutionContext;
import org.neo4j.gds.pipeline.GdsCallable;
import org.neo4j.gds.pipeline.validation.ValidationConfiguration;
import org.neo4j.gds.result.AbstractResultBuilder;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.gds.results.StandardStatsResult;
import org.neo4j.internal.kernel.api.procs.ProcedureCallContext;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.gds.betweenness.BetweennessCentralityProc.BETWEENNESS_DESCRIPTION;
import static org.neo4j.gds.pipeline.ExecutionMode.STATS;
import static org.neo4j.procedure.Mode.READ;

@GdsCallable(name = "gds.betweenness.stats", description = BETWEENNESS_DESCRIPTION, executionMode = STATS)
public class BetweennessCentralityStatsProc extends StatsProc<BetweennessCentrality, HugeAtomicDoubleArray, BetweennessCentralityStatsProc.StatsResult, BetweennessCentralityStatsConfig> {

    @Procedure(value = "gds.betweenness.stats", mode = READ)
    @Description(BETWEENNESS_DESCRIPTION)
    public Stream<StatsResult> stats(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return stats(compute(graphName, configuration));
    }

    @Procedure(value = "gds.betweenness.stats.estimate", mode = READ)
    @Description(BETWEENNESS_DESCRIPTION)
    public Stream<MemoryEstimateResult> estimate(
        @Name(value = "graphNameOrConfiguration") Object graphNameOrConfiguration,
        @Name(value = "algoConfiguration") Map<String, Object> algoConfiguration
    ) {
        return computeEstimate(graphNameOrConfiguration, algoConfiguration);
    }

    @Override
    protected BetweennessCentralityStatsConfig newConfig(String username, CypherMapWrapper config) {
        return BetweennessCentralityStatsConfig.of(config);
    }

    @Override
    public ValidationConfiguration<BetweennessCentralityStatsConfig> validationConfig() {
        return BetweennessCentralityProc.getValidationConfig();
    }

    @Override
    public GraphAlgorithmFactory<BetweennessCentrality, BetweennessCentralityStatsConfig> algorithmFactory() {
        return BetweennessCentralityProc.algorithmFactory();
    }

    @Override
    protected NodeProperties nodeProperties(ComputationResult<BetweennessCentrality, HugeAtomicDoubleArray, BetweennessCentralityStatsConfig> computationResult) {
        return BetweennessCentralityProc.nodeProperties(computationResult);
    }

    @Override
    protected AbstractResultBuilder<StatsResult> resultBuilder(
        ComputationResult<BetweennessCentrality, HugeAtomicDoubleArray, BetweennessCentralityStatsConfig> computeResult,
        ExecutionContext executionContext
    ) {
        return BetweennessCentralityProc.resultBuilder(new StatsResult.Builder(
            callContext,
            computeResult.config().concurrency()
        ), computeResult);
    }

    @SuppressWarnings("unused")
    public static class StatsResult extends StandardStatsResult {

        public final Map<String, Object> centralityDistribution;
        @Deprecated
        public final double minimumScore;
        @Deprecated
        public final double maximumScore;
        @Deprecated
        public final double scoreSum;

        StatsResult(
            @Nullable Map<String, Object> centralityDistribution,
            double scoreSum,
            double minimumScore,
            double maximumScore,
            long preProcessingMillis,
            long computeMillis,
            long postProcessingMillis,
            Map<String, Object> configuration
        ) {
            super(preProcessingMillis, computeMillis, postProcessingMillis, configuration);
            this.centralityDistribution = centralityDistribution;
            this.maximumScore = maximumScore;
            this.minimumScore = minimumScore;
            this.scoreSum = scoreSum;
        }

        static final class Builder extends BetweennessCentralityProc.BetweennessCentralityResultBuilder<StatsResult> {
            protected Builder(ProcedureCallContext callContext, int concurrency) {
                super(callContext, concurrency);
            }

            @Override
            public StatsResult buildResult() {
                return new StatsResult(
                    centralityHistogram,
                    sumCentrality,
                    minCentrality,
                    maxCentrality,
                    preProcessingMillis,
                    computeMillis,
                    postProcessingMillis,
                    config.toMap()
                );
            }
        }
    }
}
