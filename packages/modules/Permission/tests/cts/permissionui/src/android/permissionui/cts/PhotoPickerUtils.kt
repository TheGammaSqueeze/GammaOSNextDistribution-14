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

package android.permissionui.cts

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.FileUtils
import android.provider.MediaStore
import android.provider.cts.ProviderTestUtils
import android.provider.cts.media.MediaStoreUtils
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import java.io.IOException

object PhotoPickerUtils {
    private const val DISPLAY_NAME_PREFIX = "ctsPermissionPhotoPicker"
    private const val VIDEO_ICON_ID = ":id/icon_video"
    private const val IMAGE_CHECK_BOX_ID = ":id/icon_check"
    private const val ALLOW_ID = ":id/button_add"
    private var mediaProviderPkgName: String? = null

    fun getImageOrVideoResId(context: Context): String {
        return "${getMediaProviderPkgName(context)!!}$IMAGE_CHECK_BOX_ID"
    }

    fun getVideoResId(context: Context): String {
        return "${getMediaProviderPkgName(context)!!}$VIDEO_ICON_ID"
    }

    fun getAllowId(context: Context): String {
        return "${getMediaProviderPkgName(context)!!}$ALLOW_ID"
    }

    fun getMediaProviderPkgName(context: Context): String? {
        return mediaProviderPkgName
            ?: callWithShellPermissionIdentity {
                val pkgs = context.packageManager.getInstalledPackages(PackageManager.GET_PROVIDERS)
                for (pkg in pkgs) {
                    pkg.providers?.let { providerInfos ->
                        for (providerInfo in providerInfos) {
                            if (providerInfo.authority == "media") {
                                mediaProviderPkgName = pkg.packageName
                                return@callWithShellPermissionIdentity mediaProviderPkgName
                            }
                        }
                    }
                }
                null
            }
    }

    @Throws(java.lang.Exception::class)
    fun createImage(context: Context): Uri {
        return getPermissionAndStageMedia(
                context,
                R.raw.lg_g4_iso_800_jpg,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "image/jpeg"
            )
            .first
    }

    @Throws(java.lang.Exception::class)
    fun createVideo(context: Context): Uri {
        return getPermissionAndStageMedia(
                context,
                R.raw.test_video,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "video/mp4"
            )
            .first
    }

    @Throws(Exception::class)
    fun deleteMedia(context: Context, uri: Uri?) {
        if (uri == null) {
            return
        }
        try {
            ProviderTestUtils.setOwner(uri, context.packageName)
            context.contentResolver.delete(uri, Bundle.EMPTY)
        } catch (ignored: Exception) {}
    }

    @Throws(java.lang.Exception::class)
    private fun getPermissionAndStageMedia(
        context: Context,
        resId: Int,
        collectionUri: Uri,
        mimeType: String,
    ): Pair<Uri, String> {
        return callWithShellPermissionIdentity {
            stageMedia(context, resId, collectionUri, mimeType)
        }
    }
    @Throws(IOException::class)
    private fun stageMedia(
        context: Context,
        resId: Int,
        collectionUri: Uri,
        mimeType: String,
    ): Pair<Uri, String> {
        val displayName = DISPLAY_NAME_PREFIX + System.nanoTime()
        val params = MediaStoreUtils.PendingParams(collectionUri, displayName, mimeType)
        val pendingUri = MediaStoreUtils.createPending(context, params)
        MediaStoreUtils.openPending(context, pendingUri).use { session ->
            context.resources.openRawResource(resId).use { source ->
                session.openOutputStream().use { target -> FileUtils.copy(source, target) }
            }
            return session.publish() to displayName
        }
    }
}
