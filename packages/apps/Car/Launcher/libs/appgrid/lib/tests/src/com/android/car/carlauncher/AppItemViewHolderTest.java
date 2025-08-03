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

package com.android.car.carlauncher;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.carlauncher.recyclerview.AppItemViewHolder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class AppItemViewHolderTest {

    private static final String TEST_APP_PACKAGE_NAME = "com.android.car.test";
    private static final String TEST_TOS_DISABLED_APP_CLASS_NAME = "TosDisabledApp";

    @Mock private View mView;
    @Mock private Context mContext;
    @Mock private AppItemViewHolder.AppItemDragCallback mDragCallback;
    @Mock private AppGridPageSnapper.AppGridPageSnapCallback mSnapCallback;
    @Mock private ImageView mAppIcon;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testTosDisabledAppsOpacity() {
        setupMocksTosDisabledApps();

        AppItemViewHolder appItemViewHolder =
                new AppItemViewHolder(mView, mContext, mDragCallback, mSnapCallback);

        ComponentName componentName =
                new ComponentName(TEST_APP_PACKAGE_NAME, TEST_TOS_DISABLED_APP_CLASS_NAME);

        AppMetaData metaData =
                new AppMetaData(
                        null,
                        componentName,
                        null,
                        true,
                        false,
                        true,
                        null,
                        null);

        appItemViewHolder.bind(metaData, new AppItemViewHolder.BindInfo(false, null));

        verify(mAppIcon).setAlpha(0.46f);
    }

    private void setupMocksTosDisabledApps() {
        LinearLayout appItemView = mock(LinearLayout.class);
        ViewTreeObserver viewTreeObserver = mock(ViewTreeObserver.class);
        TextView appName = mock(TextView.class);

        when(mView.findViewById(R.id.app_item)).thenReturn(appItemView);
        when(appItemView.findViewById(R.id.app_icon)).thenReturn(mAppIcon);
        when(appItemView.findViewById(R.id.app_name)).thenReturn(appName);
        when(mAppIcon.getViewTreeObserver()).thenReturn(viewTreeObserver);
    }
}
