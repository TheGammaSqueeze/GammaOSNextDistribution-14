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

package com.android.server.uwb.discovery.info;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.discovery.info.FiraProfileSupportInfo.FiraProfile;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit test for {@link FiraProfileSupportInfo}
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class FiraProfileSupportInfoTest {

    private static final byte[] BYTES = new byte[] {0x1};
    private static final FiraProfile[] PROFILES = new FiraProfile[] {FiraProfile.PACS};

    @Test
    public void fromBytes_emptyData() {
        assertThat(FiraProfileSupportInfo.fromBytes(new byte[] {})).isNull();
    }

    @Test
    public void fromBytes_noProfile() {
        FiraProfileSupportInfo info =
                FiraProfileSupportInfo.fromBytes(new byte[] {0x0, 0x0, 0x0, 0x0, 0x0});
        assertThat(info).isNotNull();

        assertThat(info.supportedFiraProfiles).isEqualTo(new FiraProfile[] {});
    }

    @Test
    public void fromBytes_undefinedProfile() {
        FiraProfileSupportInfo info =
                FiraProfileSupportInfo.fromBytes(new byte[] {(byte) 0x80, 0x0, 0x8, 0x0, 0x1});
        assertThat(info).isNotNull();

        assertThat(info.supportedFiraProfiles).isEqualTo(PROFILES);
    }

    @Test
    public void fromBytes_succeed() {
        FiraProfileSupportInfo info = FiraProfileSupportInfo.fromBytes(new byte[] {0x0, 0x0, 0x1});
        assertThat(info).isNotNull();

        assertThat(info.supportedFiraProfiles).isEqualTo(PROFILES);
    }

    @Test
    public void toBytes_noProfile() {
        FiraProfileSupportInfo info = new FiraProfileSupportInfo(new FiraProfile[] {});
        assertThat(info).isNotNull();

        byte[] result = FiraProfileSupportInfo.toBytes(info);
        assertThat(result).isEqualTo(new byte[] {});
    }

    @Test
    public void toBytes_duplicateProfile() {
        FiraProfileSupportInfo info =
                new FiraProfileSupportInfo(
                        new FiraProfile[] {FiraProfile.PACS, FiraProfile.PACS, FiraProfile.PACS});
        assertThat(info).isNotNull();

        byte[] result = FiraProfileSupportInfo.toBytes(info);
        assertThat(result).isEqualTo(BYTES);
    }

    @Test
    public void toBytes_succeed() {
        FiraProfileSupportInfo info = new FiraProfileSupportInfo(PROFILES);
        assertThat(info).isNotNull();

        byte[] result = FiraProfileSupportInfo.toBytes(info);
        assertThat(result).isEqualTo(BYTES);
    }
}
