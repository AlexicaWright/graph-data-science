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
package org.neo4j.gds.ml.pipeline;

import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.configuration.DefaultsConfiguration;
import org.neo4j.gds.configuration.LimitsConfiguration;
import org.neo4j.gds.core.Username;
import org.neo4j.gds.executor.AlgoConfigParser;
import org.neo4j.gds.executor.ExecutionMode;
import org.neo4j.gds.executor.GdsCallableFinder;
import org.neo4j.gds.executor.NewConfigFunction;
import org.neo4j.gds.utils.StringJoining;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public final class NodePropertyStepFactory {

    private static final List<String> RESERVED_CONFIG_KEYS = List.of(
        AlgoBaseConfig.NODE_LABELS_KEY,
        AlgoBaseConfig.RELATIONSHIP_TYPES_KEY
    );

    private NodePropertyStepFactory() {}

    public static NodePropertyStep createNodePropertyStep(
        String taskName,
        Map<String, Object> configMap
    ) {
        return createNodePropertyStep(taskName, configMap, List.of(), List.of());
    }

    public static NodePropertyStep createNodePropertyStep(
        String taskName,
        Map<String, Object> configMap,
        List<String> contextNodeLabels,
        List<String> contextRelationshipTypes
    ) {
        var normalizedName = normalizeName(taskName);

        var gdsCallableDefinition = GdsCallableFinder
            .findByName(normalizedName)
            .orElseThrow(() -> new IllegalArgumentException(formatWithLocale(
                "Could not find a procedure called %s",
                normalizedName
            )));

        if (gdsCallableDefinition.executionMode() != ExecutionMode.MUTATE_NODE_PROPERTY) {
            throw new IllegalArgumentException(formatWithLocale(
                "The procedure %s does not mutate node properties and is thus not allowed as node property step",
                normalizedName
            ));
        }

        validateReservedConfigKeys(configMap);

        // validate user-input is valid
        tryParsingConfig(gdsCallableDefinition, configMap);

        return new NodePropertyStep(gdsCallableDefinition, configMap, contextNodeLabels, contextRelationshipTypes);
    }

    private static AlgoBaseConfig tryParsingConfig(
        GdsCallableFinder.GdsCallableDefinition callableDefinition,
        Map<String, Object> configuration
    ) {
        NewConfigFunction<AlgoBaseConfig> newConfigFunction = callableDefinition
            .algorithmSpec()
            .newConfigFunction();

        var defaults = DefaultsConfiguration.Instance;
        var limits = LimitsConfiguration.Instance;

        // passing the EMPTY_USERNAME as we only try to check if the given configuration itself is valid
        return new AlgoConfigParser<>(Username.EMPTY_USERNAME.username(), newConfigFunction, defaults, limits).processInput(configuration);
    }

    private static void validateReservedConfigKeys(Map<String, Object> procedureConfig) {
        if (RESERVED_CONFIG_KEYS.stream().anyMatch(procedureConfig::containsKey)) {
            throw new IllegalArgumentException(formatWithLocale(
                "Cannot configure %s for an individual node property step, but can only be configured at `train` and `predict` mode.",
                StringJoining.join(RESERVED_CONFIG_KEYS)
            ));
        }
    }

    private static String normalizeName(String input) {
        input = input.toLowerCase(Locale.ROOT);
        input = !input.startsWith("gds.") ? formatWithLocale("gds.%s", input) : input;
        input = !input.endsWith(".mutate") ? formatWithLocale("%s.mutate", input) : input;
        return input;
    }
}
