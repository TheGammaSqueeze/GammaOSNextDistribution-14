/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.tools.traces.monitors

import android.tools.io.TraceType
import android.tools.traces.executeShellCommand
import android.tools.traces.io.IoUtils
import com.android.internal.protolog.common.LogLevel
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.locks.ReentrantLock
import perfetto.protos.PerfettoConfig
import perfetto.protos.PerfettoConfig.DataSourceConfig
import perfetto.protos.PerfettoConfig.SurfaceFlingerLayersConfig
import perfetto.protos.PerfettoConfig.SurfaceFlingerTransactionsConfig
import perfetto.protos.PerfettoConfig.TraceConfig

/* Captures traces from Perfetto. */
open class PerfettoTraceMonitor(val config: TraceConfig) : TraceMonitor() {
    override val traceType = TraceType.PERFETTO
    override val isEnabled
        get() = perfettoPid != null

    private var perfettoPid: Int? = null
    private var configFileInPerfettoDir: File? = null
    private var traceFile: File? = null
    private var traceFileInPerfettoDir: File? = null
    private val PERFETTO_CONFIGS_DIR = File("/data/misc/perfetto-configs")
    private val PERFETTO_TRACES_DIR = File("/data/misc/perfetto-traces")

    override fun doStart() {
        val configFile = File.createTempFile("flickerlib-config-", ".cfg")
        configFileInPerfettoDir = PERFETTO_CONFIGS_DIR.resolve(requireNotNull(configFile).name)

        traceFile = File.createTempFile(traceType.fileName, "")
        traceFileInPerfettoDir = PERFETTO_TRACES_DIR.resolve(requireNotNull(traceFile).name)

        FileOutputStream(configFile).use { config.writeTo(it) }
        IoUtils.moveFile(configFile, requireNotNull(configFileInPerfettoDir))

        val command =
            "perfetto --background-wait" +
                " --config ${configFileInPerfettoDir?.absolutePath}" +
                " --out ${traceFileInPerfettoDir?.absolutePath}"
        val stdout = String(executeShellCommand(command))
        val pid = stdout.trim().toInt()
        perfettoPid = pid
        allPerfettoPidsLock.lock()
        try {
            allPerfettoPids.add(pid)
        } finally {
            allPerfettoPidsLock.unlock()
        }
    }

    override fun doStop(): File {
        require(isEnabled) { "Attempted to stop disabled trace monitor" }
        killPerfettoProcess(requireNotNull(perfettoPid))
        waitPerfettoProcessExits(requireNotNull(perfettoPid))
        IoUtils.moveFile(requireNotNull(traceFileInPerfettoDir), requireNotNull(traceFile))
        executeShellCommand("rm ${configFileInPerfettoDir?.absolutePath}")
        perfettoPid = null
        return requireNotNull(traceFile)
    }

    class Builder {
        private val DEFAULT_SF_LAYER_FLAGS =
            listOf(
                SurfaceFlingerLayersConfig.TraceFlag.TRACE_FLAG_INPUT,
                SurfaceFlingerLayersConfig.TraceFlag.TRACE_FLAG_COMPOSITION,
                SurfaceFlingerLayersConfig.TraceFlag.TRACE_FLAG_VIRTUAL_DISPLAYS
            )

        private val dataSourceConfigs = mutableSetOf<DataSourceConfig>()
        private var incrementalTimeoutMs: Int? = null

        fun enableLayersTrace(flags: List<SurfaceFlingerLayersConfig.TraceFlag>? = null): Builder =
            apply {
                enableCustomTrace(
                    createLayersTraceDataSourceConfig(flags ?: DEFAULT_SF_LAYER_FLAGS)
                )
            }

        fun enableLayersDump(flags: List<SurfaceFlingerLayersConfig.TraceFlag>? = null): Builder =
            apply {
                enableCustomTrace(createLayersDumpDataSourceConfig(flags ?: DEFAULT_SF_LAYER_FLAGS))
            }

        fun enableTransactionsTrace(): Builder = apply {
            enableCustomTrace(createTransactionsDataSourceConfig())
        }

        fun enableTransitionsTrace(): Builder = apply {
            enableCustomTrace(createTransitionsDataSourceConfig())
        }

        data class ProtoLogGroupOverride(
            val groupName: String,
            val logFrom: LogLevel,
            val collectStackTrace: Boolean,
        )

        @JvmOverloads
        fun enableProtoLog(
            logAll: Boolean = true,
            groupOverrides: List<ProtoLogGroupOverride> = emptyList()
        ): Builder = apply {
            enableCustomTrace(createProtoLogDataSourceConfig(logAll, groupOverrides))
        }

        fun enableCustomTrace(dataSourceConfig: DataSourceConfig): Builder = apply {
            dataSourceConfigs.add(dataSourceConfig)
        }

        fun setIncrementalTimeout(timeoutMs: Int) = apply { incrementalTimeoutMs = timeoutMs }

