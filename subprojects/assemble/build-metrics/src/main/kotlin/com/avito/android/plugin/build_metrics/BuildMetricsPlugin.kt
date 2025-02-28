package com.avito.android.plugin.build_metrics

import com.avito.android.critical_path.CriticalPathRegistry
import com.avito.android.gradle.metric.GradleCollector
import com.avito.android.graphite.graphiteConfig
import com.avito.android.plugin.build_metrics.internal.AppBuildTimeListener
import com.avito.android.plugin.build_metrics.internal.BuildOperationsResultProvider
import com.avito.android.plugin.build_metrics.internal.CompositeBuildMetricsListener
import com.avito.android.plugin.build_metrics.internal.ConfigurationTimeListener
import com.avito.android.plugin.build_metrics.internal.TotalBuildTimeListener
import com.avito.android.plugin.build_metrics.internal.runtime.Cgroup2
import com.avito.android.plugin.build_metrics.internal.runtime.JavaHome
import com.avito.android.plugin.build_metrics.internal.runtime.JvmMetricsCollector
import com.avito.android.plugin.build_metrics.internal.runtime.JvmMetricsSenderImpl
import com.avito.android.plugin.build_metrics.internal.runtime.OsMetricsCollector
import com.avito.android.plugin.build_metrics.internal.runtime.OsMetricsSenderImpl
import com.avito.android.plugin.build_metrics.internal.runtime.RuntimeMetricsListener
import com.avito.android.plugin.build_metrics.internal.runtime.VmResolver
import com.avito.android.plugin.build_metrics.internal.runtime.command.Jcmd
import com.avito.android.plugin.build_metrics.internal.runtime.command.Jps
import com.avito.android.plugin.build_metrics.internal.tasks.CriticalPathMetricsTracker
import com.avito.android.plugin.build_metrics.internal.teamcity.CollectTeamcityMetricsTask
import com.avito.android.stats.SeriesName
import com.avito.android.stats.StatsDSender
import com.avito.android.stats.statsd
import com.avito.android.stats.withPrefix
import com.avito.kotlin.dsl.getOptionalStringProperty
import com.avito.kotlin.dsl.isRoot
import com.avito.teamcity.teamcityCredentials
import com.avito.utils.ProcessRunner
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

public open class BuildMetricsPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        check(project.isRoot()) {
            "Plugin must be applied to the root project but was applied to ${project.path}"
        }

        val extension = project.extensions.create<BuildMetricsExtension>("buildMetrics")

        if (!project.pluginIsEnabled) {
            project.logger.lifecycle("Build metrics plugin is disabled")
            return
        }

        project.tasks.register<CollectTeamcityMetricsTask>("collectTeamcityMetrics") {
            buildId.set(project.getOptionalStringProperty("avito.build.metrics.teamcityBuildId"))
            this.teamcityCredentials.set(project.teamcityCredentials)
            this.graphiteConfig.set(project.graphiteConfig)
        }
        project.afterEvaluate { // values from extension are not available earlier
            registerListeners(project, extension)
        }
    }

    private fun registerListeners(project: Project, extension: BuildMetricsExtension) {
        val metricsTracker: StatsDSender = if (extension.metricsPrefix.isPresent) {
            val prefix = SeriesName.create(*extension.metricsPrefix.get().toTypedArray())
            project.statsd.get().withPrefix(prefix)
        } else {
            project.statsd.get()
        }

        val buildOperationsListener = BuildOperationsResultProvider.register(project, metricsTracker)

        val criticalPathTracker = CriticalPathMetricsTracker(metricsTracker)
        CriticalPathRegistry.addListener(project, criticalPathTracker)

        val processRunner = ProcessRunner.create(workingDirectory = null)
        val javaHome = JavaHome()

        val runtimeMetricsListener = RuntimeMetricsListener(
            collectors = listOf(
                JvmMetricsCollector(
                    vmResolver = VmResolver(
                        jps = Jps(processRunner, javaHome)
                    ),
                    jcmd = Jcmd(processRunner, javaHome),
                    sender = JvmMetricsSenderImpl(metricsTracker)
                ),
                OsMetricsCollector(
                    cgroup = Cgroup2.resolve(),
                    sender = OsMetricsSenderImpl(metricsTracker)
                )
            )
        )

        val buildListeners = listOf(
            runtimeMetricsListener,
            ConfigurationTimeListener(metricsTracker),
            TotalBuildTimeListener(metricsTracker),
            AppBuildTimeListener.from(project, metricsTracker)
        )

        val eventsListeners = listOf(
            CompositeBuildMetricsListener(
                listeners = buildListeners,
            ),
            buildOperationsListener
        )
        GradleCollector.initialize(
            project,
            eventsListeners
        )
    }
}

internal val Project.pluginIsEnabled: Boolean
    get() = providers
        .gradleProperty(enabledProp)
        .map { it.toBoolean() }
        .getOrElse(false)

private const val enabledProp = "avito.build.metrics.enabled"
