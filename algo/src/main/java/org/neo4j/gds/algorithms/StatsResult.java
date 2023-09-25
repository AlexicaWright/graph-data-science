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
package org.neo4j.gds.algorithms;

import org.immutables.value.Value;
import org.neo4j.gds.annotation.ValueClass;

@ValueClass
public interface StatsResult<ALGORITHM_SPECIFIC_FIELDS>  {

    @Value.Default
    default long preProcessingMillis() {
        return 0L;
    }

    long computeMillis();
    long postProcessingMillis();

    ALGORITHM_SPECIFIC_FIELDS algorithmSpecificFields();

    static <ASF> ImmutableStatsResult.Builder<ASF> builder() {
        return ImmutableStatsResult.builder();
    }

    static <ASF> StatsResult<ASF> empty(ASF algorithmSpecificFields) {
        return StatsResult.<ASF>builder()
            .computeMillis(0)
            .postProcessingMillis(0)
            .algorithmSpecificFields(algorithmSpecificFields)
            .build();
    }

}
