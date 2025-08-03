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
package com.android.car.cluster;

import static com.google.common.truth.Truth.assertThat;

import android.car.cluster.ClusterActivityState;
import android.graphics.Rect;
import android.os.Bundle;

import org.junit.Test;


/** Unit tests for {@link ClusterActivityState} */
public final class ClusterActivityStateUnitTest {
    @Test
    public void create_createsObject() {
        ClusterActivityState clusterActivityState =
                ClusterActivityState.create(/* visible= */ true,
                        /* unobscuredBounds= */ new Rect(2, 3, 2, 3));
        Bundle extras = new Bundle();
        extras.putString("KEY_1", "VALUE_1");
        clusterActivityState.setExtras(extras);

        assertThat(clusterActivityState.isVisible()).isTrue();
        assertThat(clusterActivityState.getUnobscuredBounds()).isEqualTo(new Rect(2, 3, 2, 3));
        assertThat(clusterActivityState.getExtras()).isEqualTo(extras);
    }

    @Test
    public void fromBundle_createsObject() {
        ClusterActivityState clusterActivityState =
                ClusterActivityState.create(/* visible= */ false,
                        /* unobscuredBounds= */ new Rect(2, 3, 2, 3));
        // Arbitrarily update the params
        clusterActivityState.setVisible(true);
        clusterActivityState.setUnobscuredBounds(new Rect(2, 2, 2, 2));

        ClusterActivityState clusterActivityStateFromBundle =
                ClusterActivityState.fromBundle(clusterActivityState.toBundle());

        assertThat(clusterActivityState.toString())
                .isEqualTo(clusterActivityStateFromBundle.toString());
    }

    @Test
    public void toString_outputsInCorrectFormat() {
        ClusterActivityState clusterActivityState =
                ClusterActivityState.create(/* visible= */ false,
                        /* unobscuredBounds= */ new Rect(2, 3, 2, 3));
        Bundle extras = new Bundle();
        extras.putString("KEY_1", "VALUE_1");
        clusterActivityState.setExtras(extras);

        assertThat(clusterActivityState.toString()).isEqualTo("ClusterActivityState "
                + "{visible: false, unobscuredBounds: " + new Rect(2, 3, 2, 3) + ", "
                + "extras: " + extras + " }");
    }
}
