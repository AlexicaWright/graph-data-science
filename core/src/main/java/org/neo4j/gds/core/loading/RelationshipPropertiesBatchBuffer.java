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

import org.neo4j.gds.compat.PropertyReference;
import org.neo4j.gds.core.Aggregation;

public class RelationshipPropertiesBatchBuffer implements RelationshipImporter.PropertyReader {

    private final long[][] buffer;
    private final int propertyCount;

    public RelationshipPropertiesBatchBuffer(int batchSize, int propertyCount) {
        this.propertyCount = propertyCount;
        this.buffer = new long[propertyCount][batchSize];
    }

    public void add(int relationshipId, int propertyKeyId, double property) {
        buffer[propertyKeyId][relationshipId] = Double.doubleToLongBits(property);
    }

    @Override
    public long[][] readProperty(
        long[] relationshipReferences,
        PropertyReference[] propertyReferences,
        int numberOfReferences,
        int[] propertyKeyIds,
        double[] defaultValues,
        Aggregation[] aggregations,
        boolean atLeastOnePropertyToLoad
    ) {
        long[][] resultBuffer = new long[propertyCount][numberOfReferences];

        for (int propertyKeyId = 0; propertyKeyId < propertyCount; propertyKeyId++) {
            long[] propertyValues = new long[numberOfReferences];
            for (int relationshipOffset = 0; relationshipOffset < numberOfReferences; relationshipOffset++) {
                int relationshipId = (int) relationshipReferences[relationshipOffset];
                // We need to fill this consecutively indexed
                // in the same order as the relationships are
                // stored in the batch.
                propertyValues[relationshipOffset] = buffer[propertyKeyId][relationshipId];
            }
            resultBuffer[propertyKeyId] = propertyValues;
        }

        return resultBuffer;
    }
}
