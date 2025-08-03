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

import android.os.Build.VERSION_CODES.TIRAMISU
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.OtherFileScanner
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import java.io.IOException
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import org.xml.sax.SAXException

/** Lint check for detecting invalid Safety Center configs */
class ConfigSchemaDetector : Detector(), OtherFileScanner {

    companion object {
        val ISSUE =
            Issue.create(
                id = "InvalidSafetyCenterConfigSchema",
                briefDescription = "The Safety Center config does not meet the schema requirements",
                explanation =
                    """The Safety Center config must follow all constraints defined in \
                safety_center_config.xsd. Either the config is invalid or the schema is not up to
                date.""",
                category = Category.CORRECTNESS,
                severity = Severity.ERROR,
                implementation =
                    Implementation(ConfigSchemaDetector::class.java, Scope.OTHER_SCOPE),
                androidSpecific = true
            )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.RAW
    }

    override fun run(context: Context) {
        if (context.file.name != "safety_center_config.xml") {
            return
        }
        val fileSdk = FileSdk.getSdkQualifier(context.file)
        // A config must comply with the schema at the highest SDK level that is lower or equal to
        // the SDK level of the config itself.
        var found = false
        for (sdk in fileSdk downTo TIRAMISU) {
            if (testSchema(sdk, context)) {
                found = true
                break
            }
        }
        if (!found) {
            context.report(
                ISSUE,
                Location.create(context.file),
                "No schema found for SDK level: $fileSdk, was it deleted?"
            )
        }
        // Test new schemas for backward compatibility.
        for (sdk in fileSdk + 1..FileSdk.getMaxSdkVersion()) {
            testSchema(sdk, context)
        }
    }

    private fun testSchema(sdk: Int, context: Context): Boolean {
        val xsdInputStream = FileSdk.getSchemaAsStream(sdk) ?: return false
        val xsd = StreamSource(xsdInputStream)
        val xml = StreamSource(context.file.inputStream())
        val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        try {
            val schema = schemaFactory.newSchema(xsd)
            val validator = schema.newValidator()
            validator.validate(xml)
        } catch (e: SAXException) {
            context.report(
                ISSUE,
                Location.create(context.file),
                "SAXException exception at sdk=$sdk: \"${e.message}\""
            )
        } catch (e: IOException) {
            context.report(
                ISSUE,
                Location.create(context.file),
                "IOException exception at sdk=$sdk: \"${e.message}\""
            )
        }
        return true
    }
}
