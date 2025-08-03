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
@file:JvmName("UriFilters")

package com.android.intentresolver.util

import android.content.ContentProvider.getUserIdFromUri
import android.content.ContentResolver.SCHEME_CONTENT
import android.graphics.drawable.Icon
import android.graphics.drawable.Icon.TYPE_URI
import android.graphics.drawable.Icon.TYPE_URI_ADAPTIVE_BITMAP
import android.net.Uri
import android.os.UserHandle
import android.service.chooser.ChooserAction

/**
 * Checks if the [Uri] is a `content://` uri which references the current user (from process uid).
 *
 * MediaStore interprets the user field of a content:// URI as a UserId and applies it if the caller
 * holds INTERACT_ACROSS_USERS permission. (Example: `content://10@media/images/1234`)
 *
 * No URI content should be loaded unless it passes this check since the caller would not have
 * permission to read it.
 *
 * @return false if this is a content:// [Uri] which references another user
 */
val Uri?.ownedByCurrentUser: Boolean
    @JvmName("isOwnedByCurrentUser")
    get() =
        this?.let {
            when (getUserIdFromUri(this, UserHandle.USER_CURRENT)) {
                UserHandle.USER_CURRENT,
                UserHandle.myUserId() -> true
                else -> false
            }
        } == true

/** Does the [Uri] reference a content provider ('content://')? */
internal val Uri.contentScheme: Boolean
    get() = scheme == SCHEME_CONTENT

/**
 * Checks if the Icon of a [ChooserAction] backed by content:// [Uri] is safe for display.
 *
 * @param action the chooser action
 * @see [Uri.ownedByCurrentUser]
 */
fun hasValidIcon(action: ChooserAction) = hasValidIcon(action.icon)

/**
 * Checks if the Icon backed by content:// [Uri] is safe for display.
 *
 * @see [Uri.ownedByCurrentUser]
 */
fun hasValidIcon(icon: Icon) =
    with(icon) {
        when (type) {
            TYPE_URI,
            TYPE_URI_ADAPTIVE_BITMAP -> !uri.contentScheme || uri.ownedByCurrentUser
            else -> true
        }
    }
