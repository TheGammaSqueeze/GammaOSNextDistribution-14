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
import com.android.car.qc.QCSlider;
import com.android.car.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NavigationVolumeSliderTest extends VolumeSliderTestCase {
    private NavigationVolumeSlider mVolumeSlider;

    @Before
    public void setUp() {
        super.setUp();
        mVolumeSlider = new TestNavigationVolumeSlider(mContext);
    }

    @Test
    public void getQCItem_titleSet() {
        verifyTitleSet(getNavigationVolumeSlider(), R.string.test_volume_navigation);
    }

    @Test
    public void getQCItem_iconSet() {
        verifyIconSet(getNavigationVolumeSlider(), R.drawable.test_icon);
    }

    @Test
    public void getQCItem_createsSlider() {
        verifySliderCreated(getNavigationVolumeSlider());
    }

    @Test
    public void getQCItem_hasBaseUmRestriction_sliderDisabled() {
        setBaseUserRestriction(UserManager.DISALLOW_ADJUST_VOLUME, /* restricted= */ true);
        verifyBaseUmRestriction(getNavigationVolumeSlider());
    }

    @Test
    public void getQCItem_hasUmRestriction_sliderClickableWhileDisabled() {
        setUserRestriction(UserManager.DISALLOW_ADJUST_VOLUME, /* restricted= */ true);
        verifyUmRestriction(getNavigationVolumeSlider());
    }

    @Test
    public void onNotifyChange_updatesVolume() {
        verifyVolumeChanged(mVolumeSlider, GROUP_ID);
    }

    @Test
    public void getQCItem_createsVolumeSlider_zoneWrite() {
        mVolumeSlider.setAvailabilityStatusForZone("write");
        QCItem item = mVolumeSlider.getQCItem();
        assertThat(item).isNotNull();
        assertThat(item instanceof QCList).isTrue();
        QCList list = (QCList) item;
        assertThat(list.getRows().size()).isEqualTo(1);
        QCRow row = list.getRows().get(0);
        QCSlider slider = row.getSlider();
        assertThat(slider.isEnabled()).isTrue();
    }

    @Test
    public void getQCItem_createsVolumeSlider_zoneRead() {
        mVolumeSlider.setAvailabilityStatusForZone("read");
        QCItem item = mVolumeSlider.getQCItem();
        assertThat(item).isNotNull();
        assertThat(item instanceof QCList).isTrue();
        QCList list = (QCList) item;
        assertThat(list.getRows().size()).isEqualTo(1);
        QCRow row = list.getRows().get(0);
        QCSlider slider = row.getSlider();
        assertThat(slider.isEnabled()).isFalse();
        assertThat(slider.isClickableWhileDisabled()).isTrue();
    }

    @Test
    public void getQCItem_createsVolumeSlider_zoneHidden() {
        mVolumeSlider.setAvailabilityStatusForZone("hidden");
        QCItem item = mVolumeSlider.getQCItem();
        assertThat(item).isNull();
    }

    protected QCRow getNavigationVolumeSlider() {
        QCItem item = mVolumeSlider.getQCItem();
        assertThat(item).isNotNull();
        assertThat(item instanceof QCList).isTrue();
        QCList list = (QCList) item;
        assertThat(list.getRows().size()).isEqualTo(1);
        return list.getRows().get(0);
    }

    private static class TestNavigationVolumeSlider extends NavigationVolumeSlider {
        TestNavigationVolumeSlider(Context context) {
            super(context);
        }

        @Override
        protected int carVolumeItemsXml() {
            return R.xml.test_car_volume_items;
        }
    }
}
