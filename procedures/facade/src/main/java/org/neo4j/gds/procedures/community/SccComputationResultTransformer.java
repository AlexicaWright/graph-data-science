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
package org.neo4j.gds.procedures.community;

import org.neo4j.gds.algorithms.NodePropertyMutateResult;
import org.neo4j.gds.algorithms.StandardCommunityStatisticsSpecificFields;
import org.neo4j.gds.algorithms.StatsResult;
import org.neo4j.gds.algorithms.StreamComputationResult;
import org.neo4j.gds.algorithms.community.CommunityResultCompanion;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.api.properties.nodes.NodePropertyValuesAdapter;
import org.neo4j.gds.collections.ha.HugeLongArray;
import org.neo4j.gds.procedures.community.scc.SccMutateResult;
import org.neo4j.gds.procedures.community.scc.SccStatsResult;
import org.neo4j.gds.procedures.community.scc.SccStreamResult;
import org.neo4j.gds.scc.SccStatsConfig;
import org.neo4j.gds.scc.SccStreamConfig;

import java.util.Optional;
import java.util.stream.LongStream;
import java.util.stream.Stream;

final class SccComputationResultTransformer {

    private SccComputationResultTransformer() {}

    static Stream<SccStreamResult> toStreamResult(
        StreamComputationResult<HugeLongArray> computationResult,
        SccStreamConfig configuration
    ) {
        return computationResult.result().map(sccResult -> {
            var graph = computationResult.graph();
            var nodePropertyValues = CommunityResultCompanion.nodePropertyValues(
                false,
                configuration.consecutiveIds(),
                NodePropertyValuesAdapter.adapt(sccResult),
                Optional.empty(),
                configuration.concurrency()
            );
            return LongStream.range(IdMap.START_NODE_ID, graph.nodeCount())
                .filter(nodePropertyValues::hasValue)
                .mapToObj(i -> new SccStreamResult(
                    graph.toOriginalNodeId(i),
                    nodePropertyValues.longValue(i)
                ));

        }).orElseGet(Stream::empty);
    }

    static SccMutateResult toMutateResult(NodePropertyMutateResult<StandardCommunityStatisticsSpecificFields> computationResult) {
        return new SccMutateResult(
            computationResult.algorithmSpecificFields().communityCount(),
            computationResult.algorithmSpecificFields().communityDistribution(),
            computationResult.preProcessingMillis(),
            computationResult.computeMillis(),
            computationResult.postProcessingMillis(),
            computationResult.mutateMillis(),
            computationResult.nodePropertiesWritten(),
            computationResult.configuration().toMap()
        );
    }

    static SccStatsResult toStatsResult(
        StatsResult<StandardCommunityStatisticsSpecificFields> computationResult,
        SccStatsConfig config
    ) {
        return new SccStatsResult(
            computationResult.algorithmSpecificFields().communityCount(),
            computationResult.algorithmSpecificFields().communityDistribution(),
            computationResult.preProcessingMillis(),
            computationResult.computeMillis(),
            computationResult.postProcessingMillis(),
            config.toMap()
        );
    }

}
