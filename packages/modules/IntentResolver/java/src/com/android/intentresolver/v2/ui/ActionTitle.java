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
package com.android.intentresolver.v2.ui;

import android.content.Intent;
import android.provider.MediaStore;

import androidx.annotation.StringRes;

import com.android.intentresolver.R;

/**
 * Provides a set of related resources for different use cases.
 */
public enum ActionTitle {
    VIEW(Intent.ACTION_VIEW,
            R.string.whichViewApplication,
            R.string.whichViewApplicationNamed,
            R.string.whichViewApplicationLabel),
    EDIT(Intent.ACTION_EDIT,
            R.string.whichEditApplication,
            R.string.whichEditApplicationNamed,
            R.string.whichEditApplicationLabel),
    SEND(Intent.ACTION_SEND,
            R.string.whichSendApplication,
            R.string.whichSendApplicationNamed,
            R.string.whichSendApplicationLabel),
    SENDTO(Intent.ACTION_SENDTO,
            R.string.whichSendToApplication,
            R.string.whichSendToApplicationNamed,
            R.string.whichSendToApplicationLabel),
    SEND_MULTIPLE(Intent.ACTION_SEND_MULTIPLE,
            R.string.whichSendApplication,
            R.string.whichSendApplicationNamed,
            R.string.whichSendApplicationLabel),
    CAPTURE_IMAGE(MediaStore.ACTION_IMAGE_CAPTURE,
            R.string.whichImageCaptureApplication,
            R.string.whichImageCaptureApplicationNamed,
            R.string.whichImageCaptureApplicationLabel),
    DEFAULT(null,
            R.string.whichApplication,
            R.string.whichApplicationNamed,
            R.string.whichApplicationLabel),
    HOME(Intent.ACTION_MAIN,
            R.string.whichHomeApplication,
            R.string.whichHomeApplicationNamed,
            R.string.whichHomeApplicationLabel);

    // titles for layout that deals with http(s) intents
    public static final int BROWSABLE_TITLE_RES = R.string.whichOpenLinksWith;
    public static final int BROWSABLE_HOST_TITLE_RES = R.string.whichOpenHostLinksWith;
    public static final int BROWSABLE_HOST_APP_TITLE_RES = R.string.whichOpenHostLinksWithApp;
    public static final int BROWSABLE_APP_TITLE_RES = R.string.whichOpenLinksWithApp;

    public final String action;
    public final int titleRes;
    public final int namedTitleRes;
    public final @StringRes int labelRes;

    ActionTitle(String action, int titleRes, int namedTitleRes, @StringRes int labelRes) {
        this.action = action;
        this.titleRes = titleRes;
        this.namedTitleRes = namedTitleRes;
        this.labelRes = labelRes;
    }

    public static ActionTitle forAction(String action) {
        for (ActionTitle title : values()) {
            if (title != HOME && action != null && action.equals(title.action)) {
                return title;
            }
        }
        return DEFAULT;
    }
}
