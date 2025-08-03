/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.metalava.apilevels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Assert

class ApiToExtensionsMapTest {
    @Test
    fun `empty input`() {
        val xml =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- No rules is a valid (albeit weird). -->
            <sdk-extensions-info>
                <sdk shortname="R-ext" name="R Extensions" id="30" reference="android/os/Build${'$'}VERSION_CODES${'$'}R" />
                <sdk shortname="S-ext" name="S Extensions" id="31" reference="android/os/Build${'$'}VERSION_CODES${'$'}S" />
                <sdk shortname="T-ext" name="T Extensions" id="33" reference="android/os/Build${'$'}VERSION_CODES${'$'}T" />
            </sdk-extensions-info>
        """
                .trimIndent()
        val map = ApiToExtensionsMap.fromXml("no-module", xml)

        assertTrue(map.getExtensions("com.foo.Bar").isEmpty())
    }

    @Test
    fun wildcard() {
        val xml =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- All APIs will default to extension SDK A. -->
            <sdk-extensions-info>
                <sdk shortname="A" name="A Extensions" id="1" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" />
                <symbol jar="mod" pattern="*" sdks="A" />
            </sdk-extensions-info>
        """
                .trimIndent()
        val map = ApiToExtensionsMap.fromXml("mod", xml)

        assertEquals(map.getExtensions("com.foo.Bar"), listOf("A"))
        assertEquals(map.getExtensions("com.foo.SomeOtherBar"), listOf("A"))
    }

    @Test
    fun `single class`() {
        val xml =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- A single class. The class, any internal classes, and any methods are allowed;
                 everything else is denied -->
            <sdk-extensions-info>
                <sdk shortname="A" name="A Extensions" id="1" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" />
                <symbol jar="mod" pattern="com.foo.Bar" sdks="A" />
            </sdk-extensions-info>
        """
                .trimIndent()
        val map = ApiToExtensionsMap.fromXml("mod", xml)

        assertEquals(map.getExtensions("com.foo.Bar"), listOf("A"))
        assertEquals(map.getExtensions("com.foo.Bar#FIELD"), listOf("A"))
        assertEquals(map.getExtensions("com.foo.Bar#method"), listOf("A"))
        assertEquals(map.getExtensions("com.foo.Bar\$Inner"), listOf("A"))
        assertEquals(map.getExtensions("com.foo.Bar\$Inner\$InnerInner"), listOf("A"))

        val clazz = ApiClass("com/foo/Bar", 1, false)
        val method = ApiElement("method(Ljava.lang.String;I)V", 2, false)
        assertEquals(map.getExtensions(clazz), listOf("A"))
        assertEquals(map.getExtensions(clazz, method), listOf("A"))

        assertTrue(map.getExtensions("com.foo.SomeOtherClass").isEmpty())
    }

    @Test
    fun `multiple extensions`() {
        val xml =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- Any number of white space separated extension SDKs may be listed. -->
            <sdk-extensions-info>
                <sdk shortname="A" name="A Extensions" id="1" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" />
                <sdk shortname="B" name="B Extensions" id="2" reference="android/os/Build${'$'}VERSION_CODES${'$'}B" />
                <sdk shortname="FOO" name="FOO Extensions" id="10" reference="android/os/Build${'$'}VERSION_CODES${'$'}FOO" />
                <sdk shortname="BAR" name="BAR Extensions" id="11" reference="android/os/Build${'$'}VERSION_CODES${'$'}BAR" />
                <symbol jar="mod" pattern="*" sdks="A,B,FOO,BAR" />
            </sdk-extensions-info>
        """
                .trimIndent()
        val map = ApiToExtensionsMap.fromXml("mod", xml)

        assertEquals(listOf("A", "B", "FOO", "BAR"), map.getExtensions("com.foo.Bar"))
    }

