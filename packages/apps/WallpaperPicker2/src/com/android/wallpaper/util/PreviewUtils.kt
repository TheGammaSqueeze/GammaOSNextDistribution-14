/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.wallpaper.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import java.util.concurrent.Executors

/** Util class for wallpaper preview. */
class PreviewUtils(
    private val context: Context,
    authorityMetadataKey: String? = null,
    authority: String? = null,
) {
    /** Callback for a call to the provider to render preview */
    interface WorkspacePreviewCallback {
        /** Called with the result from the provider. */
        fun onPreviewRendered(resultBundle: Bundle?)
    }

    private var providerInfo: ProviderInfo?

    constructor(
        context: Context,
        authorityMetadataKey: String,
    ) : this(
        context = context.applicationContext,
        authorityMetadataKey = authorityMetadataKey,
        authority = null,
    )

    init {
        val providerAuthority =
            authority ?: homeAuthority(context, checkNotNull(authorityMetadataKey)) ?: ""

        providerInfo =
            if (!TextUtils.isEmpty(providerAuthority)) {
                context.packageManager.resolveContentProvider(
                    providerAuthority,
                    0,
                )
            } else {
                null
            }

        providerInfo?.let {
            if (!TextUtils.isEmpty(it.readPermission)) {
                if (
                    context.checkSelfPermission(it.readPermission) !=
                        PackageManager.PERMISSION_GRANTED
                ) {
                    providerInfo = null
                }
            }
        }
    }

    /**
     * Render preview under the current grid option.
     *
     * @param bundle request options to pass on the call.
     * @param callback to receive the results, it will be called on the main thread.
     */
    fun renderPreview(bundle: Bundle?, callback: WorkspacePreviewCallback) {
        EXECUTOR_SERVICE.submit {
            val result =
                context.contentResolver.call(
                    getUri(PREVIEW),
                    METHOD_GET_PREVIEW,
                    null,
                    bundle,
                )
            Handler(Looper.getMainLooper()).post { callback.onPreviewRendered(result) }
        }
    }

    /** Easy way to generate a Uri with the provider info from this class. */
    fun getUri(path: String?): Uri {
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(checkNotNull(providerInfo).authority)
            .appendPath(path)
            .build()
    }

    /** Return whether preview is supported. */
    fun supportsPreview(): Boolean {
        return providerInfo != null
    }

    companion object {
        private const val PREVIEW = "preview"
        private const val METHOD_GET_PREVIEW = "get_preview"
        private val EXECUTOR_SERVICE = Executors.newSingleThreadExecutor()

        private fun homeAuthority(context: Context, authorityMetadataKey: String): String? {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val info =
                context.packageManager.resolveActivity(
                    homeIntent,
                    PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_META_DATA,
                )

            return info?.activityInfo?.metaData?.getString(authorityMetadataKey)
        }
    }
}
