package com.android.ndkports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

sealed class TestResult(val name: String, val abi: Abi) {
    class Success(name: String, abi: Abi) : TestResult(name, abi) {
        override fun toString(): String = "PASS $abi $name"
    }

    class Failure(name: String, abi: Abi, private val output: String) :
        TestResult(name, abi) {
        override fun toString(): String = "FAIL $abi $name: $output"
    }
}

private val BASE_DEVICE_DIRECTORY = File("/data/local/tmp/ndkports")

data class PushSpec(val src: File, val dest: File)

class PushBuilder(val abi: Abi, val toolchain: Toolchain) {
    val pushSpecs = mutableListOf<PushSpec>()

    fun push(src: File, dest: File) = pushSpecs.add(PushSpec(src, dest))
}

data class ShellTestSpec(val name: String, val cmd: Iterable<String>)

class ShellTestBuilder(val deviceDirectory: File, val abi: Abi) {
    val runSpecs = mutableListOf<ShellTestSpec>()

    fun shellTest(name: String, cmd: Iterable<String>) =
        runSpecs.add(ShellTestSpec(name, cmd))
}

abstract class AndroidExecutableTestTask : DefaultTask() {
    @get:InputDirectory
    abstract val ndkPath: DirectoryProperty

    private val ndk: Ndk
        get() = Ndk(ndkPath.asFile.get())

    @get:Input
    abstract val minSdkVersion: Property<Int>

    @get:Input
    abstract val push: Property<PushBuilder.() -> Unit>

    fun push(block: PushBuilder.() -> Unit) = push.set(block)

    @get:Input
    abstract val run: Property<ShellTestBuilder.() -> Unit>

    fun run(block: ShellTestBuilder.() -> Unit) = run.set(block)

    private fun deviceDirectoryForAbi(abi: Abi): File =
        BASE_DEVICE_DIRECTORY.resolve(project.name).resolve(abi.toString())

    private suspend fun runTests(
        device: Device, abi: Abi, resultChannel: SendChannel<TestResult>
    ) = coroutineScope {
        val deviceDirectory = deviceDirectoryForAbi(abi)

        val pushBlock = push.get()
        val runBlock = run.get()

        val pushBuilder =
            PushBuilder(abi, Toolchain(ndk, abi, minSdkVersion.get()))
        pushBuilder.pushBlock()
        coroutineScope {
            pushBuilder.pushSpecs.forEach {
                launch(Dispatchers.IO) {
                    device.push(
                        it.src, deviceDirectory.resolve(it.dest)
                    )
                }
            }
        }

        val runBuilder = ShellTestBuilder(deviceDirectory, abi)
        runBuilder.runBlock()
        runBuilder.runSpecs.forEach {
            launch(Dispatchers.IO) {
                val result = try {
                    device.shell(it.cmd)
                    TestResult.Success(it.name, abi)
                } catch (ex: AdbException) {
                    TestResult.Failure(it.name, abi, "${ex.cmd}\n${ex.output}")
                }

                resultChannel.send(result)
            }
        }
    }

    @Suppress("UnstableApiUsage")
    @TaskAction
    fun runTask() = runBlocking {
        val fleet = DeviceFleet()
        val warningChannel = Channel<String>(Channel.UNLIMITED)
        val resultChannel = Channel<TestResult>(Channel.UNLIMITED)
        coroutineScope {
            for (abi in Abi.values()) {
                launch {
                    val device = fleet.findDeviceFor(
                        abi, abi.adjustMinSdkVersion(minSdkVersion.get())
                    )
                    if (device == null) {
                        warningChannel.send(
                            "No device capable of running tests for $abi " +
                                    "minSdkVersion 21"
                        )
                        return@launch
                    }
                    device.shell(
                        listOf(
                            "rm", "-rf", deviceDirectoryForAbi(abi).toString()
                        )
                    )
                    runTests(device, abi, resultChannel)
                }
            }
        }
        warningChannel.close()
        resultChannel.close()

        for (warning in warningChannel) {
            logger.warn(warning)
        }

        val failures =
            resultChannel.toList().filterIsInstance<TestResult.Failure>()
        if (failures.isNotEmpty()) {
            throw RuntimeException(
                "Tests failed:\n${failures.joinToString("\n")}"
            )
        }
    }
}