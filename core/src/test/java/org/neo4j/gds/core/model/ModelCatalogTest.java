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
package org.neo4j.gds.core.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.gds.annotation.Configuration;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.api.schema.GraphSchema;
import org.neo4j.gds.config.BaseConfig;
import org.neo4j.gds.gdl.GdlFactory;
import org.neo4j.gds.junit.annotation.Edition;
import org.neo4j.gds.junit.annotation.GdsEditionTest;
import org.neo4j.gds.model.ModelConfig;
import org.neo4j.gds.model.catalog.TestTrainConfig;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

class ModelCatalogTest {

    private static final String USERNAME = "testUser";
    private static final GraphSchema GRAPH_SCHEMA = GdlFactory.of("(:Node1)").build().graphStore().schema();

    private static final Model<String, TestTrainConfig, Model.Mappable> TEST_MODEL = Model.of(
        USERNAME,
        "testModel",
        "testAlgo",
        GRAPH_SCHEMA,
        "modelData",
        TestTrainConfig.of(),
        Map::of
    );

    @AfterEach
    void afterEach() {
        ModelCatalog.removeAllLoadedModels();
    }

    @Test
    void shouldNotStoreMoreThanAllowedModels() {
        int allowedModelsCount = 3;

        for (int i = 0; i < allowedModelsCount; i++) {
            int modelIndex = i;
            assertDoesNotThrow(() -> {
                ModelCatalog.set(Model.of(
                    USERNAME,
                    "testModel_" + modelIndex,
                    "testAlgo",
                    GRAPH_SCHEMA,
                    1337L,
                    TestTrainConfig.of(),
                    Map::of
                ));
            });
        }

        var tippingModel = Model.of(
            USERNAME,
            "testModel_" + (allowedModelsCount + 1),
            "testAlgo",
            GRAPH_SCHEMA,
            1337L,
            TestTrainConfig.of(),
            Map::of
        );

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ModelCatalog.set(tippingModel)
        );

