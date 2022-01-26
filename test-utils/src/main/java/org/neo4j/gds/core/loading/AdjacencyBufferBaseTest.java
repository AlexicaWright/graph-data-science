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
package org.neo4j.gds.core.loading;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.RelationshipProjection;
import org.neo4j.gds.core.Aggregation;
import org.neo4j.gds.core.compress.AdjacencyListBehavior;
import org.neo4j.gds.core.huge.DirectIdMap;
import org.neo4j.gds.core.utils.mem.AllocationTracker;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.gds.core.loading.AdjacencyPreAggregation.IGNORE_VALUE;

public abstract class AdjacencyBufferBaseTest {

    protected void testAdjacencyList(AdjacencyListBehavior adjacencyListBehavior) {
        var nodeCount = 6;

        AdjacencyListWithPropertiesBuilder globalBuilder = AdjacencyListWithPropertiesBuilder.create(
            () -> nodeCount,
            adjacencyListBehavior,
            RelationshipProjection.of("", Orientation.UNDIRECTED, Aggregation.NONE),
            new Aggregation[]{Aggregation.NONE},
            new int[0],
            new double[0],
            AllocationTracker.empty()
        );

        AdjacencyBuffer adjacencyBuffer = new AdjacencyBufferBuilder()
            .globalBuilder(globalBuilder)
            .importSizing(ImportSizing.of(1, nodeCount))
            .preAggregate(false)
            .allocationTracker(AllocationTracker.empty())
            .build();

        DirectIdMap idMap = new DirectIdMap(nodeCount);

        RelationshipsBatchBuffer relationshipsBatchBuffer = new RelationshipsBatchBuffer(idMap, -1, 10);
        Map<Long, Long> relationships = new HashMap<>();
        for (long i = 0; i < nodeCount; i++) {
            relationships.put(i, nodeCount - i);
            relationshipsBatchBuffer.add(i, nodeCount - i);
        }

        RelationshipImporter relationshipImporter = new RelationshipImporter(
            adjacencyBuffer,
            AllocationTracker.empty()
        );
        RelationshipImporter.Imports imports = relationshipImporter.imports(Orientation.NATURAL, false);
        imports.importRelationships(relationshipsBatchBuffer, null);

        adjacencyBuffer.adjacencyListBuilderTasks().forEach(Runnable::run);

        try (var adjacencyList = globalBuilder.build().adjacency()) {
            for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
                try (var cursor = adjacencyList.adjacencyCursor(nodeId)) {
                    while (cursor.hasNextVLong()) {
                        long target = cursor.nextVLong();
                        assertEquals(relationships.remove(nodeId), target);
                    }
                }
            }
            assertTrue(relationships.isEmpty());
        }
    }

    @Test
    // TODO test single case
    // TODO add testcase that sets a range
    void testAggregation() {
        var values = new long[]{3, 1, 2, 2, 3, 1};

        var properties = new long[2][values.length];
        properties[0] = new long[]{1, 1, 1, 1, 1, 1};
        properties[1] = new long[]{1, 2, 3, 4, 5, 6};

        var aggregations = new Aggregation[]{Aggregation.SUM, Aggregation.MAX};

        AdjacencyPreAggregation.preAggregate(values, properties, 0, values.length, aggregations);

        assertArrayEquals(
            new long[]{3, 1, 2, IGNORE_VALUE, IGNORE_VALUE, IGNORE_VALUE},
            values
        );

        assertArrayEquals(
            new long[]{2, 2, 2, 1, 1, 1},
            properties[0]
        );

        assertArrayEquals(
            new long[]{5, 6, 4, 4, 5, 6},
            properties[1]
        );
    }
}
