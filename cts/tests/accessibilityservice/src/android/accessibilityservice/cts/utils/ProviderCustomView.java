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

package android.accessibilityservice.cts.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import androidx.annotation.Nullable;

/**
 * Class that returns a non-null AccessibilityNodeProvider. Used in AccessibilityEndToEndTest.
 */
public class ProviderCustomView extends View  {
    private boolean mReturnProvider;

    public ProviderCustomView(Context context) {
        super(context);
    }

    public ProviderCustomView(Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ProviderCustomView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ProviderCustomView(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setReturnProvider(boolean returnProvider) {
        this.mReturnProvider = returnProvider;
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        if (mReturnProvider) {
            return new AccessibilityNodeProvider() {
                @Nullable
                @Override
                public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
                    AccessibilityNodeInfo hostNode =
                            new AccessibilityNodeInfo(ProviderCustomView.this);
                    ProviderCustomView.this.onInitializeAccessibilityNodeInfo(hostNode);
                    return hostNode;
                }
            };
        }
        return null;
    }
}
