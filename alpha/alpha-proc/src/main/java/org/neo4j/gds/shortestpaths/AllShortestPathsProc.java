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
package org.neo4j.gds.shortestpaths;

import org.neo4j.gds.AlgoBaseProc;
import org.neo4j.gds.GraphAlgorithmFactory;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.concurrency.Pools;
import org.neo4j.gds.core.utils.TerminationFlag;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.impl.msbfs.AllShortestPathsStream;
import org.neo4j.gds.impl.msbfs.MSBFSASPAlgorithm;
import org.neo4j.gds.impl.msbfs.MSBFSAllShortestPaths;
import org.neo4j.gds.impl.msbfs.WeightedAllShortestPaths;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;

public class AllShortestPathsProc extends AlgoBaseProc<MSBFSASPAlgorithm, Stream<AllShortestPathsStream.Result>, AllShortestPathsConfig, AllShortestPathsStream.Result> {

    private static final String DESCRIPTION = "The All Pairs Shortest Path (APSP) calculates the shortest (weighted) path between all pairs of nodes.";

    @Procedure(name = "gds.alpha.allShortestPaths.stream", mode = READ)
    @Description(DESCRIPTION)
    public Stream<AllShortestPathsStream.Result> stream(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        ComputationResult<MSBFSASPAlgorithm, Stream<AllShortestPathsStream.Result>, AllShortestPathsConfig> computationResult =
            compute(graphName, configuration, false, false);

        if (computationResult.isGraphEmpty()) {
            computationResult.graph().release();
            return Stream.empty();
        }

        return computationResult.result();
    }

    @Override
    protected AllShortestPathsConfig newConfig(String username, CypherMapWrapper config) {
        return AllShortestPathsConfig.of(config);
    }

    @Override
    public GraphAlgorithmFactory<MSBFSASPAlgorithm, AllShortestPathsConfig> algorithmFactory() {
        return new GraphAlgorithmFactory<>() {
            @Override
            public String taskName() {
                return "MSBFSASPAlgorithm";
            }

            @Override
            public MSBFSASPAlgorithm build(
                Graph graph,
                AllShortestPathsConfig configuration,
                AllocationTracker allocationTracker,
                ProgressTracker progressTracker
            ) {
                if (configuration.hasRelationshipWeightProperty()) {
                    return new WeightedAllShortestPaths(
                        graph,
                        Pools.DEFAULT,
                        configuration.concurrency()
                    )
                        .withTerminationFlag(TerminationFlag.wrap(transaction));
                } else {
                    return new MSBFSAllShortestPaths(
                        graph,
                        allocationTracker,
                        configuration.concurrency(),
                        Pools.DEFAULT
                    )
                        .withTerminationFlag(TerminationFlag.wrap(transaction));
                }
            }
        };
    }
}
