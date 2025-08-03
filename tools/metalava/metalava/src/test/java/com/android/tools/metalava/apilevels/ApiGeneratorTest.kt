/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.metalava.ARG_ANDROID_JAR_PATTERN
import com.android.tools.metalava.ARG_API_VERSION_NAMES
import com.android.tools.metalava.ARG_API_VERSION_SIGNATURE_FILES
import com.android.tools.metalava.ARG_CURRENT_CODENAME
import com.android.tools.metalava.ARG_CURRENT_VERSION
import com.android.tools.metalava.ARG_FIRST_VERSION
import com.android.tools.metalava.ARG_GENERATE_API_LEVELS
import com.android.tools.metalava.ARG_GENERATE_API_VERSION_HISTORY
import com.android.tools.metalava.ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS
import com.android.tools.metalava.ARG_SDK_INFO_FILE
import com.android.tools.metalava.ARG_SDK_JAR_ROOT
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.doc.getApiLookup
import com.android.tools.metalava.doc.minApiLevel
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import java.io.File
import kotlin.text.Charsets.UTF_8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test

class ApiGeneratorTest : DriverTest() {

    private val oldSdkJars by
        lazy(LazyThreadSafetyMode.NONE) {
            File("../../../prebuilts/tools/common/api-versions").apply {
                if (!isDirectory) {
                    fail("prebuilts for old sdk jars not found: $this")
                }
            }
        }

    private val platformJars by
        lazy(LazyThreadSafetyMode.NONE) {
            File("../../../prebuilts/sdk").apply {
                if (!isDirectory) {
                    fail("prebuilts for platform jars not found: $this")
                }
            }
        }

    private val extensionSdkJars by
        lazy(LazyThreadSafetyMode.NONE) {
            platformJars.resolve("extensions").apply {
                if (!isDirectory) {
                    fail("prebuilts for extension jars not found: $this")
                }
            }
        }

    companion object {
        // As per ApiConstraint that uses a bit vector, API has to be between 1..61.
        private const val MAGIC_VERSION_INT = 57 // [SdkVersionInfo.MAX_LEVEL] - 4
        private const val MAGIC_VERSION_STR = MAGIC_VERSION_INT.toString()

        @JvmStatic
        @BeforeClass
        fun beforeClass() {
            assert(MAGIC_VERSION_INT > SdkVersionInfo.HIGHEST_KNOWN_API)
            // Trigger <clinit> of [SdkApiConstraint] to call `isValidApiLevel` in its companion
            ApiConstraint.UNKNOWN
            // This checks if MAGIC_VERSION_INT is not bigger than [SdkVersionInfo.MAX_LEVEL]
            assert(ApiConstraint.SdkApiConstraint.isValidApiLevel(MAGIC_VERSION_INT))
        }
    }

