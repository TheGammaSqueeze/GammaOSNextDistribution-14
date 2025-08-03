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
package android.smartspace.cts;

import static android.app.smartspace.SmartspaceTarget.FEATURE_ALARM;

import static com.google.common.truth.Truth.assertThat;
import static androidx.test.InstrumentationRegistry.getContext;

import android.app.smartspace.SmartspaceAction;
import android.app.smartspace.SmartspaceConfig;
import android.app.smartspace.SmartspaceTarget;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Process;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link SmartspaceConfig}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SmartspaceConfigTest {

    private static final String TAG = "SmartspaceTargetTest";

    @Test
    public void testCreateSmartspaceConfig() {
        Bundle extras = new Bundle();
        extras.putString("key", "value");
        SmartspaceConfig config = new SmartspaceConfig.Builder(getContext(),
                "ui_surface").setSmartspaceTargetCount(5).setExtras(extras).build();


        assertThat(config.getUiSurface()).isEqualTo("ui_surface");
        assertThat(config.getExtras()).isEqualTo(extras);
        assertThat(config.getSmartspaceTargetCount()).isEqualTo(5);
        assertThat(config.getPackageName()).isEqualTo(getContext().getPackageName());

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        config.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SmartspaceConfig copy = SmartspaceConfig.CREATOR.createFromParcel(parcel);
        assertThat(config.getUiSurface()).isEqualTo(copy.getUiSurface());
        parcel.recycle();
    }

}