        fun build(): PerfettoTraceMonitor {
            val configBuilder =
                TraceConfig.newBuilder()
                    .setDurationMs(0)
                    .addBuffers(
                        TraceConfig.BufferConfig.newBuilder()
                            .setSizeKb(TRACE_BUFFER_SIZE_KB)
                            .build()
                    )

            for (dataSourceConfig in dataSourceConfigs) {
                configBuilder.addDataSources(createDataSourceWithConfig(dataSourceConfig))
            }

            val incrementalTimeoutMs = incrementalTimeoutMs
            if (incrementalTimeoutMs != null) {
                configBuilder.setIncrementalStateConfig(
                    TraceConfig.IncrementalStateConfig.newBuilder()
                        .setClearPeriodMs(incrementalTimeoutMs)
                )
            }

            return PerfettoTraceMonitor(config = configBuilder.build())
        }

        private fun createLayersTraceDataSourceConfig(
            traceFlags: List<SurfaceFlingerLayersConfig.TraceFlag>
        ): DataSourceConfig {
            return DataSourceConfig.newBuilder()
                .setName(SF_LAYERS_DATA_SOURCE)
                .setSurfaceflingerLayersConfig(
                    SurfaceFlingerLayersConfig.newBuilder()
                        .setMode(SurfaceFlingerLayersConfig.Mode.MODE_ACTIVE)
                        .apply { traceFlags.forEach { addTraceFlags(it) } }
                        .build()
                )
                .build()
        }

        private fun createLayersDumpDataSourceConfig(
            traceFlags: List<SurfaceFlingerLayersConfig.TraceFlag>
        ): DataSourceConfig {
            return DataSourceConfig.newBuilder()
                .setName(SF_LAYERS_DATA_SOURCE)
                .setSurfaceflingerLayersConfig(
                    SurfaceFlingerLayersConfig.newBuilder()
                        .setMode(SurfaceFlingerLayersConfig.Mode.MODE_DUMP)
                        .apply { traceFlags.forEach { addTraceFlags(it) } }
                        .build()
                )
                .build()
        }

        private fun createTransactionsDataSourceConfig(): DataSourceConfig {
            return DataSourceConfig.newBuilder()
                .setName(SF_TRANSACTIONS_DATA_SOURCE)
                .setSurfaceflingerTransactionsConfig(
                    SurfaceFlingerTransactionsConfig.newBuilder()
                        .setMode(SurfaceFlingerTransactionsConfig.Mode.MODE_ACTIVE)
                        .build()
                )
                .build()
        }

        private fun createTransitionsDataSourceConfig(): DataSourceConfig {
            return DataSourceConfig.newBuilder().setName(TRANSITIONS_DATA_SOURCE).build()
        }

        private fun createProtoLogDataSourceConfig(
            logAll: Boolean,
            groupOverrides: List<ProtoLogGroupOverride>
        ): DataSourceConfig {
            val protoLogConfigBuilder = PerfettoConfig.ProtoLogConfig.newBuilder()

            if (logAll) {
                protoLogConfigBuilder.setTracingMode(
                    PerfettoConfig.ProtoLogConfig.TracingMode.ENABLE_ALL
                )
            }

            for (groupOverride in groupOverrides) {
                protoLogConfigBuilder.addGroupOverrides(
                    PerfettoConfig.ProtoLogGroup.newBuilder()
                        .setGroupName(groupOverride.groupName)
                        .setLogFrom(
                            PerfettoConfig.ProtoLogLevel.forNumber(
                                groupOverride.logFrom.ordinal + 1
                            )
                        )
                        .setCollectStacktrace(groupOverride.collectStackTrace)
                )
            }

            return DataSourceConfig.newBuilder()
                .setName(PROTOLOG_DATA_SOURCE)
                .setProtologConfig(protoLogConfigBuilder)
                .build()
        }

        private fun createDataSourceWithConfig(
            dataSourceConfig: DataSourceConfig
        ): TraceConfig.DataSource {
            return TraceConfig.DataSource.newBuilder().setConfig(dataSourceConfig).build()
        }
    }

    companion object {
        private const val TRACE_BUFFER_SIZE_KB = 1024 * 1024

        private const val SF_LAYERS_DATA_SOURCE = "android.surfaceflinger.layers"
        private const val SF_TRANSACTIONS_DATA_SOURCE = "android.surfaceflinger.transactions"
        private const val TRANSITIONS_DATA_SOURCE = "com.android.wm.shell.transition"
        private const val PROTOLOG_DATA_SOURCE = "android.protolog"

        private val allPerfettoPids = mutableListOf<Int>()
        private val allPerfettoPidsLock = ReentrantLock()

        @JvmStatic
        fun newBuilder(): Builder {
            return Builder()
        }

        fun stopAllSessions() {
            allPerfettoPidsLock.lock()
            try {
                allPerfettoPids.forEach { killPerfettoProcess(it) }
                allPerfettoPids.forEach { waitPerfettoProcessExits(it) }
            } finally {
                allPerfettoPidsLock.unlock()
            }
        }

        fun killPerfettoProcess(pid: Int) {
            if (isPerfettoProcessUp(pid)) {
                executeShellCommand("kill $pid")
            }
        }

        private fun waitPerfettoProcessExits(pid: Int) {
            while (true) {
                if (!isPerfettoProcessUp(pid)) {
                    break
                }
                Thread.sleep(50)
            }
        }

        private fun isPerfettoProcessUp(pid: Int): Boolean {
            val out = String(executeShellCommand("ps -p $pid -o CMD"))
            return out.contains("perfetto")
        }
    }
}
