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
package org.neo4j.gds;

import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.pipeline.ComputationResultConsumer;
import org.neo4j.gds.result.AbstractResultBuilder;

import java.util.stream.Stream;

public abstract class StatsProc<
    ALGO extends Algorithm<ALGO, ALGO_RESULT>,
    ALGO_RESULT,
    PROC_RESULT,
    CONFIG extends AlgoBaseConfig> extends AlgoBaseProc<ALGO, ALGO_RESULT, CONFIG, PROC_RESULT> {

    protected abstract AbstractResultBuilder<PROC_RESULT> resultBuilder(ComputationResult<ALGO, ALGO_RESULT, CONFIG> computeResult);

    @Override
    public ComputationResultConsumer<ALGO, ALGO_RESULT, CONFIG, Stream<PROC_RESULT>> computationResultConsumer() {
        return (computationResult, executionContext) -> runWithExceptionLogging("Stats call failed", () -> Stream.of(
            resultBuilder(computationResult)
                .withCreateMillis(computationResult.createMillis())
                .withComputeMillis(computationResult.computeMillis())
                .withNodeCount(computationResult.graph().nodeCount())
                .withConfig(computationResult.config())
                .build()
        ));
    }

    protected Stream<PROC_RESULT> stats(ComputationResult<ALGO, ALGO_RESULT, CONFIG> computeResult) {
        return computationResultConsumer().consume(computeResult, executionContext());
    }
}
