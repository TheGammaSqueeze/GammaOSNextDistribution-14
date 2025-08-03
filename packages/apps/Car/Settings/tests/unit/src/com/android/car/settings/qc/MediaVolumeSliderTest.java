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

package com.android.car.settings.qc;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.UserManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MediaVolumeSliderTest extends VolumeSliderTestCase {

    private MediaVolumeSlider mVolumeSlider;

    @Before
    public void setUp() {
        super.setUp();
        mVolumeSlider = getMediaVolumeSliderInstance();
    }

    @Test
    public void getQCItem_titleSet() {
        verifyTitleSet(getMediaVolumeSlider(), R.string.test_volume_music);
    }

    @Test
    public void getQCItem_iconSet() {
        verifyIconSet(getMediaVolumeSlider(), R.drawable.test_icon);
    }

    @Test
    public void getQCItem_createsSlider() {
        verifySliderCreated(getMediaVolumeSlider());
    }

    @Test
    public void getQCItem_hasBaseUmRestriction_sliderDisabled() {
        setBaseUserRestriction(UserManager.DISALLOW_ADJUST_VOLUME, /* restricted= */ true);
        verifyBaseUmRestriction(getMediaVolumeSlider());
    }

    @Test
    public void getQCItem_hasUmRestriction_sliderClickableWhileDisabled() {
        setUserRestriction(UserManager.DISALLOW_ADJUST_VOLUME, /* restricted= */ true);
        verifyUmRestriction(getMediaVolumeSlider());
    }

    @Test
    public void onNotifyChange_updatesVolume() {
        verifyVolumeChanged(mVolumeSlider, GROUP_ID);
    }

    protected QCRow getMediaVolumeSlider() {
        QCItem item = mVolumeSlider.getQCItem();
        assertThat(item).isNotNull();
        assertThat(item instanceof QCList).isTrue();
        QCList list = (QCList) item;
        assertThat(list.getRows().size()).isEqualTo(1);
        return list.getRows().get(0);
    }

    protected MediaVolumeSlider getMediaVolumeSliderInstance() {
        return new TestMediaVolumeSlider(mContext);
    }

    private static class TestMediaVolumeSlider extends MediaVolumeSlider {
        TestMediaVolumeSlider(Context context) {
            super(context);
        }

        @Override
        protected int carVolumeItemsXml() {
            return R.xml.test_car_volume_items;
        }
    }
}
