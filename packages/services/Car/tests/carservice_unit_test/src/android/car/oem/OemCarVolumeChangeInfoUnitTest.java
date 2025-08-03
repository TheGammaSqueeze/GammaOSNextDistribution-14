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

package android.car.oem;

import static org.junit.Assert.assertThrows;

import android.car.media.CarVolumeGroupInfo;
import android.car.test.AbstractExpectableTestCase;
import android.os.Parcel;

import org.junit.Test;

public final class OemCarVolumeChangeInfoUnitTest extends AbstractExpectableTestCase {

    private static final int TEST_ZONE_ID = 8;
    private static final int TEST_PRIMARY_GROUP_ID = 7;
    private static final String TEST_GROUP_NAME = "3";

    private static final CarVolumeGroupInfo TEST_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME, TEST_ZONE_ID, TEST_PRIMARY_GROUP_ID)
                    .setMaxVolumeGainIndex(9_000).setMinVolumeGainIndex(0)
                    .setMaxActivationVolumeGainIndex(8_000).setMinActivationVolumeGainIndex(100)
                    .build();

    @Test
    public void build() {
        OemCarVolumeChangeInfo info =
                new OemCarVolumeChangeInfo.Builder(/* volumeChanged= */ true).build();

        expectWithMessage("Car volume change build info changed")
                .that(info.isVolumeChanged()).isTrue();
    }

    @Test
    public void setChangedVolumeGroup() {
        OemCarVolumeChangeInfo info =
                new OemCarVolumeChangeInfo.Builder(/* volumeChanged= */ false)
                        .setChangedVolumeGroup(TEST_VOLUME_INFO).build();

        expectWithMessage("Car volume change build changed volume")
                .that(info.getChangedVolumeGroup()).isEqualTo(TEST_VOLUME_INFO);
    }


    @Test
    public void builder_withReuse_fails() {
        OemCarVolumeChangeInfo.Builder builder =
                new OemCarVolumeChangeInfo.Builder(/* volumeChanged= */ false);
        builder.build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                builder.build()
        );

        expectWithMessage("Reuse builder exception")
                .that(thrown).hasMessageThat().contains("should not be reused");
    }

    @Test
    public void writeToParcel() {
        Parcel parcel = Parcel.obtain();
        OemCarVolumeChangeInfo info =
                new OemCarVolumeChangeInfo.Builder(/* volumeChanged= */ true).build();

        info.writeToParcel(parcel, /* flags= */ 0);
        parcel.setDataPosition(/* position= */ 0);

        expectWithMessage("Car volume change from parcel")
                .that(OemCarVolumeChangeInfo.CREATOR.createFromParcel(parcel)).isEqualTo(info);
    }

    @Test
    public void newArray() {
        OemCarVolumeChangeInfo[] infos = OemCarVolumeChangeInfo.CREATOR.newArray(/* size= */ 3);

        expectWithMessage("Car volume changes").that(infos).hasLength(3);
    }

    @Test
    public void equals_forSameContent() {
        OemCarVolumeChangeInfo info =
                new OemCarVolumeChangeInfo.Builder(/* volumeChanged= */ false)
                        .setChangedVolumeGroup(TEST_VOLUME_INFO).build();
        OemCarVolumeChangeInfo info2 =
                new OemCarVolumeChangeInfo.Builder(/* volumeChanged= */ false)
                        .setChangedVolumeGroup(TEST_VOLUME_INFO).build();

        expectWithMessage("Car volume change with same content").that(info2).isEqualTo(info);
    }

    @Test
    public void equals_forDifferentContent() {
        OemCarVolumeChangeInfo info =
                new OemCarVolumeChangeInfo.Builder(/* volumeChanged= */ true)
                        .setChangedVolumeGroup(TEST_VOLUME_INFO).build();
        OemCarVolumeChangeInfo info2 =
                new OemCarVolumeChangeInfo.Builder(/* volumeChanged= */ false)
                        .setChangedVolumeGroup(TEST_VOLUME_INFO).build();

        expectWithMessage("Car volume change with different content").that(info2)
                .isNotEqualTo(info);
    }

    @Test
    public void equals_forNull() {
        OemCarVolumeChangeInfo info =
                new OemCarVolumeChangeInfo.Builder(/* volumeChanged= */ false)
                        .setChangedVolumeGroup(TEST_VOLUME_INFO).build();

        expectWithMessage("Car volume change equal for null").that(info.equals(null)).isFalse();
    }

    @Test
    public void hashCode_forSameContent() {
        OemCarVolumeChangeInfo info =
                new OemCarVolumeChangeInfo.Builder(/* volumeChanged= */ false)
                        .setChangedVolumeGroup(TEST_VOLUME_INFO).build();
        OemCarVolumeChangeInfo info2 =
                new OemCarVolumeChangeInfo.Builder(/* volumeChanged= */ false)
                        .setChangedVolumeGroup(TEST_VOLUME_INFO).build();

        expectWithMessage("Car volume change hash with same content")
                .that(info2.hashCode()).isEqualTo(info.hashCode());
    }

    @Test
    public void emptyChange_isEmpty() {
        expectWithMessage("Empty volume change")
                .that(OemCarVolumeChangeInfo.EMPTY_OEM_VOLUME_CHANGE.isVolumeChanged()).isFalse();
    }

    @Test
    public void describeContents() {
        expectWithMessage("Volume change empty description")
                .that(OemCarVolumeChangeInfo.EMPTY_OEM_VOLUME_CHANGE.describeContents())
                .isEqualTo(0);
    }
}
