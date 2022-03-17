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
package org.neo4j.gds.beta.closeness;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.AlgoBaseProc;
import org.neo4j.gds.GdsCypher;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.core.CypherMapWrapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

class ClosenessCentralityWriteProcTest extends ClosenessCentralityProcTest<ClosenessCentralityWriteConfig> {

    private static final String WRITE_PROPERTY = "score";

    @Override
    public Class<? extends AlgoBaseProc<ClosenessCentrality, ClosenessCentralityResult, ClosenessCentralityWriteConfig, ?>> getProcedureClazz() {
        return ClosenessCentralityWriteProc.class;
    }

    @Override
    public ClosenessCentralityWriteConfig createConfig(CypherMapWrapper mapWrapper) {
        return ClosenessCentralityWriteConfig.of(mapWrapper);
    }

    @Override
    public CypherMapWrapper createMinimalConfig(CypherMapWrapper mapWrapper) {
        if (!mapWrapper.containsKey("writeProperty")) {
            return mapWrapper.withString("writeProperty", WRITE_PROPERTY);
        }
        return mapWrapper;
    }

    @Test
    void testClosenessWrite() {
        loadGraph(DEFAULT_GRAPH_NAME, Orientation.UNDIRECTED);
        var query = GdsCypher.call(DEFAULT_GRAPH_NAME)
            .algo("gds.beta.closeness")
            .writeMode()
            .addParameter("writeProperty", WRITE_PROPERTY)
            .yields();

        runQueryWithRowConsumer(query, row -> {

            assertThat(row.get("configuration"))
                .isNotNull()
                .isInstanceOf(Map.class);

            assertThat(row.getNumber("writeMillis")).isNotEqualTo(-1L);
            assertThat(row.getNumber("preProcessingMillis")).isNotEqualTo(-1L);
            assertThat(row.getNumber("computeMillis")).isNotEqualTo(-1L);
            assertThat(row.getNumber("nodePropertiesWritten")).isEqualTo(11L);

            assertThat(row.get("centralityDistribution")).isEqualTo(Map.of(
                "max", 1.0000038146972656,
                "mean", 0.6256675720214844,
                "min", 0.5882339477539062,
                "p50", 0.5882339477539062,
                "p75", 0.5882339477539062,
                "p90", 0.5882339477539062,
                "p95", 0.5882339477539062,
                "p99", 1.0000038146972656,
                "p999", 1.0000038146972656
            ));
        });

        assertCypherResult(
            formatWithLocale("MATCH (n) WHERE exists(n.%1$s) RETURN id(n) AS nodeId, n.%1$s AS %1$s", WRITE_PROPERTY),
            expectedCentralityResult
        );
    }
}
