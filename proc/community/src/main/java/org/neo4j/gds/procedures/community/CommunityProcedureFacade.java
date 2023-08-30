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

import org.neo4j.gds.algorithms.community.CommunityAlgorithmsBusinessFacade;
import org.neo4j.gds.api.AlgorithmMetaDataSetter;
import org.neo4j.gds.api.DatabaseId;
import org.neo4j.gds.api.ProcedureReturnColumns;
import org.neo4j.gds.api.User;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.kcore.KCoreDecompositionStreamConfig;
import org.neo4j.gds.wcc.WccMutateConfig;
import org.neo4j.gds.wcc.WccMutateResult;
import org.neo4j.gds.wcc.WccStreamConfig;

import java.util.Map;
import java.util.stream.Stream;

public class CommunityProcedureFacade {
    private final CommunityAlgorithmsBusinessFacade algorithmsBusinessFacade;
    private final ProcedureReturnColumns procedureReturnColumns;
    private final DatabaseId databaseId;
    private final User user;

    public CommunityProcedureFacade(
        CommunityAlgorithmsBusinessFacade algorithmsBusinessFacade,
        ProcedureReturnColumns procedureReturnColumns,
        DatabaseId databaseId,
        User user
    ) {
        this.algorithmsBusinessFacade = algorithmsBusinessFacade;
        this.procedureReturnColumns = procedureReturnColumns;
        this.databaseId = databaseId;
        this.user = user;
    }

    // WCC

    public Stream<WccStreamResult> wccStream(
        String graphName,
        Map<String, Object> configuration,
        AlgorithmMetaDataSetter algorithmMetaDataSetter
    ) {
        var streamConfig = WccStreamConfig.of(CypherMapWrapper.create(configuration));

        // This is needed because of `com.neo4j.gds.ProcedureSignatureGuard` 🤦
        algorithmMetaDataSetter.set(streamConfig);

        var computationResult = algorithmsBusinessFacade.streamWcc(
            graphName,
            streamConfig,
            user,
            databaseId
        );

        return WccComputationResultTransformer.toStreamResult(computationResult);
    }

    public Stream<WccMutateResult> wccMutate(
        String graphName,
        Map<String, Object> configuration
    ) {
        var config = WccMutateConfig.of(CypherMapWrapper.create(configuration));

        var computationResult = algorithmsBusinessFacade.mutateWcc(
            graphName,
            config,
            user,
            databaseId,
            ProgressTracker.NULL_TRACKER,
            procedureReturnColumns.contains("componentCount"),
            procedureReturnColumns.contains("componentDistribution")
        );

        return Stream.of(WccComputationResultTransformer.toMutateResult(computationResult));
    }

    // WCC end

    // K-Core Decomposition
    public Stream<KCoreStreamResult> kCoreStream(
        String graphName,
        Map<String, Object> configuration,
        AlgorithmMetaDataSetter algorithmMetaDataSetter
    ) {
        var streamConfig = KCoreDecompositionStreamConfig.of(CypherMapWrapper.create(configuration));

        // This is needed because of `com.neo4j.gds.ProcedureSignatureGuard` 🤦
        algorithmMetaDataSetter.set(streamConfig);

        var computationResult = algorithmsBusinessFacade.streamKCore(
            graphName,
            streamConfig,
            user,
            databaseId
        );

        return KCoreComputationalResultTransformer.toStreamResult(computationResult);
    }

    // K-Core Decomposition end
}
