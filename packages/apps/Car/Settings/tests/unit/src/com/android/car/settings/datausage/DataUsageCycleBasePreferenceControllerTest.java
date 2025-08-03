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

package com.android.car.settings.datausage;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertThrows;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.ListPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.settingslib.net.DataUsageController;
import com.android.settingslib.net.NetworkCycleData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class DataUsageCycleBasePreferenceControllerTest {
    private static final long END_TIME_1 = System.currentTimeMillis();
    private static final long START_TIME_1 = END_TIME_1 - TimeUnit.DAYS.toMillis(10);
    private static final long END_TIME_2 = START_TIME_1;
    private static final long START_TIME_2 = END_TIME_2 - TimeUnit.DAYS.toMillis(30);
    private static final String PERIOD = "data_usage_info_period";

    private Context mContext;
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private DataUsageCycleBasePreferenceController mPreferenceController;
    private ListPreference mPreference;
    private DataUsageController.DataUsageInfo mDataUsageInfo;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private DataUsageCycleBasePreferenceController.DataCyclePickedListener mMockListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mContext = ApplicationProvider.getApplicationContext();
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new TestDataUsageCycleBasePreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions);
        mPreference = new ListPreference(mContext);
        mDataUsageInfo = new DataUsageController.DataUsageInfo();
        mDataUsageInfo.period = PERIOD;
    }

    @Test
    public void onCreate_noListenerSet_throwException() {
        assertThrows(IllegalStateException.class,
                () -> PreferenceControllerTestUtil.assignPreference(mPreferenceController,
                        mPreference));
    }

    @Test
    public void onCreate_dataNotLoaded_testSummary() {
        finishSetUp();

        assertThat(mPreference.isEnabled()).isFalse();
        assertThat(mPreference.getSummary().toString()).isEqualTo(PERIOD);
    }

    @Test
    public void onLoaded_loadEmptyData_testEntryInfo() {
        finishSetUp();
        mPreferenceController.onLoaded(Collections.emptyList());

        assertThat(mPreference.isEnabled()).isFalse();
        assertThat(mPreference.getSummary().toString()).isEqualTo(PERIOD);
    }

    @Test
    public void onLoaded_testEntryInfo() {
        finishSetUp();
        mPreferenceController.onLoaded(createDataList());

        String expectedString1 = mPreferenceController.formatDate(START_TIME_1, END_TIME_1);
        String expectedString2 = mPreferenceController.formatDate(START_TIME_2, END_TIME_2);

        assertThat(mPreference.isEnabled()).isTrue();
        verifyArray(mPreference.getEntries(), Arrays.asList(expectedString1, expectedString2));
        verifyArray(mPreference.getEntryValues(), Arrays.asList("0", "1"));
        assertThat(mPreference.getSummary().toString()).isEqualTo(expectedString1);
        assertThat(mPreference.getEntry().toString()).isEqualTo(expectedString1);
    }

    @Test
    public void onLoaded_testDataCyclePickedListener() {
        finishSetUp();
        mPreferenceController.onLoaded(createDataList());

        verify(mMockListener).onDataCyclePicked(any(), any());
    }

    private void finishSetUp() {
        mPreferenceController.setDataCyclePickedListener(mMockListener);
        mPreferenceController.setDataUsageInfo(mDataUsageInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
    }

    private void verifyArray(CharSequence[] array, List<String> list) {
        assertThat(array.length).isEqualTo(2);
        assertThat(array[0].toString()).isEqualTo(list.get(0));
        assertThat(array[1].toString()).isEqualTo(list.get(1));
    }

    private List<NetworkCycleData> createDataList() {
        List<NetworkCycleData> dataList = new ArrayList<>();
        NetworkCycleData networkCycleData1 =
                new NetworkCycleData.Builder()
                        .setEndTime(END_TIME_1)
                        .setStartTime(START_TIME_1)
                        .build();
        dataList.add(networkCycleData1);

        NetworkCycleData networkCycleData2 =
                new NetworkCycleData.Builder()
                        .setEndTime(END_TIME_2)
                        .setStartTime(START_TIME_2)
                        .build();
        dataList.add(networkCycleData2);

        return dataList;
    }

    private static class TestDataUsageCycleBasePreferenceController
            extends DataUsageCycleBasePreferenceController<NetworkCycleData> {

        TestDataUsageCycleBasePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }
    }
}
