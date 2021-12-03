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
package org.neo4j.gds.ml.splitting;

import org.neo4j.gds.AlgorithmFactory;
import org.neo4j.gds.MutateProc;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.config.GraphCreateConfig;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.utils.ProgressTimer;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.ml.splitting.EdgeSplitter.SplitResult;
import org.neo4j.gds.result.AbstractResultBuilder;
import org.neo4j.gds.validation.AfterLoadValidation;
import org.neo4j.gds.validation.ValidationConfiguration;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import org.neo4j.values.storable.NumberType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;
import static org.neo4j.procedure.Mode.READ;

public class SplitRelationshipsMutateProc extends MutateProc<SplitRelationships, SplitResult, SplitRelationshipsMutateProc.MutateResult, SplitRelationshipsMutateConfig> {

    @Procedure(name = "gds.alpha.ml.splitRelationships.mutate", mode = READ)
    @Description("Splits a graph into holdout and remaining relationship types and adds them to the in-memory graph.")
    public Stream<SplitRelationshipsMutateProc.MutateResult> mutate(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        var computationResult = compute(graphName, configuration);
        return mutate(computationResult);
    }

    @Override
    protected SplitRelationshipsMutateConfig newConfig(
        String username,
        Optional<String> graphName,
        CypherMapWrapper config
    ) {
        return SplitRelationshipsMutateConfig.of(graphName, config);
    }

    @Override
    protected AlgorithmFactory<SplitRelationships, SplitRelationshipsMutateConfig> algorithmFactory() {
        return new AlgorithmFactory<>() {

            @Override
            protected String taskName() {
                return "SplitRelationships";
            }

            @Override
            protected SplitRelationships build(
                Graph graph,
                SplitRelationshipsMutateConfig configuration,
                AllocationTracker allocationTracker,
                ProgressTracker progressTracker
            ) {
                var masterGraph = graph;
                if (!configuration.nonNegativeRelationshipTypes().isEmpty()) {
                    var graphStore = graphStoreFromCatalog(configuration.graphName().get(), configuration).graphStore();
                    masterGraph = graphStore.getGraph(
                        configuration.nodeLabelIdentifiers(graphStore),
                        configuration.superGraphTypes(),
                        Optional.empty()
                    );
                }
                return new SplitRelationships(graph, masterGraph, configuration);
            }
        };

    }

    @Override
    protected AbstractResultBuilder<MutateResult> resultBuilder(ComputationResult<SplitRelationships, SplitResult, SplitRelationshipsMutateConfig> computeResult) {
        return new MutateResult.Builder();
    }

    @Override
    protected void updateGraphStore(
        AbstractResultBuilder<?> resultBuilder,
        ComputationResult<SplitRelationships, SplitResult, SplitRelationshipsMutateConfig> computationResult
    ) {

        SplitRelationshipsMutateConfig config = computationResult.config();
        try (ProgressTimer ignored = ProgressTimer.start(resultBuilder::withMutateMillis)) {
            computationResult.graphStore().addRelationshipType(
                config.remainingRelationshipType(),
                Optional.ofNullable(config.relationshipWeightProperty()),
                Optional.of(NumberType.FLOATING_POINT),
                computationResult.result().remainingRels()
            );
            computationResult.graphStore().addRelationshipType(
                config.holdoutRelationshipType(),
                Optional.of(EdgeSplitter.RELATIONSHIP_PROPERTY),
                Optional.of(NumberType.INTEGRAL),
                computationResult.result().selectedRels()
            );
        }
        long holdoutWritten = computationResult.result().selectedRels().topology().elementCount();
        long remainingWritten = computationResult.result().remainingRels().topology().elementCount();
        resultBuilder.withRelationshipsWritten(holdoutWritten + remainingWritten);
    }

    @Override
    public ValidationConfiguration<SplitRelationshipsMutateConfig> getValidationConfig() {
        return new ValidationConfiguration<>() {
            @Override
            public List<AfterLoadValidation<SplitRelationshipsMutateConfig>> afterLoadValidations() {
                return List.of(new Validation());
            }
        };
    }

    @SuppressWarnings("unused")
    public static class MutateResult {
        public final long createMillis;
        public final long computeMillis;
        public final long mutateMillis;
        public final long relationshipsWritten;

        public final Map<String, Object> configuration;

        MutateResult(
            long createMillis,
            long computeMillis,
            long mutateMillis,
            long relationshipsWritten,
            Map<String, Object> configuration
        ) {
            this.createMillis = createMillis;
            this.computeMillis = computeMillis;
            this.mutateMillis = mutateMillis;
            this.relationshipsWritten = relationshipsWritten;
            this.configuration = configuration;
        }

        static class Builder extends AbstractResultBuilder<SplitRelationshipsMutateProc.MutateResult> {

            @Override
            public SplitRelationshipsMutateProc.MutateResult build() {
                return new SplitRelationshipsMutateProc.MutateResult(
                    createMillis,
                    computeMillis,
                    mutateMillis,
                    relationshipsWritten,
                    config.toMap()
                );
            }
        }
    }

    static class Validation implements AfterLoadValidation<SplitRelationshipsMutateConfig> {
        @Override
        public void validateConfigsAfterLoad(
            GraphStore graphStore, GraphCreateConfig graphCreateConfig, SplitRelationshipsMutateConfig config
        ) {
            validateTypeDoesNotExist(graphStore, config.holdoutRelationshipType());
            validateTypeDoesNotExist(graphStore, config.remainingRelationshipType());
            validateNonNegativeRelationshipTypesExist(graphStore, config);
        }

        private void validateNonNegativeRelationshipTypesExist(
            GraphStore graphStore,
            SplitRelationshipsMutateConfig config
        ) {
            config.nonNegativeRelationshipTypes().forEach(relationshipType -> {
                if (!graphStore.hasRelationshipType(RelationshipType.of(relationshipType))) {
                    throw new IllegalArgumentException(formatWithLocale(
                        "Relationship type `%s` does not exist in the in-memory graph.",
                        relationshipType
                    ));
                }
            });
        }

        private void validateTypeDoesNotExist(
            GraphStore graphStore,
            RelationshipType holdoutRelationshipType
        ) {
            if (graphStore.hasRelationshipType(holdoutRelationshipType)) {
                throw new IllegalArgumentException(formatWithLocale(
                    "Relationship type `%s` already exists in the in-memory graph.",
                    holdoutRelationshipType.name()
                ));
            }
        }
    }

}
