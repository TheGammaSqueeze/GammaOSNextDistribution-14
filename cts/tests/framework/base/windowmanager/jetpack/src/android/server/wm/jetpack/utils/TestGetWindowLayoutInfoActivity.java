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

package android.server.wm.jetpack.utils;

import static android.server.wm.jetpack.extensions.util.SidecarUtil.getSidecarInterface;
import static android.server.wm.jetpack.utils.WindowManagerJetpackTestBase.getActivityWindowToken;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import android.os.Bundle;
import android.os.IBinder;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.window.sidecar.SidecarDisplayFeature;
import androidx.window.sidecar.SidecarInterface;
import androidx.window.sidecar.SidecarWindowLayoutInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Activity that can verify the return value of
 * {@link SidecarInterface#getWindowLayoutInfo(IBinder)} on activity's different states.
 */
// TODO (b/201119421) - explore moving the assert logic out from here and into the calling test
public class TestGetWindowLayoutInfoActivity extends TestActivity {

    private SidecarInterface mSidecarInterface;
    @Nullable private SidecarWindowLayoutInfo mPrevWindowLayoutInfo;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSidecarInterface = getSidecarInterface(this);
        assertCorrectWindowLayoutInfoOrException(true /* isOkToThrowException */);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        assertCorrectWindowLayoutInfoOrException(true /* isOkToThrowException */);
    }

    @Override
    protected void onResume() {
        super.onResume();
        assertCorrectWindowLayoutInfoOrException(true /* isOkToThrowException */);
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        super.onLayoutChange(v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom);
        assertCorrectWindowLayoutInfoOrException(false /* isOkToThrowException */);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void assertCorrectWindowLayoutInfoOrException(boolean isOkToThrowException) {
        IBinder windowToken = getActivityWindowToken(this);
        if (windowToken == null) {
            return;
        }

        SidecarWindowLayoutInfo windowLayoutInfo = null;
        try {
            windowLayoutInfo = mSidecarInterface.getWindowLayoutInfo(windowToken);
        } catch (Exception e) {
            assertThat(isOkToThrowException).isTrue();
        }

        if (mPrevWindowLayoutInfo == null) {
            mPrevWindowLayoutInfo = windowLayoutInfo;
        } else {
            assertEqualWindowLayoutInfo(mPrevWindowLayoutInfo, windowLayoutInfo);
        }
    }

    private static class SidecarDisplayFeatureComparisonWrapper {

        private SidecarDisplayFeature mFeature;

        private SidecarDisplayFeatureComparisonWrapper(
                @NonNull SidecarDisplayFeature feature) {
            mFeature = feature;
        }

        public static SidecarDisplayFeatureComparisonWrapper create(
                @NonNull SidecarDisplayFeature feature) {
            return new SidecarDisplayFeatureComparisonWrapper(feature);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof SidecarDisplayFeatureComparisonWrapper)) {
                return false;
            }
            SidecarDisplayFeatureComparisonWrapper other
                    = (SidecarDisplayFeatureComparisonWrapper) o;
            return mFeature.getRect().equals(other.mFeature.getRect())
                    && mFeature.getType() == other.mFeature.getType();
        }

        @Override
        public final int hashCode() {
            int result = mFeature.getRect().hashCode();
            result = 31 * result + mFeature.getType();
            return result;
        }
    }

    private void assertEqualWindowLayoutInfo(SidecarWindowLayoutInfo info1,
            SidecarWindowLayoutInfo info2) {
        // If neither has any display features, then they are equal
        if (info1.displayFeatures == null && info2.displayFeatures == null) {
            return;
        }
        // Convert display features into comparable display features
        Set<SidecarDisplayFeatureComparisonWrapper> displayFeatures1 = collectDisplayFeatures(
                info1);
        Set<SidecarDisplayFeatureComparisonWrapper> displayFeatures2 = collectDisplayFeatures(
                info2);
        assertEquals(displayFeatures1, displayFeatures2);
    }

    private Set<SidecarDisplayFeatureComparisonWrapper> collectDisplayFeatures(
            @NonNull SidecarWindowLayoutInfo info) {
        if (info.displayFeatures == null) {
            return new HashSet<SidecarDisplayFeatureComparisonWrapper>();
        }
        return info.displayFeatures
                .stream()
                .map(SidecarDisplayFeatureComparisonWrapper::create)
                .collect(Collectors.toSet());
    }
}
