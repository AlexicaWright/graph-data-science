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
package org.neo4j.gds.decisiontree;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public class TreeNode<PREDICTION> {
    private PREDICTION prediction;
    private int featureIndex = -1;
    private double thresholdValue;
    private TreeNode<PREDICTION> leftChild = null;
    private TreeNode<PREDICTION> rightChild = null;

    public TreeNode(int index, double value) {
        assert index >= 0;

        this.featureIndex = index;
        this.thresholdValue = value;
    }

    public TreeNode(PREDICTION prediction) {
        this.prediction = prediction;
    }

    PREDICTION prediction() {
        return prediction;
    }

    int featureIndex() {
        return featureIndex;
    }

    double thresholdValue() {
        return thresholdValue;
    }

    TreeNode<PREDICTION> leftChild() {
        return leftChild;
    }

    void setLeftChild(TreeNode leftChild) {
        this.leftChild = leftChild;
    }

    TreeNode<PREDICTION> rightChild() {
        return rightChild;
    }

    void setRightChild(TreeNode rightChild) {
        this.rightChild = rightChild;
    }

    @Override
    public String toString() {
        return formatWithLocale("Node: prediction %s, featureIndex %s, splitValue %f", this.prediction, this.featureIndex, this.thresholdValue);
    }

    /**
     * Renders the variable into a human readable representation.
     */
    public String render() {
        StringBuilder sb = new StringBuilder();
        render(sb, this, 0);
        return sb.toString();
    }


    static void render(StringBuilder sb, TreeNode<?> node, int depth) {
        if (node == null) {
            return;
        }

        sb.append("\t".repeat(Math.max(0, depth - 1)));

        if (depth > 0) {
            sb.append("|-- ");
        }

        sb.append(node);
        sb.append(System.lineSeparator());

        render(sb, node.leftChild, depth + 1);
        render(sb, node.rightChild, depth + 1);
    }
}
