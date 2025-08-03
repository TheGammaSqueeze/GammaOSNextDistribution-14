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

package android.tools.flicker.assertions

import android.tools.Tag
import android.tools.flicker.subject.layers.LayersTraceSubject
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES
import android.tools.traces.io.ResultReader
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.assertThrows
import android.tools.utils.newTestResultWriter
import java.io.FileNotFoundException
import org.junit.ClassRule
import org.junit.Test

/** Tests for [SubjectsParser] */
class SubjectsParserTest {

    @Test
    fun failFileNotFound() {
        val data = newTestResultWriter().write()
        data.artifact.deleteIfExists()
        val parser = SubjectsParser(ResultReader(data, TRACE_CONFIG_REQUIRE_CHANGES))
        assertThrows<FileNotFoundException> {
            parser.getSubjectOfType(Tag.ALL, LayersTraceSubject::class)
        }
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
