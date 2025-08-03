package com.android.ndkports

import java.io.File

data class AdbException(val args: Iterable<String>, val output: String) :
    RuntimeException("${formatCmd(args)}:\n$output") {
    val cmd: String by lazy { formatCmd(args) }

    companion object {
        fun formatCmd(args: Iterable<String>) = args.joinToString(" ")
    }
}

private fun adb(args: Iterable<String>, serial: String? = null): String {
    val adbCmd = if (serial == null) {
        listOf("adb")
    } else {
        listOf("adb", "-s", serial)
    }
    val result = ProcessBuilder(adbCmd + args).redirectErrorStream(true).start()
    val output = result.inputStream.bufferedReader().use { it.readText() }
    if (result.waitFor() != 0) {
        throw AdbException(args, output)
    }
    return output
}

data class Device(val serial: String) {
    private val abis: List<Abi> by lazy {
        val abiProps = listOf(
            "ro.product.cpu.abi",
            "ro.product.cpu.abi2",
            "ro.product.cpu.abilist",
        )
        val abiSet = mutableSetOf<Abi>()
        for (abiProp in abiProps) {
            for (abiName in getProp(abiProp).trim().split(",")) {
                Abi.fromAbiName(abiName)?.let { abiSet.add(it) }
            }
        }
        abiSet.toList().sortedBy { it.abiName }
    }

    private val version: Int by lazy {
        getProp("ro.build.version.sdk").trim().toInt()
    }

    fun compatibleWith(abi: Abi, minSdkVersion: Int) =
        abi in abis && minSdkVersion <= version

    fun push(src: File, dest: File) =
        run(listOf("push", src.toString(), dest.toString()))

    fun shell(cmd: Iterable<String>) = run(listOf("shell") + cmd)

    private fun getProp(name: String): String = shell(listOf("getprop", name))

    private fun run(args: Iterable<String>): String = adb(args, serial)
}

class DeviceFleet {
    private fun lineHasUsableDevice(line: String): Boolean {
        if (line.isBlank()) {
            return false
        }
        if (line == "List of devices attached") {
            return false
        }
        if (line.contains("offline")) {
            return false
        }
        if (line.contains("unauthorized")) {
            return false
        }
        if (line.startsWith("* daemon")) {
            return false
        }
        return true
    }

    private val devices: List<Device> by lazy {
        adb(listOf("devices")).lines().filter { lineHasUsableDevice(it) }.map {
            Device(it.split("\\s".toRegex()).first())
        }
    }

    fun findDeviceFor(abi: Abi, minSdkVersion: Int): Device? =
        devices.find { it.compatibleWith(abi, minSdkVersion) }
}