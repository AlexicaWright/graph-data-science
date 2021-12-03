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
import org.neo4j.gds.impl.similarity.PearsonAlgorithm;
import org.neo4j.gds.impl.similarity.PearsonConfig;
import org.neo4j.gds.impl.similarity.PearsonConfigImpl;
import org.neo4j.gds.results.SimilarityResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;
import static org.neo4j.procedure.Mode.WRITE;

public final class PearsonProc extends AlphaSimilarityProc<PearsonAlgorithm, PearsonConfig> {

    private static final String DESCRIPTION =
        "Pearson-similarity is an algorithm for finding similar nodes " +
        "based on the pearson correlation coefficient.";

    @Procedure(name = "gds.alpha.similarity.pearson.stream", mode = READ)
    @Description(DESCRIPTION)
    public Stream<SimilarityResult> pearsonStream(
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return stream(configuration);
    }

    @Procedure(name = "gds.alpha.similarity.pearson.write", mode = WRITE)
    @Description(DESCRIPTION)
    public Stream<AlphaSimilaritySummaryResult> pearsonWrite(
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return write(configuration);
    }

    @Procedure(name = "gds.alpha.similarity.pearson.stats", mode = READ)
    @Description(DESCRIPTION)
    public Stream<AlphaSimilarityStatsResult> pearsonStats(
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return stats(configuration);
    }

    @Override
    protected PearsonConfig newConfig(
        String username,
        Optional<String> graphName,
        CypherMapWrapper userInput
    ) {
        return new PearsonConfigImpl(graphName, userInput);
    }

    @Override
    PearsonAlgorithm newAlgo(PearsonConfig config, AllocationTracker allocationTracker) {
        return new PearsonAlgorithm(config, api);
    }

    @Override
    String taskName() {
        return "Pearson-similarity";
    }
}
