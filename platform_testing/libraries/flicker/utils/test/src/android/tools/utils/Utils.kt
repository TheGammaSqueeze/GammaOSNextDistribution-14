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

package android.tools.utils

import android.content.Context
import android.tools.Scenario
import android.tools.ScenarioBuilder
import android.tools.ScenarioImpl
import android.tools.Timestamp
import android.tools.Timestamps
import android.tools.io.Reader
import android.tools.io.ResultArtifactDescriptor
import android.tools.io.RunStatus
import android.tools.rules.CacheCleanupRule
import android.tools.rules.InitializeCrossPlatformRule
import android.tools.rules.StopAllTracesRule
import android.tools.traces.io.ArtifactBuilder
import android.tools.traces.io.ResultWriter
import android.tools.traces.parsers.perfetto.LayersTraceParser
import android.tools.traces.parsers.perfetto.TraceProcessorSession
import android.tools.traces.parsers.wm.WindowManagerDumpParser
import android.tools.traces.parsers.wm.WindowManagerTraceParser
import android.tools.traces.wm.ConfigurationContainerImpl
import android.tools.traces.wm.RootWindowContainer
import android.tools.traces.wm.WindowContainerImpl
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.io.ByteStreams
import com.google.common.truth.Truth
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import org.junit.rules.RuleChain

/** Factory function to create cleanup test rule */
fun CleanFlickerEnvironmentRule(): RuleChain =
    RuleChain.outerRule(InitializeCrossPlatformRule())
        .around(StopAllTracesRule())
        .around(CacheCleanupRule())

val TEST_SCENARIO = ScenarioBuilder().forClass("test").build() as ScenarioImpl

/**
 * Runs `r` and asserts that an exception with type `expectedThrowable` is thrown.
 *
 * @param r the [Runnable] which is run and expected to throw.
 * @throws AssertionError if `r` does not throw, or throws a runnable that is not an instance of
 *   `expectedThrowable`.
 */
inline fun <reified ExceptionType> assertThrows(r: () -> Unit): ExceptionType {
    try {
        r()
    } catch (t: Throwable) {
        when {
            ExceptionType::class.java.isInstance(t) -> return t as ExceptionType
            t is Exception ->
                throw AssertionError(
                    "Expected ${ExceptionType::class.java}, but got '${t.javaClass}'",
                    t
                )
            // Re-throw Errors and other non-Exception throwables.
            else -> throw t
        }
    }
    error("Expected exception ${ExceptionType::class.java}, but nothing was thrown")
}

fun assertFail(expectedMessage: String, predicate: () -> Any) {
    val error = assertThrows<AssertionError> { predicate() }
    Truth.assertThat(error).hasMessageThat().contains(expectedMessage)
}

fun assertThatErrorContainsDebugInfo(error: Throwable) {
    Truth.assertThat(error).hasMessageThat().contains("What?")
    Truth.assertThat(error).hasMessageThat().contains("Where?")
}

fun assertArchiveContainsFiles(archivePath: File, expectedFiles: List<String>) {
    Truth.assertWithMessage("Expected trace archive `$archivePath` to exist")
        .that(archivePath.exists())
        .isTrue()

    val archiveStream = ZipInputStream(FileInputStream(archivePath))

    val actualFiles = generateSequence { archiveStream.nextEntry }.map { it.name }.toList()

    Truth.assertWithMessage("Trace archive doesn't contain all expected traces")
        .that(actualFiles)
        .containsExactlyElementsIn(expectedFiles)
}

fun getWmTraceReaderFromAsset(
    relativePath: String,
    from: Long = Long.MIN_VALUE,
    to: Long = Long.MAX_VALUE,
    addInitialEntry: Boolean = true,
    legacyTrace: Boolean = false,
): Reader {
    return ParsedTracesReader(
        artifact = TestArtifact(relativePath),
        wmTrace =
            WindowManagerTraceParser(legacyTrace)
                .parse(
                    readAsset(relativePath),
                    Timestamps.from(elapsedNanos = from),
                    Timestamps.from(elapsedNanos = to),
                    addInitialEntry,
                    clearCache = false
                )
    )
}

fun getWmDumpReaderFromAsset(relativePath: String): Reader {
    return ParsedTracesReader(
        artifact = TestArtifact(relativePath),
        wmTrace = WindowManagerDumpParser().parse(readAsset(relativePath), clearCache = false)
    )
}

fun getLayerTraceReaderFromAsset(
    relativePath: String,
    ignoreOrphanLayers: Boolean = true,
    from: Timestamp = Timestamps.min(),
    to: Timestamp = Timestamps.max()
): Reader {
    val layersTrace =
        TraceProcessorSession.loadPerfettoTrace(readAsset(relativePath)) { session ->
            LayersTraceParser(
                    ignoreLayersStackMatchNoDisplay = false,
                    ignoreLayersInVirtualDisplay = false,
                ) {
                    ignoreOrphanLayers
                }
                .parse(session, from, to)
        }
    return ParsedTracesReader(artifact = TestArtifact(relativePath), layersTrace = layersTrace)
}

@Throws(Exception::class)
fun readAsset(relativePath: String): ByteArray {
    val context: Context = InstrumentationRegistry.getInstrumentation().context
    val inputStream = context.resources.assets.open("testdata/$relativePath")
    return ByteStreams.toByteArray(inputStream)
}

@Throws(IOException::class)
fun readAssetAsFile(relativePath: String): File {
    val context: Context = InstrumentationRegistry.getInstrumentation().context
    return File(context.cacheDir, relativePath).also {
        if (!it.exists()) {
            it.outputStream().use { cache ->
                context.assets.open("testdata/$relativePath").use { inputStream ->
                    inputStream.copyTo(cache)
                }
            }
        }
    }
}

fun newTestResultWriter(
    scenario: Scenario = ScenarioBuilder().forClass(kotlin.io.path.createTempFile().name).build()
) =
    ResultWriter()
        .forScenario(scenario)
        .withOutputDir(createTempDirectory().toFile())
        .setRunComplete()

fun assertExceptionMessage(error: Throwable?, expectedValue: String) {
    Truth.assertWithMessage("Expected exception")
        .that(error)
        .hasMessageThat()
        .contains(expectedValue)
}

fun outputFileName(status: RunStatus) =
    File("/sdcard/flicker/${status.prefix}__test_ROTATION_0_GESTURAL_NAV.zip")

fun createDefaultArtifactBuilder(
    status: RunStatus,
    outputDir: File = createTempDirectory().toFile(),
    files: Map<ResultArtifactDescriptor, File> = emptyMap()
) =
    ArtifactBuilder()
        .withScenario(TEST_SCENARIO)
        .withOutputDir(outputDir)
        .withStatus(status)
        .withFiles(files)

fun getLauncherPackageName() =
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).launcherPackageName

fun newEmptyRootContainer(orientation: Int = 0, layerId: Int = 0) =
    RootWindowContainer(
        WindowContainerImpl(
            title = "root",
            token = "",
            orientation = orientation,
            layerId = layerId,
            _isVisible = true,
            _children = emptyList(),
            configurationContainer = ConfigurationContainerImpl.EMPTY,
            computedZ = 0
        )
    )
