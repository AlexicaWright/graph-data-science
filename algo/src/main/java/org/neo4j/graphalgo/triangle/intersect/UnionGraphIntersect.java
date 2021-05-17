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
package org.neo4j.graphalgo.triangle.intersect;

import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.graphalgo.api.AdjacencyCursor;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.huge.CompositeAdjacencyCursor;
import org.neo4j.graphalgo.core.huge.CompositeAdjacencyList;
import org.neo4j.graphalgo.core.huge.UnionGraph;

import java.util.ArrayList;
import java.util.function.LongToIntFunction;

public final class UnionGraphIntersect extends GraphIntersect<CompositeAdjacencyCursor> {

    private final LongToIntFunction degreeFunction;
    private final CompositeAdjacencyList compositeAdjacencyList;

    private UnionGraphIntersect(
        LongToIntFunction degreeFunction,
        CompositeAdjacencyList compositeAdjacencyList,
        long maxDegree
    ) {
        super(compositeAdjacencyList::rawDecompressingCursor, maxDegree);
        this.degreeFunction = degreeFunction;
        this.compositeAdjacencyList = compositeAdjacencyList;
    }

    @Override
    protected CompositeAdjacencyCursor cursor(long nodeId, int unusedDegree, CompositeAdjacencyCursor reuse) {
        var adjacencyCursors = new ArrayList<AdjacencyCursor>(compositeAdjacencyList.size());
        var cursorsIter = reuse.cursors().iterator();

        compositeAdjacencyList.forEachOffset(
            nodeId,
            (list, offset, degree) -> {
                var cursor = cursorsIter.next();
                if (offset != 0) {
                    cursor.init(offset, degree);
                }
                adjacencyCursors.add(cursor);
            }
        );
        return new CompositeAdjacencyCursor(adjacencyCursors);
    }

    @Override
    protected int degree(long nodeId) {
        return degreeFunction.applyAsInt(nodeId);
    }

    @ServiceProvider
    public static final class UnionGraphIntersectFactory implements RelationshipIntersectFactory {

        @Override
        public boolean canLoad(Graph graph) {
            return graph instanceof UnionGraph;
        }

        @Override
        public UnionGraphIntersect load(Graph graph, RelationshipIntersectConfig config) {
            assert graph instanceof UnionGraph;
            var topology = ((UnionGraph) graph).relationshipTopology();
            return new UnionGraphIntersect(
                graph::degree,
                topology,
                config.maxDegree()
            );
        }
    }
}
