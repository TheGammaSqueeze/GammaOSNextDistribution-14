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

package android.adservices.lint

import android.adservices.parser.AndroidManifestParser
import com.android.tools.lint.detector.api.*

class BackCompatAndroidProcessDetector: Detector(), OtherFileScanner {

    companion object {
        val ISSUE = Issue.create(
                id = "MissingAndroidProcessInAdExtServicesManifest",
                briefDescription = "All components in the AdExtServicesManifest.xml file need to have an android:process=\".adservices\" attribute",
                explanation = """All components (receivers, activities, services, providers) in the AdExtServicesManifest.xml file \
                    need to have an android:process=".adservices" attribute in the XML.""",
                category = Category.CORRECTNESS,
                severity = Severity.ERROR,
                implementation = Implementation(
                        BackCompatAndroidProcessDetector::class.java,
                        Scope.OTHER_SCOPE
                ),
                androidSpecific = true
        )
    }

    override fun run(context: Context) {
        // NOTE: The linter will copy the manifest to a file called AndroidManifest.xml
        if (context.file.name != "AndroidManifest.xml") {
            return
        }

        val stream = context.file.inputStream()
        var issues : List<Int>
        stream.use { s -> issues = AndroidManifestParser.findComponentsMissingAdservicesProcess(s) }

        for (issue: Int in issues) {
            context.report(
                    ISSUE,
                    Location.create(context.file),
                    "AdExtServicesManifest.xml is missing an `android:process=\".adservices\" at line " + issue)
        }
    }
}