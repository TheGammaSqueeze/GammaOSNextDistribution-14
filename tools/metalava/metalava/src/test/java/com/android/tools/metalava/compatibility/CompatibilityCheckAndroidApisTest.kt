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

package com.android.tools.metalava.compatibility

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.cli.common.ARG_WARNING
import com.android.tools.metalava.testing.getAndroidJar
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Test

abstract class CompatibilityCheckAndroidApisTest(
    private val apiLevelCheck: ApiLevelCheck,
) : DriverTest() {

    data class ApiLevelCheck(
        val apiLevel: Int,
        val expectedIssues: String,
        val extraArgs: List<String>,
        val disabled: Boolean = false,
    ) {
        override fun toString(): String = "${apiLevel - 1} to $apiLevel"
    }

    companion object {
        private val DEFAULT_HIDDEN_ISSUES =
            listOf(
                "AddedClass",
                "AddedField",
                "AddedInterface",
                "AddedMethod",
                "AddedPackage",
                "ChangedDeprecated",
                "RemovedClass",
                "RemovedDeprecatedClass",
                "RemovedField",
            )
        private val DEFAULT_HIDDEN_ISSUES_STRING = DEFAULT_HIDDEN_ISSUES.joinToString(",")

        private fun joinIssues(issues: Array<out String>): String = issues.joinToString(",")

        fun hide(vararg issues: String): List<String> {
            return listOf(ARG_HIDE, joinIssues(issues))
        }

        fun warning(vararg issues: String): List<String> {
            return listOf(ARG_WARNING, issues.joinToString(","))
        }

        /** Data for each api version to check. */
        private val data =
            listOf(
                ApiLevelCheck(
                    5,
                    """
                warning: Method android.view.Surface.lockCanvas added thrown exception java.lang.IllegalArgumentException [ChangedThrows]
                """,
                    hide(
                        DEFAULT_HIDDEN_ISSUES_STRING,
                        "AddedAbstractMethod",
                    ) + warning("ChangedThrows"),
                ),
                ApiLevelCheck(
                    6,
                    """
                warning: Method android.accounts.AbstractAccountAuthenticator.confirmCredentials added thrown exception android.accounts.NetworkErrorException [ChangedThrows]
                warning: Method android.accounts.AbstractAccountAuthenticator.updateCredentials added thrown exception android.accounts.NetworkErrorException [ChangedThrows]
                warning: Field android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL has changed value from 2008 to 2014 [ChangedValue]
                """,
                    hide(
                        DEFAULT_HIDDEN_ISSUES_STRING,
                    ) +
                        warning(
                            "ChangedThrows",
                            "ChangedValue",
                        ),
                ),
                ApiLevelCheck(
                    7,
                    """
                error: Removed field android.view.ViewGroup.FLAG_USE_CHILD_DRAWING_ORDER [RemovedField]
                """,
                    hide(
                        "AddedClass",
                        "AddedField",
                        "AddedInterface",
                        "AddedMethod",
                        "AddedPackage",
                        "ChangedDeprecated",
                    ),
                ),
                ApiLevelCheck(
                    8,
                    """
                error: Method android.content.ComponentName.clone has changed return type from java.lang.Object to android.content.ComponentName [ChangedType]
                warning: Method android.content.ComponentName.clone no longer throws exception java.lang.CloneNotSupportedException [ChangedThrows]
                warning: Method android.gesture.Gesture.clone no longer throws exception java.lang.CloneNotSupportedException [ChangedThrows]
                warning: Method android.gesture.GesturePoint.clone no longer throws exception java.lang.CloneNotSupportedException [ChangedThrows]
                warning: Method android.gesture.GestureStroke.clone no longer throws exception java.lang.CloneNotSupportedException [ChangedThrows]
                warning: Constructor android.net.SSLCertificateSocketFactory no longer throws exception java.security.KeyManagementException [ChangedThrows]
                warning: Constructor android.net.SSLCertificateSocketFactory no longer throws exception java.security.NoSuchAlgorithmException [ChangedThrows]
                warning: Constructor java.nio.charset.Charset no longer throws exception java.nio.charset.IllegalCharsetNameException [ChangedThrows]
                warning: Method java.nio.charset.Charset.forName no longer throws exception java.nio.charset.IllegalCharsetNameException [ChangedThrows]
                warning: Method java.nio.charset.Charset.forName no longer throws exception java.nio.charset.UnsupportedCharsetException [ChangedThrows]
                warning: Method java.nio.charset.Charset.isSupported no longer throws exception java.nio.charset.IllegalCharsetNameException [ChangedThrows]
                warning: Method java.util.regex.Matcher.appendReplacement no longer throws exception java.lang.IllegalStateException [ChangedThrows]
                warning: Method java.util.regex.Matcher.start no longer throws exception java.lang.IllegalStateException [ChangedThrows]
                warning: Method java.util.regex.Pattern.compile no longer throws exception java.util.regex.PatternSyntaxException [ChangedThrows]
                warning: Class javax.xml.XMLConstants added 'final' qualifier [AddedFinal]
                error: Removed constructor javax.xml.XMLConstants() [RemovedMethod]
                warning: Method javax.xml.parsers.DocumentBuilder.isXIncludeAware no longer throws exception java.lang.UnsupportedOperationException [ChangedThrows]
                warning: Method javax.xml.parsers.DocumentBuilderFactory.newInstance no longer throws exception javax.xml.parsers.FactoryConfigurationError [ChangedThrows]
                warning: Method javax.xml.parsers.SAXParser.isXIncludeAware no longer throws exception java.lang.UnsupportedOperationException [ChangedThrows]
                warning: Method javax.xml.parsers.SAXParserFactory.newInstance no longer throws exception javax.xml.parsers.FactoryConfigurationError [ChangedThrows]
                warning: Method org.w3c.dom.Element.getAttributeNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                warning: Method org.w3c.dom.Element.getAttributeNodeNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                warning: Method org.w3c.dom.Element.getElementsByTagNameNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                warning: Method org.w3c.dom.Element.hasAttributeNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                warning: Method org.w3c.dom.NamedNodeMap.getNamedItemNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                """,
                    hide(
                        DEFAULT_HIDDEN_ISSUES_STRING,
                        "AddedAbstractMethod",
                    ) +
                        warning(
                            "AddedFinal",
                            "ChangedThrows",
                        ),
                ),
                ApiLevelCheck(
                    18,
                    """
                error: Added method android.content.pm.PackageManager.getPackagesHoldingPermissions(String[],int) [AddedAbstractMethod]
                error: Removed field android.os.Process.BLUETOOTH_GID [RemovedField]
                error: Removed class android.renderscript.Program [RemovedClass]
                error: Removed class android.renderscript.ProgramStore [RemovedClass]
                error: Added method android.widget.MediaController.MediaPlayerControl.getAudioSessionId() [AddedAbstractMethod]
                """,
                    hide(
                        "AddedClass",
                        "AddedField",
                        "AddedFinal",
                        "AddedInterface",
                        "AddedMethod",
                        "AddedPackage",
                        "ChangedDeprecated",
                        "ChangedThrows",
                        "ChangedType",
                        "RemovedDeprecatedClass",
                        "RemovedMethod",
                    ),
                ),
                ApiLevelCheck(
                    19,
                    """
                error: Removed method android.os.Debug.MemoryInfo.getOtherLabel(int) [RemovedMethod]
                error: Removed method android.os.Debug.MemoryInfo.getOtherPrivateDirty(int) [RemovedMethod]
                error: Removed method android.os.Debug.MemoryInfo.getOtherPss(int) [RemovedMethod]
                error: Removed method android.os.Debug.MemoryInfo.getOtherSharedDirty(int) [RemovedMethod]
                warning: Field android.view.animation.Transformation.TYPE_ALPHA has changed value from nothing/not constant to 1 [ChangedValue]
                warning: Field android.view.animation.Transformation.TYPE_ALPHA has added 'final' qualifier [AddedFinal]
                warning: Field android.view.animation.Transformation.TYPE_BOTH has changed value from nothing/not constant to 3 [ChangedValue]
                warning: Field android.view.animation.Transformation.TYPE_BOTH has added 'final' qualifier [AddedFinal]
                warning: Field android.view.animation.Transformation.TYPE_IDENTITY has changed value from nothing/not constant to 0 [ChangedValue]
                warning: Field android.view.animation.Transformation.TYPE_IDENTITY has added 'final' qualifier [AddedFinal]
                warning: Field android.view.animation.Transformation.TYPE_MATRIX has changed value from nothing/not constant to 2 [ChangedValue]
                warning: Field android.view.animation.Transformation.TYPE_MATRIX has added 'final' qualifier [AddedFinal]
                warning: Method java.nio.CharBuffer.subSequence has changed return type from java.lang.CharSequence to java.nio.CharBuffer [ChangedType]
                """,
                    // The last warning above is not right; seems to be a PSI jar loading bug. It
                    // returns the wrong return type!
                    hide(
                        DEFAULT_HIDDEN_ISSUES_STRING,
                        "AddedAbstractMethod",
                    ) +
                        warning(
                            "AddedFinal",
                            "ChangedType",
                            "ChangedValue",
                        ),
                ),
                ApiLevelCheck(
                    20,
                    """
                error: Removed method android.util.TypedValue.complexToDimensionNoisy(int,android.util.DisplayMetrics) [RemovedMethod]
                warning: Method org.json.JSONObject.keys has changed return type from java.util.Iterator to java.util.Iterator<java.lang.String> [ChangedType]
                warning: Field org.xmlpull.v1.XmlPullParserFactory.features has changed type from java.util.HashMap to java.util.HashMap<java.lang.String,java.lang.Boolean> [ChangedType]
                """,
                    hide(
                        DEFAULT_HIDDEN_ISSUES_STRING,
                        "AddedAbstractMethod",
                    ) +
                        warning(
                            "ChangedType",
                        ),
                ),
                ApiLevelCheck(
                    26,
                    """
                warning: Field android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE has changed value from 130 to 230 [ChangedValue]
                warning: Field android.content.pm.PermissionInfo.PROTECTION_MASK_FLAGS has changed value from 4080 to 65520 [ChangedValue]
                """,
                    hide(
                        "AddedAbstractMethod",
                        "AddedClass",
                        "AddedField",
                        "AddedFinal",
                        "AddedInterface",
                        "AddedMethod",
                        "AddedPackage",
                        "ChangedAbstract",
                        "ChangedDeprecated",
                        "ChangedThrows",
                        "ChangedType",
                        "RemovedClass",
                        "RemovedDeprecatedClass",
                        "RemovedMethod",
                    ) +
                        warning(
                            "ChangedValue",
                        ),
                ),
                ApiLevelCheck(
                    27,
                    "",
                    hide(
                        "AddedClass",
                        "AddedField",
                        "AddedFinal",
                        "AddedInterface",
                        "AddedMethod",
                        "AddedPackage",
                        "ChangedAbstract",
                        "ChangedDeprecated",
                        "ChangedThrows",
                        "RemovedMethod",
                    ),
                ),
            )

        @JvmStatic
        protected fun shardTestParameters(apiLevelRange: IntRange) =
            data.filter { it.apiLevel in apiLevelRange }
    }

    @Test
    fun `Test All Android API levels`() {
        // Checks API across Android SDK versions and makes sure the results are
        // intentional (to help shake out bugs in the API compatibility checker)

        // Temporary let block to keep the same indent as before when this was looping over the data
        // itself.
        let {
            if (apiLevelCheck.disabled) {
                throw AssumptionViolatedException("Test disabled")
            }

            val apiLevel = apiLevelCheck.apiLevel
            val expectedIssues = apiLevelCheck.expectedIssues
            val expectedFail =
                if (expectedIssues.contains("error: ")) "Aborting: Found compatibility problems"
                else ""
            val extraArgs = apiLevelCheck.extraArgs.toTypedArray()

            val current = getAndroidJar(apiLevel)
            val previous = getAndroidJar(apiLevel - 1)
            val previousApi = previous.path

            // PSI based check

            check(
                extraArguments = extraArgs,
                expectedIssues = expectedIssues,
                expectedFail = expectedFail,
                checkCompatibilityApiReleased = previousApi,
                apiJar = current,
                skipEmitPackages = emptyList(),
            )

            // Signature based check
            if (apiLevel >= 21) {
                // Check signature file checks. We have .txt files for API level 14 and up, but
                // there are a
                // BUNCH of problems in older signature files that make the comparisons not work --
                // missing type variables in class declarations, missing generics in method
                // signatures, etc.
                val signatureFile =
                    File("../../../prebuilts/sdk/${apiLevel - 1}/public/api/android.txt")
                assertTrue(
                    "Couldn't find $signatureFile: Check that pwd (${File("").absolutePath}) for test is correct",
                    signatureFile.isFile
                )

                check(
                    extraArguments = extraArgs,
                    expectedIssues = expectedIssues,
                    expectedFail = expectedFail,
                    checkCompatibilityApiReleased = signatureFile.path,
                    apiJar = current,
                    skipEmitPackages = emptyList(),
                )
            }
        }
    }
}
