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
package org.neo4j.gds.ml.linkmodels;

import org.neo4j.gds.AlgorithmFactory;
import org.neo4j.gds.GraphAlgorithmFactory;
import org.neo4j.gds.WriteStreamOfRelationshipsProc;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.model.ModelCatalog;
import org.neo4j.gds.executor.AlgorithmSpec;
import org.neo4j.gds.executor.ComputationResult;
import org.neo4j.gds.executor.GdsCallable;
import org.neo4j.gds.executor.validation.ValidationConfiguration;
import org.neo4j.gds.result.AbstractResultBuilder;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.gds.results.StandardWriteRelationshipsResult;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.gds.executor.ExecutionMode.WRITE_RELATIONSHIP;
import static org.neo4j.gds.ml.linkmodels.LinkPredictionPredictCompanion.DESCRIPTION;
import static org.neo4j.procedure.Mode.READ;
import static org.neo4j.procedure.Mode.WRITE;

@GdsCallable(name = "gds.alpha.ml.linkPrediction.predict.write", description = DESCRIPTION, executionMode = WRITE_RELATIONSHIP)
public class LinkPredictionPredictWriteProc extends WriteStreamOfRelationshipsProc<LinkPredictionPredict, ExhaustiveLinkPredictionResult, StandardWriteRelationshipsResult, LinkPredictionPredictWriteConfig> {

    @Context
    public ModelCatalog modelCatalog;

    @Procedure(name = "gds.alpha.ml.linkPrediction.predict.write", mode = WRITE)
    @Description(DESCRIPTION)
    public Stream<StandardWriteRelationshipsResult> write(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return write(compute(graphName, configuration));
    }

    @Procedure(name = "gds.alpha.ml.linkPrediction.predict.write.estimate", mode = READ)
    @Description("Estimates memory for applying a linkPrediction model")
    public Stream<MemoryEstimateResult> estimate(
        @Name(value = "graphNameOrConfiguration") Object graphNameOrConfiguration,
        @Name(value = "algoConfiguration") Map<String, Object> algoConfiguration
    ) {
        return computeEstimate(graphNameOrConfiguration, algoConfiguration);
    }

    @Override
    public ValidationConfiguration<LinkPredictionPredictWriteConfig> validationConfig() {
        return LinkPredictionPredictCompanion.getValidationConfig();
    }

    @Override
    public AlgorithmSpec<LinkPredictionPredict, ExhaustiveLinkPredictionResult, LinkPredictionPredictWriteConfig, Stream<StandardWriteRelationshipsResult>, AlgorithmFactory<?, LinkPredictionPredict, LinkPredictionPredictWriteConfig>> withModelCatalog(
        ModelCatalog modelCatalog
    ) {
        this.modelCatalog = modelCatalog;
        return this;
    }

    @Override
    protected LinkPredictionPredictWriteConfig newConfig(String username, CypherMapWrapper config) {
        return LinkPredictionPredictWriteConfig.of(username, config);
    }

    @Override
    public GraphAlgorithmFactory<LinkPredictionPredict, LinkPredictionPredictWriteConfig> algorithmFactory() {
        return new LinkPredictionPredictFactory<>(modelCatalog);
    }

    @Override
    protected AbstractResultBuilder<StandardWriteRelationshipsResult> resultBuilder(
        ComputationResult<LinkPredictionPredict, ExhaustiveLinkPredictionResult, LinkPredictionPredictWriteConfig> computeResult
    ) {
        return new StandardWriteRelationshipsResult.Builder();
    }
}
