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

package com.android.systemui.car.qc;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.android.car.qc.view.QCView;
import com.android.systemui.R;
import com.android.systemui.car.systembar.element.CarSystemBarElement;
import com.android.systemui.car.systembar.element.CarSystemBarElementFlags;
import com.android.systemui.car.systembar.element.CarSystemBarElementResolver;

/**
 * Quick Control View Element for CarSystemUI.
 *
 * This extended class allows for specifying a local or remote quick controls provider via xml
 * attributes. This is then retrieved by a {@link SystemUIQCViewController} to be bound and
 * controlled.
 *
 * @attr ref R.styleable#SystemUIQCView_remoteQCProvider
 * @attr ref R.styleable#SystemUIQCView_localQCProvider
 */
public class SystemUIQCView extends QCView implements CarSystemBarElement {
    private Class<?> mElementControllerClassAttr;
    private int mSystemBarDisableFlags;
    private int mSystemBarDisable2Flags;
    private boolean mDisableForLockTaskModeLocked;
    private String mRemoteUri;
    private String mLocalClass;

    public SystemUIQCView(Context context) {
        super(context);
        init(context, /* attrs= */ null);
    }

    public SystemUIQCView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public SystemUIQCView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public SystemUIQCView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
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
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SystemUIQCView);
        mRemoteUri = a.getString(R.styleable.SystemUIQCView_remoteQCProvider);
        mLocalClass = a.getString(R.styleable.SystemUIQCView_localQCProvider);
        a.recycle();
    }

    @Nullable
    public String getRemoteUriString() {
        return mRemoteUri;
    }

    @Nullable
    public String getLocalClassString() {
        return mLocalClass;
    }

    @Override
    public Class<?> getElementControllerClass() {
        if (mElementControllerClassAttr != null) {
            return mElementControllerClassAttr;
        }
        return SystemUIQCViewController.class;
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
