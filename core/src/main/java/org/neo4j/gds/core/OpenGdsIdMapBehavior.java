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
package org.neo4j.gds.core;

import org.neo4j.gds.core.loading.HugeIdMap;
import org.neo4j.gds.core.loading.HugeIdMapBuilder;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;

import java.util.Optional;

public class OpenGdsIdMapBehavior implements IdMapBehavior {

    @Override
    public HugeIdMapBuilder create(
        Optional<Long> maxOriginalId,
        Optional<Long> nodeCount,
        AllocationTracker allocationTracker
    ) {
        long capacity = nodeCount.orElseGet(() -> maxOriginalId
            .map(maxId -> maxId + 1)
            .orElseThrow(() -> new IllegalArgumentException("Either `maxOriginalId` or `nodeCount` must be set")));

        return HugeIdMapBuilder.of(capacity, allocationTracker);
    }

    @Override
    public MemoryEstimation memoryEstimation() {
        return HugeIdMap.memoryEstimation();
    }
}
