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
package org.neo4j.gds.similarity;

import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.impl.similarity.EuclideanAlgorithm;
import org.neo4j.gds.impl.similarity.EuclideanConfig;
import org.neo4j.gds.impl.similarity.EuclideanConfigImpl;
import org.neo4j.gds.results.SimilarityResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;
import static org.neo4j.procedure.Mode.WRITE;

public class EuclideanProc extends AlphaSimilarityProc<EuclideanAlgorithm, EuclideanConfig> {

    private static final String DESCRIPTION = "Euclidean-similarity is an algorithm for finding similar nodes based on the euclidean distance.";

    @Procedure(name = "gds.alpha.similarity.euclidean.stream", mode = READ)
    @Description(DESCRIPTION)
    public Stream<SimilarityResult> euclideanStream(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return stream(graphName, configuration);
    }

    @Procedure(name = "gds.alpha.similarity.euclidean.write", mode = WRITE)
    @Description(DESCRIPTION)
    public Stream<AlphaSimilaritySummaryResult> euclideanWrite(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return write(graphName, configuration);
    }

    @Procedure(name = "gds.alpha.similarity.euclidean.stats", mode = READ)
    @Description(DESCRIPTION)
    public Stream<AlphaSimilarityStatsResult> euclideanStats(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return stats(graphName, configuration);
    }

    @Override
    protected EuclideanConfig newConfig(
        String username,
        Optional<String> graphName,
        CypherMapWrapper userInput
    ) {
        return new EuclideanConfigImpl(graphName, userInput);
    }

    @Override
    EuclideanAlgorithm newAlgo(EuclideanConfig config, AllocationTracker allocationTracker) {
        return new EuclideanAlgorithm(config, api);
    }

    @Override
    String taskName() {
        return "Euclidean-similarity";
    }
}