    @Test
    fun precedence() {
        val xml =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- Multiple classes, and multiple rules with different precedence. -->
            <sdk-extensions-info>
                <sdk shortname="A" name="A Extensions" id="1" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" />
                <sdk shortname="B" name="B Extensions" id="2" reference="android/os/Build${'$'}VERSION_CODES${'$'}B" />
                <sdk shortname="C" name="C Extensions" id="3" reference="android/os/Build${'$'}VERSION_CODES${'$'}C" />
                <sdk shortname="D" name="D Extensions" id="4" reference="android/os/Build${'$'}VERSION_CODES${'$'}D" />
                <symbol jar="mod" pattern="*" sdks="A" />
                <symbol jar="mod" pattern="com.foo.Bar" sdks="B" />
                <symbol jar="mod" pattern="com.foo.Bar${'$'}Inner#method" sdks="C" />
                <symbol jar="mod" pattern="com.bar.Foo" sdks="D" />
            </sdk-extensions-info>
        """
                .trimIndent()
        val map = ApiToExtensionsMap.fromXml("mod", xml)

        assertEquals(map.getExtensions("anything"), listOf("A"))

        assertEquals(map.getExtensions("com.foo.Bar"), listOf("B"))
        assertEquals(map.getExtensions("com.foo.Bar#FIELD"), listOf("B"))
        assertEquals(map.getExtensions("com.foo.Bar\$Inner"), listOf("B"))

        assertEquals(map.getExtensions("com.foo.Bar\$Inner#method"), listOf("C"))

        assertEquals(map.getExtensions("com.bar.Foo"), listOf("D"))
        assertEquals(map.getExtensions("com.bar.Foo#FIELD"), listOf("D"))
    }

    @Test
    fun `multiple mainline modules`() {
        val xml =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- The allow list will only consider patterns that are marked with the given mainline module -->
            <sdk-extensions-info>
                <sdk shortname="A" name="A Extensions" id="1" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" />
                <sdk shortname="B" name="B Extensions" id="2" reference="android/os/Build${'$'}VERSION_CODES${'$'}B" />
                <symbol jar="foo" pattern="*" sdks="A" />
                <symbol jar="bar" pattern="*" sdks="B" />
            </sdk-extensions-info>
        """
                .trimIndent()
        val allowListA = ApiToExtensionsMap.fromXml("foo", xml)
        val allowListB = ApiToExtensionsMap.fromXml("bar", xml)
        val allowListC = ApiToExtensionsMap.fromXml("baz", xml)

        assertEquals(allowListA.getExtensions("anything"), listOf("A"))
        assertEquals(allowListB.getExtensions("anything"), listOf("B"))
        assertTrue(allowListC.getExtensions("anything").isEmpty())
    }

