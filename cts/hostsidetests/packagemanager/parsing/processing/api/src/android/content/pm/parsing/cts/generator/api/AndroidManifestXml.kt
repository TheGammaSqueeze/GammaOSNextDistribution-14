/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.content.pm.parsing.cts.generator.api

/**
 * Used to annotate a triple quoted string literal in order to automatically build an APK host side
 * so that tests don't need to configure individual test apps for each iteration.
 *
 * Note that this only supports actual string constants, with no inline format or variations. The
 * string in the source file must exactly match the string that will be passed to [ApkGenerator],
 * which will look up the appropriate APK by hash code.
 *
 * Sample usage:
 * ```kotlin
 * fun someTestMethod() {
 *     @AndroidManifestXml
 *     val xml = """
 *         <manifest xmlns:android="http://schemas.android.com/apk/res/android">
 *             <uses-sdk android:targetSdkVersion="30"/>
 *             <application/>
 *         </manifest>
 *     """
 *     val result = ApkGenerator.install(device, xml, tempFolder)
 *     ...
 * }
 * ```
 *
 * **Note:** The string *cannot* end with [String.trimIndent] as this will alter the hash code.
 */
@Target(
    AnnotationTarget.EXPRESSION,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.TYPE_PARAMETER,
    AnnotationTarget.VALUE_PARAMETER,
)
@Retention(AnnotationRetention.SOURCE)
annotation class AndroidManifestXml
