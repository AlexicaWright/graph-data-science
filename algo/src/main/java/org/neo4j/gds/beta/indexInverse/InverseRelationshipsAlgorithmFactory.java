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
package org.neo4j.gds.beta.indexInverse;

import org.neo4j.gds.GraphStoreAlgorithmFactory;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.core.compress.AdjacencyListBehavior;
import org.neo4j.gds.core.concurrency.Pools;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.gds.core.utils.mem.MemoryEstimations;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.core.utils.progress.tasks.Tasks;

public class InverseRelationshipsAlgorithmFactory extends GraphStoreAlgorithmFactory<InverseRelationships, InverseRelationshipsConfig> {

    @Override
    public InverseRelationships build(
        GraphStore graphStore,
        InverseRelationshipsConfig configuration,
        ProgressTracker progressTracker
    ) {
        return new InverseRelationships(graphStore, configuration, progressTracker, Pools.DEFAULT);
    }

    @Override
    public String taskName() {
        return "IndexInverse";
    }

    @Override
    public Task progressTask(GraphStore graphStore, InverseRelationshipsConfig config) {
        long nodeCount = graphStore.nodeCount();
        return Tasks.task(
            taskName(),
            Tasks.leaf("Create inverse relationships", nodeCount),
            Tasks.leaf("Build Adjacency list")
        );
    }

    @Override
    public MemoryEstimation memoryEstimation(InverseRelationshipsConfig configuration) {
        RelationshipType relationshipType = RelationshipType.of(configuration.relationshipType());

        var builder = MemoryEstimations.builder(InverseRelationships.class)
            .add("inverse relationships", AdjacencyListBehavior.adjacencyListEstimation(relationshipType, false));

        builder.perGraphDimension("properties", ((graphDimensions, concurrency) -> {
            var singlePropertyEstimation = AdjacencyListBehavior
                .adjacencyPropertiesEstimation(relationshipType, false)
                .estimate(graphDimensions, concurrency)
                .memoryUsage();

            return singlePropertyEstimation.times(graphDimensions.relationshipPropertyTokens().size());
        }));

        return builder.build();
    }
}