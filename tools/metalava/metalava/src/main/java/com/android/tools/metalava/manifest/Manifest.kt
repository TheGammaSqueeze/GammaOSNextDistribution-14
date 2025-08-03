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

package com.android.tools.metalava.manifest

import com.android.SdkConstants
import com.android.tools.metalava.model.MinSdkVersion
import com.android.tools.metalava.model.SetMinSdkVersion
import com.android.tools.metalava.model.UnsetMinSdkVersion
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.xml.parseDocument
import com.android.utils.XmlUtils
import java.io.File

/**
 * An empty manifest. This is safe to share as while it is not strictly immutable it only mutates
 * the object when lazily initializing [Manifest.info].
 */
val emptyManifest: Manifest by lazy { Manifest(null, null) }

private data class ManifestInfo(
    val permissions: Map<String, String>,
    val minSdkVersion: MinSdkVersion
)

private val defaultInfo = ManifestInfo(emptyMap(), UnsetMinSdkVersion)

/** Provides access to information from an `AndroidManifest.xml` file. */
class Manifest(private val manifest: File?, private val reporter: Reporter?) {

    private val info: ManifestInfo by lazy { readManifestInfo() }

    private fun readManifestInfo(): ManifestInfo {
        if (manifest == null) {
            return defaultInfo
        }

        return try {
            val doc = parseDocument(manifest.readText(Charsets.UTF_8), true)

            // Extract permissions.
            val map = HashMap<String, String>(600)
            var current =
                XmlUtils.getFirstSubTagByName(doc.documentElement, SdkConstants.TAG_PERMISSION)
            while (current != null) {
                val permissionName =
                    current.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                val protectionLevel =
                    current.getAttributeNS(SdkConstants.ANDROID_URI, "protectionLevel")
                map[permissionName] = protectionLevel
                current = XmlUtils.getNextTagByName(current, SdkConstants.TAG_PERMISSION)
            }

            // Extract minSdkVersion.
            val min: MinSdkVersion = run {
                val usesSdk =
                    XmlUtils.getFirstSubTagByName(doc.documentElement, SdkConstants.TAG_USES_SDK)
                if (usesSdk == null) {
                    UnsetMinSdkVersion
                } else {
                    val value =
                        usesSdk.getAttributeNS(
                            SdkConstants.ANDROID_URI,
                            SdkConstants.ATTR_MIN_SDK_VERSION
                        )
                    if (value.isEmpty()) UnsetMinSdkVersion else SetMinSdkVersion(value.toInt())
                }
            }

            ManifestInfo(map, min)
        } catch (error: Throwable) {
            reporter?.report(
                Issues.PARSE_ERROR,
                manifest,
                "Failed to parse $manifest: ${error.message}"
            )
                ?: throw error
            defaultInfo
        }
    }

    /** Check whether the manifest is empty or not. */
    fun isEmpty(): Boolean {
        return manifest == null
    }

    /**
     * Returns the permission level of the named permission, if specified in the manifest. This
     * method should only be called if the codebase has been configured with a manifest
     */
    fun getPermissionLevel(name: String): String? {
        assert(manifest != null) {
            "This method should only be called when a manifest has been configured on the codebase"
        }

        return info.permissions[name]
    }

    fun getMinSdkVersion(): MinSdkVersion {
        return info.minSdkVersion
    }

    override fun toString(): String {
        return manifest.toString()
    }
}