    @Test
    fun `Extract API levels`() {
        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    outputPath,
                    ARG_ANDROID_JAR_PATTERN,
                    "${oldSdkJars.path}/android-%/android.jar",
                    ARG_ANDROID_JAR_PATTERN,
                    "${platformJars.path}/%/public/android.jar",
                    ARG_CURRENT_CODENAME,
                    "Z",
                    ARG_CURRENT_VERSION,
                    MAGIC_VERSION_STR // not real api level of Z
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    public class MyTest {
                    }
                    """
                    )
                )
        )

        assertTrue(output.isFile)

        val xml = output.readText(UTF_8)
        val nextVersion = MAGIC_VERSION_INT + 1
        assertTrue(xml.contains("<class name=\"android/Manifest\$permission\" since=\"1\">"))
        assertTrue(
            xml.contains(
                "<field name=\"BIND_CARRIER_MESSAGING_SERVICE\" since=\"22\" deprecated=\"23\"/>"
            )
        )
        assertTrue(xml.contains("<class name=\"android/pkg/MyTest\" since=\"$nextVersion\""))
        assertFalse(xml.contains("<implements name=\"java/lang/annotation/Annotation\" removed=\""))
        assertFalse(xml.contains("<extends name=\"java/lang/Enum\" removed=\""))
        assertFalse(xml.contains("<method name=\"append(C)Ljava/lang/AbstractStringBuilder;\""))

        // Also make sure package private super classes are pruned
        assertFalse(xml.contains("<extends name=\"android/icu/util/CECalendar\""))

        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())

        // Make sure we're really using the correct database, not the SDK one. (This placeholder
        // class is provided as a source file above.)
        @Suppress("DEPRECATION")
        assertEquals(nextVersion, apiLookup.getClassVersion("android.pkg.MyTest"))

        @Suppress("DEPRECATION") apiLookup.getClassVersion("android.v")
        @Suppress("DEPRECATION")
        assertEquals(
            5,
            apiLookup.getFieldVersion("android.Manifest\$permission", "AUTHENTICATE_ACCOUNTS")
        )

        @Suppress("DEPRECATION")
        val methodVersion =
            apiLookup.getMethodVersion("android/icu/util/CopticCalendar", "computeTime", "()")
        assertEquals(24, methodVersion)
    }

    @Test
    fun `Extract System API`() {
        // These are the wrong jar paths but this test doesn't actually care what the
        // content of the jar files, just checking the logic of starting the database
        // at some higher number than 1
        val androidJarPattern = "${platformJars.path}/%/public/android.jar"

        val filter = File.createTempFile("filter", "txt")
        filter.deleteOnExit()
        filter.writeText(
            """
                <sdk-extensions-info>
                <!-- SDK definitions -->
                <sdk shortname="R" name="R Extensions" id="30" reference="android/os/Build${'$'}VERSION_CODES${'$'}R" />
                <sdk shortname="S" name="S Extensions" id="31" reference="android/os/Build${'$'}VERSION_CODES${'$'}S" />
                <sdk shortname="T" name="T Extensions" id="33" reference="android/os/Build${'$'}VERSION_CODES${'$'}T" />

                <!-- Rules -->
                <symbol jar="art.module.public.api" pattern="*" sdks="R" />
                <symbol jar="conscrypt.module.intra.core.api " pattern="" sdks="R" />
                <symbol jar="conscrypt.module.platform.api" pattern="*" sdks="R" />
                <symbol jar="conscrypt.module.public.api" pattern="*" sdks="R" />
                <symbol jar="framework-mediaprovider" pattern="*" sdks="R" />
                <symbol jar="framework-mediaprovider" pattern="android.provider.MediaStore#canManageMedia" sdks="T" />
                <symbol jar="framework-permission-s" pattern="*" sdks="R" />
                <symbol jar="framework-permission" pattern="*" sdks="R" />
                <symbol jar="framework-sdkextensions" pattern="*" sdks="R" />
                <symbol jar="framework-scheduling" pattern="*" sdks="R" />
                <symbol jar="framework-statsd" pattern="*" sdks="R" />
                <symbol jar="framework-tethering" pattern="*" sdks="R" />
                <symbol jar="legacy.art.module.platform.api" pattern="*" sdks="R" />
                <symbol jar="service-media-s" pattern="*" sdks="R" />
                <symbol jar="service-permission" pattern="*" sdks="R" />

                <!-- use framework-permissions-s to test the order of multiple SDKs is respected -->
                <symbol jar="android.net.ipsec.ike" pattern="android.net.eap.EapAkaInfo" sdks="R,S,T" />
                <symbol jar="android.net.ipsec.ike" pattern="android.net.eap.EapInfo" sdks="T,S,R" />
                <symbol jar="android.net.ipsec.ike" pattern="*" sdks="R" />

                <!-- framework-connectivity: only android.net.CaptivePortal should have the 'sdks' attribute -->
                <symbol jar="framework-connectivity" pattern="android.net.CaptivePortal" sdks="R" />

                <!-- framework-media explicitly omitted: nothing in this module should have the 'sdks' attribute -->
                </sdk-extensions-info>
            """
                .trimIndent()
        )

        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    outputPath,
                    ARG_ANDROID_JAR_PATTERN,
                    androidJarPattern,
                    ARG_SDK_JAR_ROOT,
                    "$extensionSdkJars",
                    ARG_SDK_INFO_FILE,
                    filter.path,
                    ARG_FIRST_VERSION,
                    "21",
                    ARG_CURRENT_VERSION,
                    "33"
                )
        )

        assertTrue(output.isFile)
        val xml = output.readText(UTF_8)
        assertTrue(xml.contains("<api version=\"3\" min=\"21\">"))
        assertTrue(
            xml.contains(
                "<sdk id=\"30\" shortname=\"R\" name=\"R Extensions\" reference=\"android/os/Build\$VERSION_CODES\$R\"/>"
            )
        )
        assertTrue(
            xml.contains(
                "<sdk id=\"31\" shortname=\"S\" name=\"S Extensions\" reference=\"android/os/Build\$VERSION_CODES\$S\"/>"
            )
        )
        assertTrue(
            xml.contains(
                "<sdk id=\"33\" shortname=\"T\" name=\"T Extensions\" reference=\"android/os/Build\$VERSION_CODES\$T\"/>"
            )
        )
        assertTrue(xml.contains("<class name=\"android/Manifest\" since=\"21\">"))
        assertTrue(xml.contains("<field name=\"showWhenLocked\" since=\"27\"/>"))

        // top level class marked as since=21 and R=1, implemented in the framework-mediaprovider
        // mainline module
        assertTrue(
            xml.contains(
                "<class name=\"android/provider/MediaStore\" module=\"framework-mediaprovider\" since=\"21\" sdks=\"30:1,0:21\">"
            )
        )

        // method with identical sdks attribute as containing class: sdks attribute should be
        // omitted
        assertTrue(xml.contains("<method name=\"getMediaScannerUri()Landroid/net/Uri;\"/>"))

        // method with different sdks attribute than containing class
        assertTrue(
            xml.contains(
                "<method name=\"canManageMedia(Landroid/content/Context;)Z\" since=\"31\" sdks=\"33:1,0:31\"/>"
            )
        )

        val apiLookup = getApiLookup(output)
        @Suppress("DEPRECATION") apiLookup.getClassVersion("android.v")
        // This field was added in API level 5, but when we're starting the count higher
        // (as in the system API), the first introduced API level is the one we use
        @Suppress("DEPRECATION")
        assertEquals(
            21,
            apiLookup.getFieldVersion("android.Manifest\$permission", "AUTHENTICATE_ACCOUNTS")
        )

        @Suppress("DEPRECATION")
        val methodVersion =
            apiLookup.getMethodVersion("android/icu/util/CopticCalendar", "computeTime", "()")
        assertEquals(24, methodVersion)

        // The filter says 'framework-permission-s             *    R' so RoleManager should exist
        // and should have a module/sdks attributes
        assertTrue(apiLookup.containsClass("android/app/role/RoleManager"))
        assertTrue(
            xml.contains(
                "<method name=\"canManageMedia(Landroid/content/Context;)Z\" since=\"31\" sdks=\"33:1,0:31\"/>"
            )
        )

        // The filter doesn't mention framework-media, so no class in that module should have a
        // module/sdks attributes
        assertTrue(xml.contains("<class name=\"android/media/MediaFeature\" since=\"31\">"))

        // The filter only defines a single API in framework-connectivity: verify that only that API
        // has the module/sdks attributes
        assertTrue(
            xml.contains(
                "<class name=\"android/net/CaptivePortal\" module=\"framework-connectivity\" since=\"23\" sdks=\"30:1,0:23\">"
            )
        )
        assertTrue(
            xml.contains("<class name=\"android/net/ConnectivityDiagnosticsManager\" since=\"30\">")
        )

        // The order of the SDKs should be respected
        // android.net.eap.EapAkaInfo    R S T -> 0,30,31,33
        assertTrue(
            xml.contains(
                "<class name=\"android/net/eap/EapAkaInfo\" module=\"android.net.ipsec.ike\" since=\"33\" sdks=\"30:3,31:3,33:3,0:33\">"
            )
        )
        // android.net.eap.EapInfo       T S R -> 0,33,31,30
        assertTrue(
            xml.contains(
                "<class name=\"android/net/eap/EapInfo\" module=\"android.net.ipsec.ike\" since=\"33\" sdks=\"33:3,31:3,30:3,0:33\">"
            )
        )

        // Verify historical backfill
        assertEquals(30, apiLookup.getClassVersions("android/os/ext/SdkExtensions").minApiLevel())
        assertEquals(
            30,
            apiLookup
                .getMethodVersions("android/os/ext/SdkExtensions", "getExtensionVersion", "(I)I")
                .minApiLevel()
        )
        assertEquals(
            31,
            apiLookup
                .getMethodVersions(
                    "android/os/ext/SdkExtensions",
                    "getAllExtensionVersions",
                    "()Ljava/util/Map;"
                )
                .minApiLevel()
        )

        // Verify there's no extension versions listed for SdkExtensions
        val sdkExtClassLine =
            xml.lines().first { it.contains("<class name=\"android/os/ext/SdkExtensions\"") }
        assertFalse(sdkExtClassLine.contains("sdks="))
    }

    @Test
    fun `Correct API Level for release`() {
        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    outputPath,
                    ARG_ANDROID_JAR_PATTERN,
                    "${oldSdkJars.path}/android-%/android.jar",
                    ARG_ANDROID_JAR_PATTERN,
                    "${platformJars.path}/%/public/android.jar",
                    ARG_CURRENT_CODENAME,
                    "REL",
                    ARG_CURRENT_VERSION,
                    MAGIC_VERSION_STR // not real api level
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.pkg;
                        public class MyTest {
                        }
                        """
                    )
                )
        )

        assertTrue(output.isFile)
        // Anything with a REL codename is in the current API level
        val xml = output.readText(UTF_8)
        assertTrue(xml.contains("<class name=\"android/pkg/MyTest\" since=\"$MAGIC_VERSION_STR\""))
        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())
        @Suppress("DEPRECATION")
        assertEquals(MAGIC_VERSION_INT, apiLookup.getClassVersion("android.pkg.MyTest"))
    }

    @Test
    fun `Correct API Level for non-release`() {
        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    outputPath,
                    ARG_ANDROID_JAR_PATTERN,
                    "${oldSdkJars.path}/android-%/android.jar",
                    ARG_ANDROID_JAR_PATTERN,
                    "${platformJars.path}/%/public/android.jar",
                    ARG_CURRENT_CODENAME,
                    "ZZZ", // not just Z, but very ZZZ
                    ARG_CURRENT_VERSION,
                    MAGIC_VERSION_STR // not real api level
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.pkg;
                        public class MyTest {
                        }
                        """
                    )
                )
        )

        assertTrue(output.isFile)
        // Metalava should understand that a codename means "current api + 1"
        val nextVersion = MAGIC_VERSION_INT + 1
        val xml = output.readText(UTF_8)
        assertTrue(xml.contains("<class name=\"android/pkg/MyTest\" since=\"$nextVersion\""))
        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())
        @Suppress("DEPRECATION")
        assertEquals(nextVersion, apiLookup.getClassVersion("android.pkg.MyTest"))
    }

    @Test
    fun `Generate API for test prebuilts`() {
        var testPrebuiltsRoot = File(System.getenv("METALAVA_TEST_PREBUILTS_SDK_ROOT"))
        if (!testPrebuiltsRoot.isDirectory) {
            fail("test prebuilts not found: $testPrebuiltsRoot")
        }

        val api_versions_xml = File.createTempFile("api-versions", "xml")
        api_versions_xml.deleteOnExit()

        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    api_versions_xml.path,
                    ARG_ANDROID_JAR_PATTERN,
                    "${testPrebuiltsRoot.path}/%/public/android.jar",
                    ARG_SDK_JAR_ROOT,
                    "${testPrebuiltsRoot.path}/extensions",
                    ARG_SDK_INFO_FILE,
                    "${testPrebuiltsRoot.path}/sdk-extensions-info.xml",
                    ARG_FIRST_VERSION,
                    "30",
                    ARG_CURRENT_VERSION,
                    "32",
                    ARG_CURRENT_CODENAME,
                    "Foo"
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.test;
                    public class ClassAddedInApi31AndExt2 {
                        private ClassAddedInApi31AndExt2() {}
                        public static final int FIELD_ADDED_IN_API_31_AND_EXT_2 = 1;
                        public static final int FIELD_ADDED_IN_EXT_3 = 2;
                        public void methodAddedInApi31AndExt2() { throw new RuntimeException("Stub!"); }
                        public void methodAddedInExt3() { throw new RuntimeException("Stub!"); };
                        public void methodNotFinalized() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )

        assertTrue(api_versions_xml.isFile)
        val xml = api_versions_xml.readText(UTF_8)

        val expected =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <api version="3" min="30">
                <sdk id="30" shortname="R-ext" name="R Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}R"/>
                <sdk id="31" shortname="S-ext" name="S Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}S"/>
                <class name="android/test/ClassAddedInApi30" since="30">
                    <extends name="java/lang/Object"/>
                    <method name="methodAddedInApi30()V"/>
                    <method name="methodAddedInApi31()V" since="31"/>
                </class>
                <class name="android/test/ClassAddedInApi31AndExt2" module="framework-ext" since="31" sdks="30:2,31:2,0:31">
                    <extends name="java/lang/Object"/>
                    <method name="methodAddedInApi31AndExt2()V"/>
                    <method name="methodAddedInExt3()V" since="33" sdks="30:3,31:3"/>
                    <method name="methodNotFinalized()V" since="33" sdks="0:33"/>
                    <field name="FIELD_ADDED_IN_API_31_AND_EXT_2"/>
                    <field name="FIELD_ADDED_IN_EXT_3" since="33" sdks="30:3,31:3"/>
                </class>
                <class name="android/test/ClassAddedInExt1" module="framework-ext" since="31" sdks="30:1,31:1,0:31">
                    <extends name="java/lang/Object"/>
                    <method name="methodAddedInApi31AndExt2()V" sdks="30:2,31:2,0:31"/>
                    <method name="methodAddedInExt1()V"/>
                    <method name="methodAddedInExt3()V" since="33" sdks="30:3,31:3"/>
                    <field name="FIELD_ADDED_IN_API_31_AND_EXT_2" sdks="30:2,31:2,0:31"/>
                    <field name="FIELD_ADDED_IN_EXT_1"/>
                    <field name="FIELD_ADDED_IN_EXT_3" since="33" sdks="30:3,31:3"/>
                </class>
                <class name="android/test/ClassAddedInExt3" module="framework-ext" since="33" sdks="30:3,31:3">
                    <extends name="java/lang/Object"/>
                    <method name="methodAddedInExt3()V"/>
                    <field name="FIELD_ADDED_IN_EXT_3"/>
                </class>
                <class name="java/lang/Object" since="30">
                    <method name="&lt;init>()V"/>
                </class>
            </api>
        """

        fun String.trimEachLine(): String =
            lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

        assertEquals(expected.trimEachLine(), xml.trimEachLine())
    }

    @Test
    fun `Generate API for test prebuilts skip SDK extensions 3+`() {
        var testPrebuiltsRoot = File(System.getenv("METALAVA_TEST_PREBUILTS_SDK_ROOT"))
        if (!testPrebuiltsRoot.isDirectory) {
            fail("test prebuilts not found: $testPrebuiltsRoot")
        }

        val api_versions_xml = File.createTempFile("api-versions", "xml")
        api_versions_xml.deleteOnExit()

        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    api_versions_xml.path,
                    ARG_ANDROID_JAR_PATTERN,
                    "${testPrebuiltsRoot.path}/%/public/android.jar",
                    ARG_SDK_JAR_ROOT,
                    "${testPrebuiltsRoot.path}/extensions",
                    ARG_SDK_INFO_FILE,
                    "${testPrebuiltsRoot.path}/sdk-extensions-info.xml",
                    ARG_FIRST_VERSION,
                    "30",
                    ARG_CURRENT_VERSION,
                    "32",
                    ARG_CURRENT_CODENAME,
                    "Foo",
                    "--hide-sdk-extensions-newer-than",
                    "2",
                ),
        )

        assertTrue(api_versions_xml.isFile)
        val xml = api_versions_xml.readText(UTF_8)

        val expected =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <api version="3" min="30">
                <sdk id="30" shortname="R-ext" name="R Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}R"/>
                <sdk id="31" shortname="S-ext" name="S Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}S"/>
                <class name="android/test/ClassAddedInApi30" since="30">
                    <extends name="java/lang/Object"/>
                    <method name="methodAddedInApi30()V"/>
                    <method name="methodAddedInApi31()V" since="31"/>
                </class>
                <class name="android/test/ClassAddedInApi31AndExt2" module="framework-ext" since="31" sdks="30:2,31:2,0:31">
                    <extends name="java/lang/Object"/>
                    <method name="methodAddedInApi31AndExt2()V"/>
                    <field name="FIELD_ADDED_IN_API_31_AND_EXT_2"/>
                </class>
                <class name="android/test/ClassAddedInExt1" module="framework-ext" since="31" sdks="30:1,31:1,0:31">
                    <extends name="java/lang/Object"/>
                    <method name="methodAddedInApi31AndExt2()V" sdks="30:2,31:2,0:31"/>
                    <method name="methodAddedInExt1()V"/>
                    <field name="FIELD_ADDED_IN_API_31_AND_EXT_2" sdks="30:2,31:2,0:31"/>
                    <field name="FIELD_ADDED_IN_EXT_1"/>
                </class>
                <class name="java/lang/Object" since="30">
                    <method name="&lt;init>()V"/>
                </class>
            </api>
        """

        fun String.trimEachLine(): String =
            lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

        assertEquals(expected.trimEachLine(), xml.trimEachLine())
    }

    @Test
    fun `Generate API while removing missing class references`() {
        val api_versions_xml = File.createTempFile("api-versions", "xml")
        api_versions_xml.deleteOnExit()

        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    api_versions_xml.path,
                    ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS,
                    ARG_FIRST_VERSION,
                    "30",
                    ARG_CURRENT_VERSION,
                    "32",
                    ARG_CURRENT_CODENAME,
                    "Foo"
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.test;
                    public class ClassThatImplementsMethodFromApex implements ClassFromApex {
                    }
                    """
                    )
                )
        )

        assertTrue(api_versions_xml.isFile)
        val xml = api_versions_xml.readText(UTF_8)

        val expected =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <api version="3" min="30">
                <class name="android/test/ClassThatImplementsMethodFromApex" since="33">
                    <method name="&lt;init>()V"/>
                </class>
            </api>
        """

        fun String.trimEachLine(): String =
            lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

        assertEquals(expected.trimEachLine(), xml.trimEachLine())
    }

    @Test
    fun `Generate API finds missing class references`() {
        var testPrebuiltsRoot = File(System.getenv("METALAVA_TEST_PREBUILTS_SDK_ROOT"))
        if (!testPrebuiltsRoot.isDirectory) {
            fail("test prebuilts not found: $testPrebuiltsRoot")
        }

        val api_versions_xml = File.createTempFile("api-versions", "xml")
        api_versions_xml.deleteOnExit()

        var exception: IllegalStateException? = null
        try {
            check(
                extraArguments =
                    arrayOf(
                        ARG_GENERATE_API_LEVELS,
                        api_versions_xml.path,
                        ARG_FIRST_VERSION,
                        "30",
                        ARG_CURRENT_VERSION,
                        "32",
                        ARG_CURRENT_CODENAME,
                        "Foo"
                    ),
                sourceFiles =
                    arrayOf(
                        java(
                            """
                        package android.test;
                        // Really this class should implement some interface that doesn't exist,
                        // but that's hard to set up in the test harness, so just verify that
                        // metalava complains about java/lang/Object not existing because we didn't
                        // include the testdata prebuilt jars.
                        public class ClassThatImplementsMethodFromApex {
                        }
                        """
                        )
                    )
            )
        } catch (e: IllegalStateException) {
            exception = e
        }

        assertNotNull(exception)
        assertThat(exception?.message ?: "")
            .contains(
                "There are classes in this API that reference other classes that do not exist in this API."
            )
        assertThat(exception?.message ?: "")
            .contains(
                "java/lang/Object referenced by:\n    android/test/ClassThatImplementsMethodFromApex"
            )
    }

    @Test
    fun `Create API levels from signature files`() {
        val output = File.createTempFile("api-info", ".json")
        output.deleteOnExit()
        val outputPath = output.path

        val pastVersions =
            listOf(
                createTextFile(
                    "1.1.0",
                    """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public <T extends java.lang.String> void methodV1(T);
                        field public int fieldV1;
                      }
                      public class Foo.Bar {
                      }
                    }
                """
                        .trimIndent()
                ),
                createTextFile(
                    "1.2.0",
                    """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public <T extends java.lang.String> void methodV1(T);
                        method @Deprecated public <T> void methodV2(String, int);
                        field public int fieldV1;
                        field public int fieldV2;
                      }
                      public class Foo.Bar {
                      }
                    }
                """
                        .trimIndent()
                ),
                createTextFile(
                    "1.3.0",
                    """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method @Deprecated public <T extends java.lang.String> void methodV1(T);
                        method public void methodV3();
                        field public int fieldV1;
                        field public int fieldV2;
                      }
                      @Deprecated public class Foo.Bar {
                      }
                    }
                """
                        .trimIndent()
                )
            )
        val currentVersion =
            """
            package test.pkg {
              public class Foo {
                method @Deprecated public <T extends java.lang.String> void methodV1(T);
                method public void methodV3();
                method public void methodV4();
                field public int fieldV1;
                field public int fieldV2;
              }
              @Deprecated public class Foo.Bar {
              }
            }
        """
                .trimIndent()

        check(
            signatureSource = currentVersion,
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_VERSION_HISTORY,
                    outputPath,
                    ARG_API_VERSION_SIGNATURE_FILES,
                    pastVersions.joinToString(":") { it.absolutePath },
                    ARG_API_VERSION_NAMES,
                    listOf("1.1.0", "1.2.0", "1.3.0", "1.4.0").joinToString(" "),
                )
        )

        assertTrue(output.isFile)

        // Read output and reprint with pretty printing enabled to make test failures easier to read
        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
        val outputJson = gson.fromJson(output.readText(), JsonElement::class.java)
        val prettyOutput = gson.toJson(outputJson)
        assertEquals(
            """
                [
                  {
                    "class": "test.pkg.Foo.Bar",
                    "addedIn": "1.1.0",
                    "deprecatedIn": "1.3.0",
                    "methods": [],
                    "fields": []
                  },
                  {
                    "class": "test.pkg.Foo",
                    "addedIn": "1.1.0",
                    "methods": [
                      {
                        "method": "methodV1<T extends java.lang.String>(T)",
                        "addedIn": "1.1.0",
                        "deprecatedIn": "1.3.0"
                      },
                      {
                        "method": "methodV2<T>(java.lang.String,int)",
                        "addedIn": "1.2.0",
                        "deprecatedIn": "1.2.0"
                      },
                      {
                        "method": "methodV3()",
                        "addedIn": "1.3.0"
                      },
                      {
                        "method": "methodV4()",
                        "addedIn": "1.4.0"
                      }
                    ],
                    "fields": [
                      {
                        "field": "fieldV2",
                        "addedIn": "1.2.0"
                      },
                      {
                        "field": "fieldV1",
                        "addedIn": "1.1.0"
                      }
                    ]
                  }
                ]
            """
                .trimIndent(),
            prettyOutput
        )
    }

    @Test
    fun `Correct error with different number of API signature files and API version names`() {
        val output = File.createTempFile("api-info", ".json")
        output.deleteOnExit()
        val outputPath = output.path

        val filePaths =
            listOf("1.1.0", "1.2.0", "1.3.0").map { name ->
                val file = File.createTempFile(name, ".txt")
                file.deleteOnExit()
                file.path
            }

        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_VERSION_HISTORY,
                    outputPath,
                    ARG_API_VERSION_SIGNATURE_FILES,
                    filePaths.joinToString(":"),
                    ARG_API_VERSION_NAMES,
                    listOf("1.1.0", "1.2.0").joinToString(" ")
                ),
            expectedFail =
                "Aborting: --api-version-names must have one more version than --api-version-signature-files to include the current version name"
        )
    }

    @Test
    fun `API levels can be generated from just the current codebase`() {
        val output = File.createTempFile("api-info", ".json")
        output.deleteOnExit()
        val outputPath = output.path

        val api =
            """
                // Signature format: 3.0
                package test.pkg {
                  public class Foo {
                    method public void foo(String?);
                  }
                }
        """
                .trimIndent()

        check(
            signatureSource = api,
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_VERSION_HISTORY,
                    outputPath,
                    ARG_API_VERSION_NAMES,
                    "0.0.0"
                )
        )

        val expectedJson =
            "[{\"class\":\"test.pkg.Foo\",\"addedIn\":\"0.0.0\",\"methods\":[{\"method\":\"foo(java.lang.String)\",\"addedIn\":\"0.0.0\"}],\"fields\":[]}]"
        assertEquals(expectedJson, output.readText())
    }

    @Test
    fun `API levels using source as current version does not include inherited methods excluded from signatures`() {
        val output = File.createTempFile("api-info", ".json")
        output.deleteOnExit()
        val outputPath = output.path

        val pastVersions =
            listOf(
                createTextFile(
                    "1.1.0",
                    """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Bar extends test.pkg.Foo {
                        ctor public Bar();
                      }
                      public class Foo {
                        ctor public Foo();
                        method public void inherited();
                      }
                    }
                """
                        .trimIndent()
                )
            )
        val currentVersion =
            arrayOf(
                java(
                    """
                        package test.pkg;

                        public class Foo {
                            public void inherited() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Bar extends Foo {
                            @Override
                            public void inherited() {}
                        }
                    """
                )
            )

        check(
            sourceFiles = currentVersion,
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_VERSION_HISTORY,
                    outputPath,
                    ARG_API_VERSION_SIGNATURE_FILES,
                    pastVersions.joinToString(":") { it.absolutePath },
                    ARG_API_VERSION_NAMES,
                    listOf("1.1.0", "1.2.0").joinToString(" ")
                )
        )

        assertTrue(output.isFile)

        // Read output and reprint with pretty printing enabled to make test failures easier to read
        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
        val outputJson = gson.fromJson(output.readText(), JsonElement::class.java)
        val prettyOutput = gson.toJson(outputJson)
        assertEquals(
            """
                [
                  {
                    "class": "test.pkg.Bar",
                    "addedIn": "1.1.0",
                    "methods": [
                      {
                        "method": "Bar()",
                        "addedIn": "1.1.0"
                      }
                    ],
                    "fields": []
                  },
                  {
                    "class": "test.pkg.Foo",
                    "addedIn": "1.1.0",
                    "methods": [
                      {
                        "method": "Foo()",
                        "addedIn": "1.1.0"
                      },
                      {
                        "method": "inherited()",
                        "addedIn": "1.1.0"
                      }
                    ],
                    "fields": []
                  }
                ]
            """
                .trimIndent(),
            prettyOutput
        )
    }

    @Test
    fun `APIs annotated with suppress-compatibility-meta-annotations appear in output`() {
        val output = File.createTempFile("api-info", ".json")
        output.deleteOnExit()
        val outputPath = output.path

        val pastVersions =
            listOf(
                createTextFile(
                    "1.1.0.txt",
                    """
                    // Signature format: 4.0
                    package test.pkg {
                      public final class Foo {
                        ctor public Foo();
                        method @SuppressCompatibility @kotlin.RequiresOptIn public void experimentalFunction();
                        method public void regularFunction();
                      }
                    }
                """
                        .trimIndent(),
                )
            )
        val currentVersion =
            arrayOf(
                kotlin(
                    """
                    package test.pkg
                    class Foo {
                        @RequiresOptIn fun experimentalFunction() {}
                        @RequiresOptIn fun newExperimentalFunction() {}
                        fun regularFunction() {}
                    }
                """
                        .trimIndent()
                )
            )

        check(
            sourceFiles = currentVersion,
            suppressCompatibilityMetaAnnotations = arrayOf("kotlin.RequiresOptIn"),
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_VERSION_HISTORY,
                    outputPath,
                    ARG_API_VERSION_SIGNATURE_FILES,
                    pastVersions.joinToString(":") { it.absolutePath },
                    ARG_API_VERSION_NAMES,
                    listOf("1.1.0", "1.2.0").joinToString(" ")
                )
        )

        assertTrue(output.isFile)

        // Read output and reprint with pretty printing enabled to make test failures easier to read
        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
        val outputJson = gson.fromJson(output.readText(), JsonElement::class.java)
        val prettyOutput = gson.toJson(outputJson)
        assertEquals(
            """
                [
                  {
                    "class": "test.pkg.Foo",
                    "addedIn": "1.1.0",
                    "methods": [
                      {
                        "method": "experimentalFunction()",
                        "addedIn": "1.1.0"
                      },
                      {
                        "method": "Foo()",
                        "addedIn": "1.1.0"
                      },
                      {
                        "method": "newExperimentalFunction()",
                        "addedIn": "1.2.0"
                      },
                      {
                        "method": "regularFunction()",
                        "addedIn": "1.1.0"
                      }
                    ],
                    "fields": []
                  }
                ]
            """
                .trimIndent(),
            prettyOutput
        )
    }

    private fun createTextFile(name: String, contents: String): File {
        val file = File.createTempFile(name, ".txt")
        file.deleteOnExit()
        file.writeText(contents)
        return file
    }
}
