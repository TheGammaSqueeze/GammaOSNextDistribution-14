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

package android.car.cluster;

import static android.view.Display.INVALID_DISPLAY;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class ClusterStateTest {
    private static final ClusterState CLUSTER_STATE = new ClusterState() {
        {
            on = true;
            bounds = new Rect(1, 1, 2, 2);
            insets = Insets.of(0, 1, 2, 3);
            uiType = ClusterHomeManager.UI_TYPE_CLUSTER_NONE;
            displayId = INVALID_DISPLAY;
        }
    };

    @Test
    public void clusterStateWriteAndReadParcel() {
        ClusterState originalClusterState = CLUSTER_STATE;
        Parcel parcel = Parcel.obtain();
        originalClusterState.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ClusterState convertedClusterState = ClusterState.CREATOR.createFromParcel(parcel);

        assertThat(convertedClusterState.on).isEqualTo(true);
        assertThat(convertedClusterState.bounds).isEqualTo(new Rect(1, 1, 2, 2));
        assertThat(convertedClusterState.insets).isEqualTo(Insets.of(0, 1, 2, 3));
        assertThat(convertedClusterState.uiType).isEqualTo(ClusterHomeManager.UI_TYPE_CLUSTER_NONE);
        assertThat(convertedClusterState.displayId).isEqualTo(INVALID_DISPLAY);
    }

    @Test
    public void clusterStateNewArray() {
        ClusterState[] clusterStateArray = ClusterState.CREATOR.newArray(10);
        assertThat(clusterStateArray).hasLength(10);
    }
}
