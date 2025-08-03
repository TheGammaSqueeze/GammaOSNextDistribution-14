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
package com.android.bedstead.nene.broadcasts

import android.os.Build
import androidx.annotation.RequiresApi
import com.android.bedstead.nene.exceptions.AdbParseException
import com.android.bedstead.nene.utils.matchAll
import com.android.bedstead.nene.utils.matchFirst
import kotlin.String
import kotlin.Throws

/**
 * Parser for "adb dumpsys activity activities" on Android T+
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AdbActivitiesParser33 : AdbActivitiesParser {
    companion object ParsingRules {
        // Matches all lines in "* Task{" block until if finds an empty line
        val tasksRule = """(?:.*?)( +\* Task\{(?:.|[\n\r][^\n\r])*)""".toRegex()
        // Extracts the hex characters from either 0x01234ABC or 01234ABC representations
        val hexRule = """(?i)(?:0x)?([0-9a-f]+)""".toRegex()
        // Parses intent. Sample: Intent { act=android.intent.action.VIEW dat=MT:Y.K9042C00KA0648G00 flg=0x800000 pkg=com.google.android.gms cmp=com.google.android.gms/.home.SetupDeviceActivityQrCode }
        val intentRule = """Intent \{ act=(?<action>[.\w]+) dat=(?<data>[:.\w]+) flg=(?<flags>\w+) pkg=(?<pkg>[.\w]+) cmp=(?<cmp>[.\/\w]+) \}""".trimMargin().toRegex()
    }

    /** Parses output of dumpsys activity activities looking for blocks of " * Task{" */
    @Throws(AdbParseException::class)
    override fun parseDumpsysActivities(dumpsysActivityManagerOutput: String):
            List<AdbDumpsysActivities.Intent> {
        // The Activity Manager may have several tasks. Creates a list of " * Task{" text blocks
        val tasksOutput = dumpsysActivityManagerOutput.matchAll(tasksRule)

        // Parses every text block
        return tasksOutput.map { taskOutput -> parseTask(taskOutput) }.toList()
    }

    /** Parses output of parse for retrieving intents */
    private fun parseTask(taskOutput: String): AdbDumpsysActivities.Intent {
        val intent = AdbDumpsysActivities.Intent()

        val matchResult = intentRule.find(taskOutput)
        matchResult?.let {
            matchResult.groups["action"]?.let { intent.action = it.value }
            matchResult.groups["data"]?.let { intent.data = it.value }
            matchResult.groups["flags"]?.let {  intent.flags =
                it.value.matchFirst(hexRule).toLong(radix = 16) }
            matchResult.groups["pkg"]?.let { intent.pkg = it.value }
            matchResult.groups["cmp"]?.let {
                intent.cmp = it.value.split("/".toRegex())
            }
        }

        return intent
    }
}