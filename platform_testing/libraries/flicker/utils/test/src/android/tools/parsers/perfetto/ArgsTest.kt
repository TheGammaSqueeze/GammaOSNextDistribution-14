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

package android.tools.parsers.perfetto

import android.tools.traces.parsers.perfetto.Args
import android.tools.utils.assertThrows
import com.google.common.truth.Truth
import java.lang.ClassCastException
import org.junit.Test

/** Tests for [Args] */
class ArgsTest {
    @Test
    fun getValue() {
        val args =
            makeArgs(
                listOf(
                    Pair("int", 10),
                    Pair("long", 100L),
                    Pair("float", 10.1f),
                    Pair("string", "text")
                )
            )

        Truth.assertThat(args.getChild("invalidChild")).isNull()
        Truth.assertThat(args.getChild("int")?.getInt()).isEqualTo(10)
        Truth.assertThat(args.getChild("long")?.getLong()).isEqualTo(100L)
        Truth.assertThat(args.getChild("float")?.getFloat()).isEqualTo(10.1f)
        Truth.assertThat(args.getChild("string")?.getString()).isEqualTo("text")

        assertThrows<ClassCastException> { args.getChild("int")?.getString() }
    }

    @Test
    fun getChild() {
        val args = makeArgs(listOf(Pair("child.grandChild0", 10), Pair("child.grandChild1", 11)))

        Truth.assertThat(args.getChild("invalidChild")).isNull()
        Truth.assertThat(args.getChild("child")).isNotNull()

        Truth.assertThat(args.getChild("child")?.getChild("invalidgrandChild")).isNull()
        Truth.assertThat(args.getChild("child")?.getChild("grandChild0")).isNotNull()
        Truth.assertThat(args.getChild("child")?.getChild("grandChild1")).isNotNull()
    }

    @Test
    fun getChildren() {
        val args =
            makeArgs(
                listOf(
                    Pair("children[0]", 10),
                    Pair("children[1]", 11),
                    Pair("children[2]", 12),
                )
            )

        Truth.assertThat(args.getChildren("invalidChildren")).isNull()

        val values = args.getChildren("children")?.map { it -> it.getInt() }
        Truth.assertThat(values).isEqualTo(listOf(10, 11, 12))
    }

    @Test
    fun canHandleComplexStructure() {
        val args =
            makeArgs(
                listOf(
                    Pair("child0", "0"),
                    Pair("child1", "1"),
                    Pair("children[0]", "10"),
                    Pair("children[1].grandChildInt", 10),
                    Pair("children[1].grandChildString", "text"),
                    Pair("children[2].grandChildren[0]", "0"),
                    Pair("children[2].grandChildren[1]", "1"),
                    Pair("otherChildren[0]", "0"),
                    Pair("otherChildren[1]", "1"),
                )
            )

        Truth.assertThat(args.getChild("child0")?.getString()).isEqualTo("0")
        Truth.assertThat(args.getChild("child1")?.getString()).isEqualTo("1")

        Truth.assertThat(args.getChildren("children")!![0].getString()).isEqualTo("10")
        Truth.assertThat(args.getChildren("children")!![1].getChild("grandChildInt")?.getInt())
            .isEqualTo(10)
        Truth.assertThat(
                args.getChildren("children")!![1].getChild("grandChildString")?.getString()
            )
            .isEqualTo("text")
        Truth.assertThat(
                args.getChildren("children")!![2].getChildren("grandChildren")!![0].getString()
            )
            .isEqualTo("0")
        Truth.assertThat(
                args.getChildren("children")!![2].getChildren("grandChildren")!![1].getString()
            )
            .isEqualTo("1")

        Truth.assertThat(args.getChildren("otherChildren")!![0].getString()).isEqualTo("0")
        Truth.assertThat(args.getChildren("otherChildren")!![1].getString()).isEqualTo("1")
    }

    companion object {
        private fun makeArgs(entries: List<Pair<String, Any>>): Args {
            return Args().apply {
                for ((key, value) in entries) {
                    add(key, value.toString(), getValueType(value))
                }
            }
        }

        private fun getValueType(value: Any): String {
            return when (value) {
                is Int -> "int"
                is Long -> "int"
                is Float -> "real"
                is String -> "string"
                else -> "unknown type"
            }
        }
    }
}
