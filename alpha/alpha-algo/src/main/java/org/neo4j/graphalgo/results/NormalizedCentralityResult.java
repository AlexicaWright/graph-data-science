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
package org.neo4j.graphalgo.results;

import org.neo4j.gds.scaling.ScalarScaler;
import org.neo4j.graphalgo.api.nodeproperties.DoubleNodeProperties;
import org.neo4j.graphalgo.result.CentralityResult;

public class NormalizedCentralityResult extends CentralityResult {
    private final ScalarScaler scaler;

    public NormalizedCentralityResult(CentralityResult result, ScalarScaler scaler) {
        super(result.array());
        this.scaler = scaler;
    }

    @Override
    public double score(long nodeId) {
        return scaler.scaleProperty(nodeId);
    }

    @Override
    public DoubleNodeProperties asNodeProperties() {
        return new DoubleNodeProperties() {
            @Override
            public long size() {
                return result.size();
            }

            @Override
            public double doubleValue(long nodeId) {
                return score(nodeId);
            }
        };
    }
}
