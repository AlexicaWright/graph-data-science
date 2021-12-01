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
package org.neo4j.gds;

import org.neo4j.gds.api.GraphLoaderContext;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.config.GraphCreateConfig;
import org.neo4j.gds.core.GraphDimensions;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.kernel.database.NamedDatabaseId;

import java.util.Optional;
import java.util.function.Supplier;

public interface GraphStoreLoader {
    GraphCreateConfig graphCreateConfig();
    GraphStore graphStore();
    GraphDimensions graphDimensions();
    // This is empty in the GraphStoreFromCatalogLoader case
    // as we do not add the graph memory consumption to the
    // estimation result in that case.
    Optional<MemoryEstimation> memoryEstimation();

    // The supplier arguments are necessary as the EstimationCLI will create
    // procedures without any of the context injected fields set. This will
    // cause method calls like `BaseProc#username()` to throw an NPE.
    // This will be fixed once all procedure logic is encapsulated in separate
    // classes and the EstimationCLI will call these classes directly instead.
    static GraphStoreLoader of(
        AlgoBaseConfig config,
        Optional<String> maybeGraphName,
        Optional<GraphCreateConfig> implicitCreateConfig,
        Supplier<NamedDatabaseId> databaseIdSupplier,
        Supplier<String> usernameSupplier,
        Supplier<GraphLoaderContext> graphLoaderContextSupplier,
        boolean isGdsAdmin
    ) {
        if (maybeGraphName.isPresent()) {
            return new GraphStoreFromCatalogLoader(
                maybeGraphName.get(),
                config,
                usernameSupplier.get(),
                databaseIdSupplier.get(),
                isGdsAdmin
            );
        }
        else if (implicitCreateConfig.isPresent()) {
            GraphCreateConfig graphCreateConfig = implicitCreateConfig.get();
            return implicitGraphLoader(usernameSupplier, graphLoaderContextSupplier, graphCreateConfig);
        } else {
            throw new IllegalStateException("There must be either a graph name or an anonymous graph projection config");
        }
    }

    static GraphStoreLoader implicitGraphLoader(
        Supplier<String> usernameSupplier,
        Supplier<GraphLoaderContext> graphLoaderContextSupplier,
        GraphCreateConfig graphCreateConfig
    ) {
        if (graphCreateConfig.isFictitiousLoading()) {
            return new FictitiousGraphStoreLoader(graphCreateConfig);
        } else {
            return new GraphStoreFromDatabaseLoader(
                graphCreateConfig,
                usernameSupplier.get(),
                graphLoaderContextSupplier.get()
            );
        }
    }

}
