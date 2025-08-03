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

package com.android.intentresolver.v2.ui.model

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.UserHandle
import com.android.intentresolver.v2.shared.model.Profile
import com.android.intentresolver.v2.ext.isHomeIntent

/** All of the things that are consumed from an incoming Intent Resolution request (+Extras). */
data class ResolverRequest(
    /** The intent to be resolved to a target. */
    val intent: Intent,

    /**
     * Supplied by the system to indicate which profile should be selected by default. This is
     * required since ResolverActivity may be launched as either the originating OR target user when
     * resolving a cross profile intent.
     *
     * Valid values are: [PERSONAL][Profile.Type.PERSONAL] and [WORK][Profile.Type.WORK] and null
     * when the intent is not a forwarded cross-profile intent.
     */
    val selectedProfile: Profile.Type?,

    /**
     * When handing a cross profile forwarded intent, this is the user which started the original
     * intent. This is required to allow ResolverActivity to be launched as the target user under
     * some conditions.
     */
    val callingUser: UserHandle?,

    /**
     * Indicates if resolving actions for a connected device which has audio capture capability
     * (e.g. is a USB Microphone).
     *
     * When used to handle a connected device, ResolverActivity uses this signal to present a
     * warning when a resolved application does not hold the RECORD_AUDIO permission. (If selected
     * the app would be able to capture audio directly via the device, bypassing audio API
     * permissions.)
     */
    val isAudioCaptureDevice: Boolean = false,

    /** A list of a resolved activity targets. This list overrides normal intent resolution. */
    val resolutionList: List<ResolveInfo>? = null,

    /** A customized title for the resolver interface. */
    val title: String? = null,
) {
    val isResolvingHome = intent.isHomeIntent()

    /** For compatibility with existing code shared between chooser/resolver. */
    val payloadIntents: List<Intent> = listOf(intent)
}
