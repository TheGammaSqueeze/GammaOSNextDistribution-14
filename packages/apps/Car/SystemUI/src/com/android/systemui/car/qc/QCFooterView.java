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

package com.android.systemui.car.qc;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.systemui.R;
import com.android.systemui.car.systembar.element.CarSystemBarElement;
import com.android.systemui.car.systembar.element.CarSystemBarElementFlags;
import com.android.systemui.car.systembar.element.CarSystemBarElementResolver;

import java.net.URISyntaxException;

/**
 * Footer layout which contains one or multiple views for quick control panels.
 *
 * Allows for an intent action to be specified via the
 * {@link R.styleable.QCFooterView_intent} attribute and for enabled state to be set
 * according to driving mode via the {@link R.styleable.QCFooterView_disableWhileDriving}
 * attribute.
 */
public class QCFooterView extends ConstraintLayout implements CarSystemBarElement {
    private static final String TAG = QCFooterView.class.getSimpleName();

    private final Class<?> mElementControllerClassAttr;
    private final int mSystemBarDisableFlags;
    private final int mSystemBarDisable2Flags;
    private final boolean mDisableForLockTaskModeLocked;
    private final Intent mIntent;
    private final boolean mDisableWhileDriving;

    public QCFooterView(Context context) {
        this(context, null);
    }

    public QCFooterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QCFooterView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public QCFooterView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mElementControllerClassAttr =
                CarSystemBarElementResolver.getElementControllerClassFromAttributes(context, attrs);
        mSystemBarDisableFlags =
                CarSystemBarElementFlags.getStatusBarManagerDisableFlagsFromAttributes(context,
                        attrs);
        mSystemBarDisable2Flags =
                CarSystemBarElementFlags.getStatusBarManagerDisable2FlagsFromAttributes(context,
                        attrs);
        mDisableForLockTaskModeLocked =
                CarSystemBarElementFlags.getDisableForLockTaskModeLockedFromAttributes(context,
                        attrs);

        if (attrs == null) {
            mIntent = null;
            mDisableWhileDriving = false;
            return;
        }

        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                R.styleable.QCFooterView);
        String intentString = typedArray.getString(R.styleable.QCFooterView_intent);
        if (intentString != null) {
            try {
                mIntent = Intent.parseUri(intentString, Intent.URI_INTENT_SCHEME);
            } catch (URISyntaxException e) {
                throw new RuntimeException("Failed to attach intent", e);
            }
        } else {
            mIntent = null;
        }
        mDisableWhileDriving = typedArray.getBoolean(
                R.styleable.QCFooterView_disableWhileDriving, /* defValue= */ false);
        typedArray.recycle();
    }

    @Nullable
    public Intent getOnClickIntent() {
        return mIntent;
    }

    public boolean isDisableWhileDriving() {
        return mDisableWhileDriving;
    }

    @Override
    public Class<?> getElementControllerClass() {
        if (mElementControllerClassAttr != null) {
            return mElementControllerClassAttr;
        }
        return QCFooterViewController.class;
    }

    @Override
    public int getSystemBarDisableFlags() {
        return mSystemBarDisableFlags;
    }

    @Override
    public int getSystemBarDisable2Flags() {
        return mSystemBarDisable2Flags;
    }

    @Override
    public boolean disableForLockTaskModeLocked() {
        return mDisableForLockTaskModeLocked;
    }
}
