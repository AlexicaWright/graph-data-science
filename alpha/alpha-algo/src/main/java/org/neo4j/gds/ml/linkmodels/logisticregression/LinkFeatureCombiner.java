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
package org.neo4j.gds.ml.linkmodels.logisticregression;

import org.neo4j.gds.ml.DoubleArrayCombiner;
import org.neo4j.graphalgo.core.utils.Intersections;

public enum LinkFeatureCombiner implements DoubleArrayCombiner {
    L2 {
        @Override
        public double[] combine(double[] sourceArray, double[] targetArray) {
            assert sourceArray.length == targetArray.length;
            var result = new double[sourceArray.length + 1];
            for (int i = 0; i < sourceArray.length; i++) {
                result[i] = Math.pow((sourceArray[i] - targetArray[i]), 2);
            }
            result[sourceArray.length] = 1.0;
            return result;
        }

        @Override
        public int outputDimension(int inputDimension) {
            return inputDimension + 1;
        }
    },
    HADAMARD {
        @Override
        public double[] combine(double[] sourceArray, double[] targetArray) {
            assert sourceArray.length == targetArray.length;
            var result = new double[sourceArray.length + 1];
            for (int i = 0; i < sourceArray.length; i++) {
                result[i] = sourceArray[i] * targetArray[i];
            }
            result[sourceArray.length] = 1.0;
            return result;
        }

        @Override
        public int outputDimension(int inputDimension) {
            return inputDimension + 1;
        }
    },
    COSINE {
        @Override
        public double[] combine(double[] sourceArray, double[] targetArray) {
            var result = new double[] { Intersections.cosine(sourceArray, targetArray, sourceArray.length) , 1.0};
            if (Double.isNaN(result[0])) {
                result[0] = 0;
            }
            return result;
        }

        @Override
        public int outputDimension(int inputDimension) {
            return 2;
        }
    }
}
