/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.intentresolver.icon

import android.content.ContentResolver
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import java.io.File
import java.io.FileInputStream

sealed interface ComposeIcon

data class BitmapIcon(val bitmap: Bitmap) : ComposeIcon

data class ResourceIcon(val resId: Int, val res: Resources) : ComposeIcon

@JvmInline value class AdaptiveIcon(val wrapped: ComposeIcon) : ComposeIcon

fun Icon.toComposeIcon(pm: PackageManager, resolver: ContentResolver): ComposeIcon? {
    return when (type) {
        Icon.TYPE_BITMAP -> BitmapIcon(bitmap)
        Icon.TYPE_RESOURCE -> pm.resourcesForPackage(resPackage)?.let { ResourceIcon(resId, it) }
        Icon.TYPE_DATA ->
            BitmapIcon(BitmapFactory.decodeByteArray(dataBytes, dataOffset, dataLength))
        Icon.TYPE_URI -> uriIcon(resolver)
        Icon.TYPE_ADAPTIVE_BITMAP -> AdaptiveIcon(BitmapIcon(bitmap))
        Icon.TYPE_URI_ADAPTIVE_BITMAP -> uriIcon(resolver)?.let { AdaptiveIcon(it) }
        else -> error("unexpected icon type: $type")
    }
}

fun Icon.toComposeIcon(resources: Resources?, resolver: ContentResolver): ComposeIcon? {
    return when (type) {
        Icon.TYPE_BITMAP -> BitmapIcon(bitmap)
        Icon.TYPE_RESOURCE -> resources?.let { ResourceIcon(resId, resources) }
        Icon.TYPE_DATA ->
            BitmapIcon(BitmapFactory.decodeByteArray(dataBytes, dataOffset, dataLength))
        Icon.TYPE_URI -> uriIcon(resolver)
        Icon.TYPE_ADAPTIVE_BITMAP -> AdaptiveIcon(BitmapIcon(bitmap))
        Icon.TYPE_URI_ADAPTIVE_BITMAP -> uriIcon(resolver)?.let { AdaptiveIcon(it) }
        else -> error("unexpected icon type: $type")
    }
}

// TODO: this is probably constant and doesn't need to be re-queried for each icon
fun PackageManager.resourcesForPackage(pkgName: String): Resources? {
    return if (pkgName == "android") {
        Resources.getSystem()
    } else {
        runCatching {
                this@resourcesForPackage.getApplicationInfo(
                    pkgName,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES or
                        PackageManager.GET_SHARED_LIBRARY_FILES
                )
            }
            .getOrNull()
            ?.let { ai -> getResourcesForApplication(ai) }
    }
}

private fun Icon.uriIcon(resolver: ContentResolver): BitmapIcon? {
    return runCatching {
            when (uri.scheme) {
                ContentResolver.SCHEME_CONTENT,
                ContentResolver.SCHEME_FILE -> resolver.openInputStream(uri)
                else -> FileInputStream(File(uriString))
            }
        }
        .getOrNull()
        ?.let { inStream -> BitmapIcon(BitmapFactory.decodeStream(inStream)) }
}
