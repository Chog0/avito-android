package com.avito.android.plugin.build_metrics.tasks

import com.avito.android.critical_path.TaskOperation
import com.avito.android.plugin.build_metrics.internal.tasks.CriticalPathMetricsTracker
import com.avito.android.stats.SeriesName
import com.avito.android.stats.StatsMetric
import com.avito.android.stats.StubStatsdSender
import com.avito.android.stats.TimeMetric
import com.avito.graph.OperationsPath
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Task
import org.gradle.util.Path
import org.junit.jupiter.api.Test

internal class CriticalPathMetricsTrackerTest {

    @Test
    fun `on path ready - sends tasks metrics`() {
        val metrics = processResults(
            taskOperation(
                path = ":lib:build-a",
                type = CustomTask::class.java,
                startMs = 0,
                finishMs = 1_000
            ),
            taskOperation(
                path = ":lib:build-b",
                type = CustomTask::class.java,
                startMs = 0,
                finishMs = 1_000
            ),
            taskOperation(
                path = ":app:build",
                type = CustomTask::class.java,
                startMs = 0,
                finishMs = 3_000
            ),
        )
        assertThat(metrics).contains(
            TimeMetric(
                SeriesName.create("build", "tasks", "critical", "task", "lib", "CustomTask"),
                timeInMs = 2_000
            )
        )
        assertThat(metrics).contains(
            TimeMetric(
                SeriesName.create("build", "tasks", "critical", "task", "app", "CustomTask"),
                timeInMs = 3_000
            )
        )
    }

    private fun taskOperation(
        path: String,
        type: Class<out Task>,
        startMs: Long,
        finishMs: Long,
    ) = TaskOperation(
        path = Path.path(path),
        type = type,
        startMs = startMs,
        finishMs = finishMs,
        predecessors = emptySet()
    )

    private fun processResults(vararg tasks: TaskOperation): List<StatsMetric> {
        val metricsTracker = StubStatsdSender()

        val tracker = CriticalPathMetricsTracker(metricsTracker)
        tracker.onCriticalPathReady(
            OperationsPath(operations = tasks.toList())
        )
        return metricsTracker.getSentMetrics()
    }

    private abstract class CustomTask : Task
}
