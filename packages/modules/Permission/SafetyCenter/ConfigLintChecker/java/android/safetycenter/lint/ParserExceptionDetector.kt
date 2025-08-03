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

package android.safetycenter.lint

import android.content.res.Resources
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_STRING
import com.android.modules.utils.build.SdkLevel
import com.android.resources.ResourceFolderType
import com.android.safetycenter.config.ParseException
import com.android.safetycenter.config.SafetyCenterConfigParser
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.OtherFileScanner
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import java.util.EnumSet
import kotlin.math.min
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Lint check for detecting invalid Safety Center configs */
class ParserExceptionDetector : Detector(), OtherFileScanner, XmlScanner {

    companion object {
        val ISSUE =
            Issue.create(
                id = "InvalidSafetyCenterConfig",
                briefDescription = "The Safety Center config parser detected an error",
                explanation =
                    """The Safety Center config must follow all constraints defined in \
                safety_center_config.xsd. Check the error message to find out the specific \
                constraint not met by the current config.""",
                category = Category.CORRECTNESS,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        ParserExceptionDetector::class.java,
                        EnumSet.of(Scope.RESOURCE_FILE, Scope.OTHER)
                    ),
                androidSpecific = true
            )

        const val STRING_MAP_BUILD_PHASE = 1
        const val CONFIG_PARSE_PHASE = 2
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.RAW || folderType == ResourceFolderType.VALUES
    }

    override fun afterCheckEachProject(context: Context) {
        context.driver.requestRepeat(this, Scope.OTHER_SCOPE)
    }

    /** Implements XmlScanner and builds a map of string resources in the first phase */
    private val mNameToIndex: MutableMap<String, Int> = mutableMapOf()
    private val mIndexToValue: MutableMap<Int, String> = mutableMapOf()
    private val mIndexToMinSdk: MutableMap<Int, Int> = mutableMapOf()
    private var mIndex = 1000

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_STRING)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (
            context.driver.phase != STRING_MAP_BUILD_PHASE ||
                context.resourceFolderType != ResourceFolderType.VALUES ||
                !FileSdk.belongsToABasicConfiguration(context.file)
        ) {
            return
        }
        val minSdk = FileSdk.getSdkQualifier(context.file)
        val name = element.getAttribute(ATTR_NAME)
        val index = mNameToIndex[name]
        if (index != null) {
            mIndexToMinSdk[index] = min(mIndexToMinSdk[index]!!, minSdk)
            return
        }
        var value = ""
        for (childIndex in 0 until element.childNodes.length) {
            val child = element.childNodes.item(childIndex)
            if (child.nodeType == Node.TEXT_NODE) {
                value = child.nodeValue
                break
            }
        }
        mNameToIndex[name] = mIndex
        mIndexToValue[mIndex] = value
        mIndexToMinSdk[mIndex] = minSdk
        mIndex++
    }

    /** Implements OtherFileScanner and parses the XML config in the second phase */
    override fun run(context: Context) {
        if (
            context.driver.phase != CONFIG_PARSE_PHASE ||
                context.file.name != "safety_center_config.xml"
        ) {
            return
        }
        val minSdk = FileSdk.getSdkQualifier(context.file)
        val maxSdk = maxOf(minSdk, FileSdk.getMaxSdkVersion())
        // Test the parser at the SDK level for which the config was designed.
        // Then test parsers at higher SDK levels for backward compatibility.
        // This is slightly inefficient if a parser at a higher SDK level has no behavioral changes
        // compared to one at a lower SDK level, but doing an exhaustive search is safer.
        for (sdk in minSdk..maxSdk) {
            synchronized(SdkLevel::class.java) {
                SdkLevel.setSdkInt(sdk)
                try {
                    SafetyCenterConfigParser.parseXmlResource(
                        context.file.inputStream(),
                        // Note: using a map of the string resources present in the APK under
                        // analysis is necessary in order to get the value of string resources that
                        // are resolved and validated at parse time. The drawback of this is that
                        // the linter cannot be used on overlay packages that refer to resources in
                        // the target package or on packages that refer to Android global resources.
                        // However, we cannot use a custom linter with the default soong overlay
                        // build rule regardless.
                        Resources(
                            context.project.`package`,
                            mNameToIndex.filterValues { sdk >= mIndexToMinSdk[it]!! },
                            mIndexToValue.filterKeys { sdk >= mIndexToMinSdk[it]!! }
                        )
                    )
                } catch (e: ParseException) {
                    context.report(
                        ISSUE,
                        Location.create(context.file),
                        "Parser exception at sdk=$sdk: \"${e.message}\", cause: " +
                            "\"${e.cause?.message}\""
                    )
                }
            }
        }
    }
}
