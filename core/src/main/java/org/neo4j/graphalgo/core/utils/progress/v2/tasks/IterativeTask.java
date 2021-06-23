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
package org.neo4j.graphalgo.core.utils.progress.v2.tasks;

import java.util.List;
import java.util.function.Supplier;

public class IterativeTask extends Task {

    enum Mode {
        DYNAMIC,
        OPEN,
        FIXED
    }

    private final Supplier<List<Task>> subTasksSupplier;
    private final int iterations;
    private final Mode mode;

    IterativeTask(
        String description,
        List<Task> subTasks,
        Supplier<List<Task>> subTasksSupplier,
        int iterations,
        Mode mode
    ) {
        super(description, subTasks);
        this.subTasksSupplier = subTasksSupplier;
        this.iterations = iterations;
        this.mode = mode;
    }

    @Override
    public Progress getProgress() {
        var progress = super.getProgress();

        if (mode == Mode.OPEN && status() != Status.FINISHED) {
            return ImmutableProgress.of(progress.progress(), -1);
        }

        return progress;
    }

    @Override
    public Task nextSubtask() {
        if (subTasks().stream().anyMatch(t -> t.status() == Status.RUNNING)) {
            throw new IllegalStateException("Cannot move to next subtask, because some subtasks are still running");
        }

        var maybeNextSubtask = subTasks().stream().filter(t -> t.status() == Status.PENDING).findFirst();

        if (maybeNextSubtask.isPresent()) {
            return maybeNextSubtask.get();
        } else if (mode == Mode.OPEN) {
            var newIterationTasks = subTasksSupplier.get();
            subTasks().addAll(newIterationTasks);
            return newIterationTasks.get(0);
        } else {
            throw new IllegalStateException("No more pending subtasks");
        }
    }

    @Override
    public void finish() {
        super.finish();
        subTasks().forEach(t -> {
            if (t.status() == Status.PENDING) {
                t.cancel();
            }
        });
    }

    public int currentIteration() {
        return (int) subTasks().stream().filter(t -> t.status() == Status.FINISHED).count() / subTasksSupplier
            .get()
            .size();
    }

}
