/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.uwb.multichip;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.multchip.UwbMultichipData;
import com.android.uwb.resources.R;

import com.google.uwb.support.multichip.ChipInfoParams;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

/**
 * Unit tests for {@link com.android.server.uwb.multichip.UwbMultichipData}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbMultichipDataTest {
    @Rule
    public TemporaryFolder mTempFolder = TemporaryFolder.builder().build();
    private static final String NONEXISTENT_CONFIG_FILE = "doesNotExist.xml";
    @Mock
    private Context mMockContext;
    @Mock
    private Resources mMockResources;

    private UwbMultichipData mUwbMultichipData;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        mUwbMultichipData = new UwbMultichipData(mMockContext);
    }

    @Test
    public void testInitializeSingleChip() {
        when(mMockResources.getBoolean(R.bool.config_isMultichip)).thenReturn(false);

        mUwbMultichipData.initialize();
        List<ChipInfoParams> chipInfos = mUwbMultichipData.getChipInfos();
        assertThat(chipInfos).hasSize(1);
        ChipInfoParams chipInfo  = chipInfos.get(0);
        assertThat(chipInfo.getChipId()).isEqualTo(mUwbMultichipData.getDefaultChipId());
        assertThat(chipInfo.getPositionX()).isEqualTo(0.0);
        assertThat(chipInfo.getPositionY()).isEqualTo(0.0);
        assertThat(chipInfo.getPositionZ()).isEqualTo(0.0);
    }

    @Test
    public void testInitializeMultiChipButNoFilePath() {
        when(mMockResources.getBoolean(R.bool.config_isMultichip)).thenReturn(true);

        mUwbMultichipData.initialize();
        List<ChipInfoParams> chipInfos = mUwbMultichipData.getChipInfos();
        assertThat(chipInfos).hasSize(1);
        ChipInfoParams chipInfo  = chipInfos.get(0);
        assertThat(chipInfo.getChipId()).isEqualTo(mUwbMultichipData.getDefaultChipId());
        assertThat(chipInfo.getPositionX()).isEqualTo(0.0);
        assertThat(chipInfo.getPositionY()).isEqualTo(0.0);
        assertThat(chipInfo.getPositionZ()).isEqualTo(0.0);

        List<String> chipIds = mUwbMultichipData.getChipIds();
        assertThat(chipIds).hasSize(1);
        assertThat(chipIds.get(0)).isEqualTo(mUwbMultichipData.getDefaultChipId());
    }

    @Test
    public void testInitializeMultiChipButFileDoesNotExist() {
        when(mMockResources.getBoolean(R.bool.config_isMultichip)).thenReturn(true);
        when(mMockResources.getString(R.string.config_multichipConfigPath))
                .thenReturn(NONEXISTENT_CONFIG_FILE);

        mUwbMultichipData.initialize();
        List<ChipInfoParams> chipInfos = mUwbMultichipData.getChipInfos();
        assertThat(chipInfos).hasSize(1);
        ChipInfoParams chipInfo  = chipInfos.get(0);
        assertThat(chipInfo.getChipId()).isEqualTo(mUwbMultichipData.getDefaultChipId());
        assertThat(chipInfo.getPositionX()).isEqualTo(0.0);
        assertThat(chipInfo.getPositionY()).isEqualTo(0.0);
        assertThat(chipInfo.getPositionZ()).isEqualTo(0.0);

        List<String> chipIds = mUwbMultichipData.getChipIds();
        assertThat(chipIds).hasSize(1);
        assertThat(chipIds.get(0)).isEqualTo(mUwbMultichipData.getDefaultChipId());
    }

    @Test
    public void testInitializeMultiChipOneChipConfig() throws Exception {
        when(mMockResources.getBoolean(R.bool.config_isMultichip)).thenReturn(true);
        when(mMockResources.getString(R.string.config_multichipConfigPath)).thenReturn(
                MultichipConfigFileCreator.createOneChipFileFromResource(mTempFolder,
                        getClass()).getCanonicalPath());

        mUwbMultichipData.initialize();

        List<ChipInfoParams> chipInfos = mUwbMultichipData.getChipInfos();
        assertThat(chipInfos).hasSize(1);
        ChipInfoParams chipInfo  = chipInfos.get(0);
        assertThat(chipInfo.getChipId()).isEqualTo("chipIdString");
        assertThat(chipInfo.getPositionX()).isEqualTo(1.0);
        assertThat(chipInfo.getPositionY()).isEqualTo(2.0);
        assertThat(chipInfo.getPositionZ()).isEqualTo(3.0);

        List<String> chipIds = mUwbMultichipData.getChipIds();
        assertThat(chipIds).hasSize(1);
        assertThat(chipIds.get(0)).isEqualTo("chipIdString");
    }

    @Test
    public void testInitializeMultiChipNoPosition() throws Exception {
        when(mMockResources.getBoolean(R.bool.config_isMultichip)).thenReturn(true);
        when(mMockResources.getString(R.string.config_multichipConfigPath)).thenReturn(
                MultichipConfigFileCreator.createNoPositionFileFromResource(mTempFolder,
                        getClass()).getCanonicalPath());

        mUwbMultichipData.initialize();

        List<ChipInfoParams> chipInfos = mUwbMultichipData.getChipInfos();
        assertThat(chipInfos).hasSize(1);
        ChipInfoParams chipInfo  = chipInfos.get(0);
        assertThat(chipInfo.getChipId()).isEqualTo("chipIdString");
        assertThat(chipInfo.getPositionX()).isEqualTo(0.0);
        assertThat(chipInfo.getPositionY()).isEqualTo(0.0);
        assertThat(chipInfo.getPositionZ()).isEqualTo(0.0);

        List<String> chipIds = mUwbMultichipData.getChipIds();
        assertThat(chipIds).hasSize(1);
        assertThat(chipIds.get(0)).isEqualTo("chipIdString");
    }

    @Test
    public void testInitializeMultiChipTwoChipConfig() throws Exception {
        when(mMockResources.getBoolean(R.bool.config_isMultichip)).thenReturn(true);
        when(mMockResources.getString(R.string.config_multichipConfigPath)).thenReturn(
                MultichipConfigFileCreator.createTwoChipFileFromResource(mTempFolder,
                        getClass()).getCanonicalPath());

        mUwbMultichipData.initialize();

        List<ChipInfoParams> chipInfos = mUwbMultichipData.getChipInfos();
        assertThat(chipInfos).hasSize(2);

        ChipInfoParams chipInfo  = chipInfos.get(0);
        assertThat(chipInfo.getChipId()).isEqualTo("chipIdString1");
        assertThat(chipInfo.getPositionX()).isEqualTo(1.0);
        assertThat(chipInfo.getPositionY()).isEqualTo(2.0);
        assertThat(chipInfo.getPositionZ()).isEqualTo(3.0);

        chipInfo  = chipInfos.get(1);
        assertThat(chipInfo.getChipId()).isEqualTo("chipIdString2");
        assertThat(chipInfo.getPositionX()).isEqualTo(4.0);
        assertThat(chipInfo.getPositionY()).isEqualTo(5.0);
        assertThat(chipInfo.getPositionZ()).isEqualTo(6.0);


        List<String> chipIds = mUwbMultichipData.getChipIds();
        assertThat(chipIds).hasSize(2);
        assertThat(chipIds.get(0)).isEqualTo("chipIdString1");
        assertThat(chipIds.get(1)).isEqualTo("chipIdString2");
    }

}
