/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.view.accessibility.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.LocaleList;
import android.os.Parcel;
import android.platform.test.annotations.Presubmit;
import android.text.TextUtils;
import android.view.Display;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Class for testing {@link AccessibilityWindowInfo}.
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class AccessibilityWindowInfoTest {

    @Rule
    public final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @SmallTest
    @Test
    public void testObtain() {
        AccessibilityWindowInfo w1 = AccessibilityWindowInfo.obtain();
        assertThat(w1).isNotNull();

        AccessibilityWindowInfo w2 = AccessibilityWindowInfo.obtain(w1);
        assertThat(w2).isNotSameInstanceAs(w1);
        assertThat(areWindowsEqual(w1, w2)).isTrue();
    }

    @SmallTest
    @Test
    public void testParceling() {
        Parcel parcel = Parcel.obtain();
        AccessibilityWindowInfo w1 = AccessibilityWindowInfo.obtain();
        w1.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AccessibilityWindowInfo w2 = AccessibilityWindowInfo.CREATOR.createFromParcel(parcel);
        assertThat(w2).isNotSameInstanceAs(w1);
        assertThat(areWindowsEqual(w1, w2)).isTrue();
        parcel.recycle();
    }

    @SmallTest
    @Test
    public void testDefaultValues() {
        AccessibilityWindowInfo w = AccessibilityWindowInfo.obtain();
        assertThat(w.getChildCount()).isEqualTo(0);
        assertThat(w.getType()).isEqualTo(-1);
        assertThat(w.getLayer()).isEqualTo(-1);
        assertThat(w.getId()).isEqualTo(-1);
        assertThat(w.describeContents()).isEqualTo(0);
        assertThat(w.getDisplayId()).isEqualTo(Display.INVALID_DISPLAY);
        assertThat(w.getParent()).isNull();
        assertThat(w.getRoot()).isNull();
        assertThat(w.isAccessibilityFocused()).isFalse();
        assertThat(w.isActive()).isFalse();
        assertThat(w.isFocused()).isFalse();
        assertThat(w.getTitle()).isNull();

        Rect rect = new Rect();
        w.getBoundsInScreen(rect);
        assertThat(rect.isEmpty()).isTrue();

        Region region = new Region();
        w.getRegionInScreen(region);
        assertThat(region.isEmpty()).isTrue();
        assertThat(w.getTransitionTimeMillis()).isEqualTo(0);
        assertThat(w.getLocales()).isEqualTo(LocaleList.getEmptyLocaleList());

        try {
            w.getChild(0);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // Expected.
        }
    }

    @SmallTest
    @Test
    public void testRecycle() {
        AccessibilityWindowInfo w = AccessibilityWindowInfo.obtain();
        w.recycle();

        try {
            w.recycle();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            // Expected.
        }
    }

    private boolean areWindowsEqual(AccessibilityWindowInfo w1, AccessibilityWindowInfo w2) {
        boolean equality = w1.equals(w2);
        equality &= TextUtils.equals(w1.getTitle(), w2.getTitle());
        equality &= w1.isAccessibilityFocused() == w2.isAccessibilityFocused();
        equality &= w1.isActive() == w2.isActive();
        equality &= w1.getType() == w2.getType();
        equality &= w1.getLayer() == w2.getLayer();
        equality &= w1.getDisplayId() == w2.getDisplayId();
        Rect bounds1 = new Rect();
        Rect bounds2 = new Rect();
        w1.getBoundsInScreen(bounds1);
        w2.getBoundsInScreen(bounds2);
        equality &= bounds1.equals(bounds2);
        Region regions1 = new Region();
        Region regions2 = new Region();
        w1.getRegionInScreen(regions1);
        w2.getRegionInScreen(regions2);
        equality &= regions1.equals(regions2);
        equality &= w1.getTransitionTimeMillis() == w2.getTransitionTimeMillis();
        equality &= w1.getLocales().equals(w2.getLocales());
        return equality;
    }
}
