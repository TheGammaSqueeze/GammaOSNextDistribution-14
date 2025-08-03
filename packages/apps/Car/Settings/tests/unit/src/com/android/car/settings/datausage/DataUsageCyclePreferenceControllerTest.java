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
import com.android.settingslib.net.NetworkCycleChartData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class DataUsageCyclePreferenceControllerTest {
    private static final long END_TIME_1 = System.currentTimeMillis();
    private static final long START_TIME_1 = END_TIME_1 - TimeUnit.DAYS.toMillis(10);
    private static final long END_TIME_2 = START_TIME_1;
    private static final long START_TIME_2 = END_TIME_2 - TimeUnit.DAYS.toMillis(30);

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private DataUsageCyclePreferenceController mPreferenceController;
    private ListPreference mPreference;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private DataUsageCycleBasePreferenceController.DataCyclePickedListener mMockListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new DataUsageCyclePreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions);
        mPreference = new ListPreference(mContext);
        mPreferenceController.setDataCyclePickedListener(mMockListener);
        mPreferenceController.setDataUsageInfo(new DataUsageController.DataUsageInfo());
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @Test
    public void onCreate_testOnDataLoaded() {
        mPreferenceController.onDataLoaded(createDataList());

        assertThat(mPreference.getSummary().toString()).isEqualTo(
                mPreferenceController.formatDate(START_TIME_1, END_TIME_1));
    }

    private List<NetworkCycleChartData> createDataList() {
        List<NetworkCycleChartData> dataList = new ArrayList<>();
        NetworkCycleChartData networkCycleChartData1 =
                (NetworkCycleChartData) new NetworkCycleChartData.Builder()
                        .setEndTime(END_TIME_1)
                        .setStartTime(START_TIME_1)
                        .build();
        dataList.add(networkCycleChartData1);

        NetworkCycleChartData networkCycleChartData2 =
                (NetworkCycleChartData) new NetworkCycleChartData.Builder()
                        .setEndTime(END_TIME_2)
                        .setStartTime(START_TIME_2)
                        .build();
        dataList.add(networkCycleChartData2);

        return dataList;
    }
}