        assertEquals(formatWithLocale("Community users can only store `%d` models in the catalog, see https://neo4j.com/docs/graph-data-science/", allowedModelsCount), ex.getMessage());
    }

    @Test
    void shouldStoreModelsPerType() {
        var model = Model.of(
            USERNAME,
            "testModel",
            "testAlgo",
            GRAPH_SCHEMA,
            "testTrainData",
            TestTrainConfig.of(),
            Map::of
        );
        var model2 = Model.of(
            USERNAME,
            "testModel2",
            "testAlgo2",
            GRAPH_SCHEMA,
            1337L,
            TestTrainConfig.of(),
            Map::of
        );

        ModelCatalog.set(model);
        ModelCatalog.set(model2);

        assertEquals(model, ModelCatalog.get(USERNAME, "testModel", String.class, TestTrainConfig.class, Model.Mappable.class));
        assertEquals(model2, ModelCatalog.get(USERNAME, "testModel2", Long.class, TestTrainConfig.class, Model.Mappable.class));
    }

    @Test
    void shouldThrowWhenPublishingOnCE() {
        ModelCatalog.set(TEST_MODEL);

        assertThatThrownBy(() -> ModelCatalog.publish(USERNAME, TEST_MODEL.name()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(
                "Publishing a model is only available with the Graph Data Science library Enterprise Edition.");
    }

    @Test
    void shouldStoreModels() {
        var model = Model.of(
            "user1",
            "testModel",
            "testAlgo",
            GRAPH_SCHEMA,
            "testTrainData",
            TestTrainConfig.of(),
            Map::of
        );
        var model2 = Model.of(
            "user2",
            "testModel2",
            "testAlgo",
            GRAPH_SCHEMA,
            "testTrainData",
            TestTrainConfig.of(),
            Map::of
        );

        ModelCatalog.set(model);
        ModelCatalog.set(model2);

        assertEquals(model, ModelCatalog.get("user1", "testModel", String.class, TestTrainConfig.class, Model.Mappable.class));
        assertEquals(model2, ModelCatalog.get("user2", "testModel2", String.class, TestTrainConfig.class, Model.Mappable.class));
    }

    @Test
    void shouldThrowWhenTryingToGetOtherUsersModel() {
        ModelCatalog.set(TEST_MODEL);

        var ex = assertThrows(
            NoSuchElementException.class,
            () -> ModelCatalog.get("fakeUser", "testModel", String.class, TestTrainConfig.class, Model.Mappable.class)
        );

        assertEquals("Model with name `testModel` does not exist.", ex.getMessage());
        assertNotNull(TEST_MODEL.creationTime());
    }

    @Test
    void shouldThrowOnModelDataTypeMismatch() {
        ModelCatalog.set(TEST_MODEL);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ModelCatalog.get(USERNAME, "testModel", Double.class, TestTrainConfig.class, Model.Mappable.class)
        );

        assertEquals(
            "The model `testModel` has data with different types than expected. " +
            "Expected data type: `java.lang.String`, invoked with model data type: `java.lang.Double`.",
            ex.getMessage()
        );
    }

    @Test
    void shouldThrowOnModelConfigTypeMismatch() {
        ModelCatalog.set(TEST_MODEL);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ModelCatalog.get(USERNAME, "testModel", String.class, ModelCatalogTestTrainConfig.class, Model.Mappable.class)
        );

        assertEquals(
            "The model `testModel` has a training config with different types than expected. " +
            "Expected train config type: `org.neo4j.gds.model.catalog.TestTrainConfigImpl`, " +
            "invoked with model config type: `org.neo4j.gds.core.model.ModelCatalogTest$ModelCatalogTestTrainConfig`.",
            ex.getMessage()
        );
    }

    @Test
    void shouldThrowIfModelNameAlreadyExists() {
        ModelCatalog.set(TEST_MODEL);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ModelCatalog.checkStorable(TEST_MODEL.creator(), TEST_MODEL.name(), TEST_MODEL.algoType())
        );

        assertEquals(
            "Model with name `testModel` already exists.",
            ex.getMessage()
        );
    }

    @Test
    void shouldThrowIfModelNameAlreadyExistsOnSet() {
        ModelCatalog.set(TEST_MODEL);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ModelCatalog.set(TEST_MODEL)
        );

        assertEquals(
            "Model with name `testModel` already exists.",
            ex.getMessage()
        );
    }

    @Test
    void checksIfModelExists() {
        ModelCatalog.set(TEST_MODEL);

        assertTrue(ModelCatalog.exists(USERNAME, "testModel"));
        assertFalse(ModelCatalog.exists(USERNAME, "bogusModel"));
        assertFalse(ModelCatalog.exists("fakeUser", "testModel"));
    }

    @Test
    void getModelType() {
        ModelCatalog.set(TEST_MODEL);

        Optional<String> testModel = ModelCatalog.type(USERNAME, "testModel");
        assertFalse(testModel.isEmpty());
        assertEquals("testAlgo", testModel.get());
    }

    @Test
    void getNonExistingModelType() {
        Optional<String> bogusModel = ModelCatalog.type(USERNAME, "bogusModel");
        assertTrue(bogusModel.isEmpty());
    }

    @Test
    void shouldDropModel() {
        ModelCatalog.set(TEST_MODEL);

        assertTrue(ModelCatalog.exists(USERNAME, "testModel"));
        ModelCatalog.drop(USERNAME, "testModel");
        assertFalse(ModelCatalog.exists(USERNAME, "testModel"));
    }

    @Test
    void shouldNotThrowWhenListingNonExistentModel() {
        assertDoesNotThrow(() -> ModelCatalog.list(USERNAME, "nonExistentModel"));
    }

    @Test
    void shouldReturnEmptyList() {
        assertEquals(0, ModelCatalog.list(USERNAME).size());
    }

    @Nested
    @GdsEditionTest(Edition.EE)
    class ModelCatalogEnterpriseFeaturesTest {

        @Test
        void shouldListModels() {
            var model1 = Model.of(
                USERNAME,
                "testModel1",
                "testAlgo1",
                GRAPH_SCHEMA,
                "modelData1",
                TestTrainConfig.of(),
                Map::of
            );

            var model2 = Model.of(
                USERNAME,
                "testModel2",
                "testAlgo2",
                GRAPH_SCHEMA,
                1337L,
                TestTrainConfig.of(),
                Map::of
            );

            var publicModel = Model.of(
                "anotherUser",
                "testModel2",
                "testAlgo2",
                GRAPH_SCHEMA,
                1337L,
                TestTrainConfig.of(),
                Map::of
            );

            ModelCatalog.set(model1);
            ModelCatalog.set(model2);
            ModelCatalog.set(publicModel);
            var publishedModel = ModelCatalog.publish("anotherUser", "testModel2");


            var models = ModelCatalog.list(USERNAME);
            assertEquals(3, models.size());

            assertThat(models).containsExactlyInAnyOrder(model1, model2, publishedModel);
        }

        @Test
        void shouldOnlyBeDroppedByCreator() {
            ModelCatalog.set(TEST_MODEL);
            ModelCatalog.publish(USERNAME, "testModel");

            assertThatThrownBy(() -> ModelCatalog.drop("anotherUser", "testModel_public"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only the creator");

            ModelCatalog.drop(USERNAME, "testModel_public");
            assertEquals(0, ModelCatalog.list(USERNAME).size());
        }

        @Test
        void checksIfPublicModelExists() {
            ModelCatalog.set(TEST_MODEL);
            ModelCatalog.publish(USERNAME, "testModel");

            assertThat(ModelCatalog.exists(USERNAME, "testModel")).isFalse();
            assertThat(ModelCatalog.exists(USERNAME, "testModel_public")).isTrue();
            assertThat(ModelCatalog.exists(USERNAME, "bogusModel")).isFalse();
            assertThat(ModelCatalog.exists("anotherUser", "testModel")).isFalse();
            assertThat(ModelCatalog.exists("anotherUser", "testModel_public")).isTrue();
        }

        @Test
        void shouldPublishModels() {
            ModelCatalog.set(TEST_MODEL);
            var publishedModel = ModelCatalog.publish(USERNAME, "testModel");
            assertEquals(1, ModelCatalog.list(USERNAME).size());

            var publicModel = ModelCatalog.get(
                "anotherUser",
                publishedModel.name(),
                String.class,
                TestTrainConfig.class,
                Model.Mappable.class
            );

            assertThat(publicModel)
                .usingRecursiveComparison()
                .ignoringFields("sharedWith", "name")
                .withStrictTypeChecking()
                .isEqualTo(TEST_MODEL);

            assertEquals(List.of(Model.ALL_USERS), publicModel.sharedWith());
        }

        @ParameterizedTest
        @MethodSource("org.neo4j.gds.core.model.ModelCatalogTest#modelInput")
        void shouldThrowOnMissingModel(Iterable<String> existingModels, String searchModel, String expectedMessage) {
            existingModels.forEach(existingModel -> ModelCatalog.set(testModel(existingModel)));

            // test the get code path
            assertThatExceptionOfType(NoSuchElementException.class)
                .isThrownBy(() -> ModelCatalog.get(USERNAME, searchModel, String.class, TestTrainConfig.class, Model.Mappable.class))
                .withMessage(expectedMessage);

            // test the drop code path
            assertThatExceptionOfType(NoSuchElementException.class)
                .isThrownBy(() -> ModelCatalog.drop(USERNAME, searchModel))
                .withMessage(expectedMessage);
        }

        @Test
        void shouldThrowOnOverridingModels() {
            ModelCatalog.set(TEST_MODEL);

            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ModelCatalog.set(TEST_MODEL)
            );

            assertEquals("Model with name `testModel` already exists.", ex.getMessage());
        }
    }

    static Stream<Arguments> modelInput() {
        return Stream.of(
            Arguments.of(List.of(), "something", "Model with name `something` does not exist."),
            Arguments.of(
                List.of("model0"),
                "model1",
                "Model with name `model1` does not exist. Did you mean `model0`?"
            ),
            Arguments.of(
                List.of("model0", "model1"),
                "model2",
                "Model with name `model2` does not exist. Did you mean one of [`model0`, `model1`]?"
            ),
            Arguments.of(
                List.of("model0", "model1", "foobar"),
                "model2",
                "Model with name `model2` does not exist. Did you mean one of [`model0`, `model1`]?"
            )
        );
    }

    private static Model<Integer, TestTrainConfig, Model.Mappable> testModel(String name) {
        return Model.of(USERNAME, name, "algo", GraphSchema.empty(), 42, TestTrainConfig.of(), Map::of);
    }

    @ValueClass
    @Configuration("ModelCatalogTestTrainConfigImpl")
    @SuppressWarnings("immutables:subtype")
    interface ModelCatalogTestTrainConfig extends BaseConfig, ModelConfig {

        long serialVersionUID = 0x42L;

        static ModelCatalogTestTrainConfig of() {
            return ImmutableModelCatalogTestTrainConfig.of("username", "modelName");
        }
    }
}
