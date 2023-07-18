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
package org.neo4j.gds.approxmaxkcut;

import org.neo4j.gds.api.properties.nodes.LongNodePropertyValues;
import org.neo4j.gds.core.utils.paged.HugeByteArray;

/**
 * NodePropertyValues backed by HugeByteArray
 */
final class NodePropertyValuesAdapter implements LongNodePropertyValues {

    private final HugeByteArray delegate;

    private NodePropertyValuesAdapter(HugeByteArray delegate) {this.delegate = delegate;}

    static LongNodePropertyValues create(HugeByteArray hugeByteArray) {
        return new NodePropertyValuesAdapter(hugeByteArray);
    }

    @Override
    public long longValue(long nodeId) {
        return delegate.get(nodeId);
    }

    @Override
    public long nodeCount() {
        return delegate.size();
    }
}