    @Test
    fun `declarations and rules can be mixed`() {
        val xml =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- SDK declarations and rule lines can be mixed in any order -->
            <sdk-extensions-info>
                <sdk shortname="A" name="A Extensions" id="1" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" />
                <symbol jar="foo" pattern="*" sdks="A,B" />
                <sdk shortname="B" name="B Extensions" id="2" reference="android/os/Build${'$'}VERSION_CODES${'$'}B" />
            </sdk-extensions-info>
        """
                .trimIndent()
        val map = ApiToExtensionsMap.fromXml("foo", xml)

        assertEquals(map.getExtensions("com.foo.Bar"), listOf("A", "B"))
    }

    @Test
    fun `bad input`() {
        assertFailsWith<IllegalArgumentException> {
            ApiToExtensionsMap.fromXml(
                "mod",
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!-- Missing root element -->
                    <sdk shortname="A" name="A Extensions" id="1" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" />
                    <symbol jar="mod" pattern="com.foo.Bar" sdks="A" />
                """
                    .trimIndent()
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ApiToExtensionsMap.fromXml(
                "mod",
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!-- <sdk> tag at unexpected depth  -->
                    <sdk-extensions-info version="2">
                        <foo>
                            <sdk shortname="A" name="A Extensions" id="1" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" >
                        </foo>
                        <symbol jar="mod" pattern="com.foo.Bar" sdks="A" />
                    </sdk-extensions-info>
                """
                    .trimIndent()
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ApiToExtensionsMap.fromXml(
                "mod",
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!-- using 0 (reserved for the Android platform SDK) as ID -->
                    <sdk-extensions-info>
                        <sdk shortname="A" name="A Extensions" id="0" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" />
                        <symbol jar="mod" pattern="com.foo.Bar" sdks="A" />
                    </sdk-extensions-info>
                """
                    .trimIndent()
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ApiToExtensionsMap.fromXml(
                "mod",
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!-- missing module attribute -->
                    <sdk-extensions-info>
                        <sdk shortname="A" name="A Extensions" id="1" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" />
                        <symbol pattern="com.foo.Bar" sdks="A" />
                    </sdk-extensions-info>
                """
                    .trimIndent()
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ApiToExtensionsMap.fromXml(
                "mod",
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!-- duplicate module+pattern pairs -->
                    <sdk-extensions-info>
                        <sdk shortname="A" name="A Extensions" id="1" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" />
                        <symbol jar="mod" pattern="com.foo.Bar" sdks="A" />
                        <symbol jar="mod" pattern="com.foo.Bar" sdks="B" />
                    </sdk-extensions-info>
                """
                    .trimIndent()
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ApiToExtensionsMap.fromXml(
                "mod",
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!-- sdks attribute refer to non-declared SDK -->
                    <sdk-extensions-info>
                        <sdk shortname="B" name="A Extensions" id="2" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" />
                        <symbol jar="mod" pattern="com.foo.Bar" sdks="A" />
                    </sdk-extensions-info>
                """
                    .trimIndent()
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ApiToExtensionsMap.fromXml(
                "mod",
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!-- duplicate numerical ID -->
                    <sdk-extensions-info>
                        <sdk shortname="A" name="A Extensions" id="1" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" />
                        <sdk shortname="B" name="B Extensions" id="1" reference="android/os/Build${'$'}VERSION_CODES${'$'}B" />
                        <symbol jar="mod" pattern="com.foo.Bar" sdks="A" />
                    </sdk-extensions-info>
                """
                    .trimIndent()
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ApiToExtensionsMap.fromXml(
                "mod",
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!-- duplicate short SDK name -->
                    <sdk-extensions-info>
                        <sdk shortname="A" name="A Extensions" id="1" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" />
                        <sdk shortname="A" name="B Extensions" id="2" reference="android/os/Build${'$'}VERSION_CODES${'$'}B" />
                        <symbol jar="mod" pattern="com.foo.Bar" sdks="A" />
                    </sdk-extensions-info>
                """
                    .trimIndent()
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ApiToExtensionsMap.fromXml(
                "mod",
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!-- duplicate long SDK name -->
                    <sdk-extensions-info>
                        <sdk shortname="A" name="A Extensions" id="1" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" />
                        <sdk shortname="B" name="A Extensions" id="2" reference="android/os/Build${'$'}VERSION_CODES${'$'}B" />
                        <symbol jar="mod" pattern="com.foo.Bar" sdks="A" />
                    </sdk-extensions-info>
                """
                    .trimIndent()
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ApiToExtensionsMap.fromXml(
                "mod",
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!-- duplicate SDK reference -->
                    <sdk-extensions-info version="1">
                        <sdk shortname="A" name="A Extensions" id="1" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" />
                        <sdk shortname="B" name="B Extensions" id="2" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" />
                        <symbol jar="mod" pattern="com.foo.Bar" sdks="A" />
                    </sdk-extensions-info>
                """
                    .trimIndent()
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ApiToExtensionsMap.fromXml(
                "mod",
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!-- duplicate SDK for same symbol -->
                    <sdk-extensions-info>
                        <sdk shortname="A" name="A Extensions" id="1" reference="android/os/Build${'$'}VERSION_CODES${'$'}A" />
                        <sdk shortname="B" name="B Extensions" id="1" reference="android/os/Build${'$'}VERSION_CODES${'$'}B" />
                        <symbol jar="mod" pattern="com.foo.Bar" sdks="A,B,A" />
                    </sdk-extensions-info>
                """
                    .trimIndent()
            )
        }
    }

    @Test
    fun `calculate sdks xml attribute`() {
        val xml =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- Verify the calculateSdksAttr method -->
            <sdk-extensions-info>
                <sdk shortname="R" name="R Extensions" id="30" reference="android/os/Build${'$'}VERSION_CODES${'$'}R" />
                <sdk shortname="S" name="S Extensions" id="31" reference="android/os/Build${'$'}VERSION_CODES${'$'}S" />
                <sdk shortname="T" name="T Extensions" id="33" reference="android/os/Build${'$'}VERSION_CODES${'$'}T" />
                <sdk shortname="FOO" name="FOO Extensions" id="1000" reference="android/os/Build${'$'}VERSION_CODES${'$'}FOO" />
                <sdk shortname="BAR" name="BAR Extensions" id="1001" reference="android/os/Build${'$'}VERSION_CODES${'$'}BAR" />
            </sdk-extensions-info>
        """
                .trimIndent()
        val filter = ApiToExtensionsMap.fromXml("mod", xml)

        Assert.assertEquals("0:34", filter.calculateSdksAttr(34, 34, listOf(), ApiElement.NEVER))

        Assert.assertEquals("30:4", filter.calculateSdksAttr(34, 34, listOf("R"), 4))

        Assert.assertEquals("30:4,31:4", filter.calculateSdksAttr(34, 34, listOf("R", "S"), 4))

        Assert.assertEquals("30:4,31:4,0:33", filter.calculateSdksAttr(33, 34, listOf("R", "S"), 4))

        Assert.assertEquals(
            "30:4,31:4,1000:4,0:33",
            filter.calculateSdksAttr(33, 34, listOf("R", "S", "FOO"), 4)
        )

        Assert.assertEquals(
            "30:4,31:4,1000:4,1001:4,0:33",
            filter.calculateSdksAttr(33, 34, listOf("R", "S", "FOO", "BAR"), 4)
        )
    }
}
