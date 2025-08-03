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
package com.android.server.uwb.correction;

import static com.android.server.uwb.correction.TestHelpers.assertClose;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;

import com.android.server.uwb.correction.filtering.NullFilter;
import com.android.server.uwb.correction.filtering.PositionFilterImpl;
import com.android.server.uwb.correction.math.Pose;
import com.android.server.uwb.correction.math.Quaternion;
import com.android.server.uwb.correction.math.SphericalVector;
import com.android.server.uwb.correction.math.SphericalVector.Annotated;
import com.android.server.uwb.correction.math.Vector3;
import com.android.server.uwb.correction.pose.NullPoseSource;
import com.android.server.uwb.correction.primers.NullPrimer;

import org.junit.Test;

@Presubmit
public class UwbFilterEngineTest {

    @Test
    public void basic() {
        UwbFilterEngine engine = new UwbFilterEngine.Builder().build();
        engine.add(SphericalVector.fromRadians(1, 1.2f, 1.3f).toAnnotated(), 0);
        SphericalVector currentVector = engine.compute(0);
        assertThat(currentVector.azimuth).isEqualTo(1);
        assertThat(currentVector.elevation).isEqualTo(1.2f);
        assertThat(currentVector.distance).isEqualTo(1.3f);
        engine.close();
    }

    @Test
    public void testBadReading() {
        UwbFilterEngine engine = new UwbFilterEngine.Builder().build();
        Annotated annotated = SphericalVector.fromRadians(0, 0, 0)
                .toAnnotated(false, false, false);
        engine.add(annotated, 0);
        Annotated currentVector = engine.compute(0);
        assertThat(currentVector.hasAzimuth).isFalse();
        assertThat(currentVector.hasElevation).isFalse();
        assertThat(currentVector.hasDistance).isFalse();
        engine.close();
    }

    @Test
    public void testIntermittentReading() {
        UwbFilterEngine engine = new UwbFilterEngine.Builder().build();
        engine.add(SphericalVector.fromRadians(1, 1.2f, 1.3f).toAnnotated(), 0);

        Annotated annotated = SphericalVector.fromRadians(0, 0, 0)
                .toAnnotated(false, false, false);
        engine.add(annotated, 0);

        Annotated currentVector = engine.compute(0);
        assertThat(currentVector.azimuth).isEqualTo(1);
        assertThat(currentVector.elevation).isEqualTo(1.2f);
        assertThat(currentVector.distance).isEqualTo(1.3f);
        engine.close();
    }

    @Test
    public void testDefaultPose() {
        UwbFilterEngine engine = new UwbFilterEngine.Builder().build();
        // Make sure that an engine without a pose engine always assumes a valid identity pose.
        assertThat(engine.getPose()).isSameInstanceAs(Pose.IDENTITY);
        engine.close();
    }

    @Test
    public void poseChanges() {
        NullPoseSource poseSource = new NullPoseSource();
        UwbFilterEngine engine = new UwbFilterEngine.Builder()
                .setFilter(
                new PositionFilterImpl(new NullFilter(), new NullFilter(), new NullFilter()))
                .setPoseSource(poseSource)
                .build();

        poseSource.changePose(Pose.IDENTITY);
        engine.add(SphericalVector.fromRadians(0.7f, 1.2f, 1.3f).toAnnotated(), 0);

        // Check initial state.
        SphericalVector currentVector = engine.compute(0);
        assertThat(currentVector.azimuth).isEqualTo(0.7f);
        assertThat(currentVector.elevation).isEqualTo(1.2f);
        assertThat(currentVector.distance).isEqualTo(1.3f);

        // Turn left.
        poseSource.changePose(
                new Pose(Vector3.ORIGIN, Quaternion.yawPitchRoll(-0.5f, 0, 0))
        );
        currentVector = engine.compute(0);

        // See if the azimuth is to our right now that we turned left.
        assertClose(currentVector.azimuth, 0.7f - 0.5f);
        assertClose(currentVector.elevation, 1.2f);
        assertClose(currentVector.distance, 1.3f);

        Pose newPose = engine.getPose();
        assertThat(newPose.translation.lengthSquared()).isEqualTo(0);
        assertClose(newPose.rotation.toYawPitchRoll().x, -0.5f);

        engine.close();
    }

    @Test
    public void primerTest() {
        NullPoseSource poseSource = new NullPoseSource();
        NullPrimer primer = new NullPrimer();
        UwbFilterEngine engine = new UwbFilterEngine.Builder()
                .addPrimer(primer)
                .setFilter(
                new PositionFilterImpl(new NullFilter(), new NullFilter(), new NullFilter()))
                .setPoseSource(poseSource)
                .build();

        poseSource.changePose(Pose.IDENTITY);
        engine.add(SphericalVector.fromRadians(-0.7f, 0, 1.3f).toAnnotated(), 0);

        // Check initial state.
        SphericalVector currentVector = engine.compute(0);
        assertThat(currentVector.azimuth).isEqualTo(0.7f); // Primer would make this positive.
        assertThat(currentVector.elevation).isEqualTo(0f);
        assertThat(currentVector.distance).isEqualTo(1.3f);

        engine.add(SphericalVector.fromRadians(0f, 0, 1.3f).toAnnotated(), 0);

        // Look down.
        poseSource.changePose(
                new Pose(Vector3.ORIGIN, Quaternion.yawPitchRoll(0, 1, 0))
        );

        // Generate a new measurement that doesn't have elevation or distance.
        engine.add(
                SphericalVector.fromRadians(0f, 0f, 0f)
                .toAnnotated(true, false, false),
                0);

        // Expect the predicted elevation based on the pose change.  Distance should be unaffected.
        currentVector = engine.compute(0);
        assertClose(currentVector.azimuth, 0.0f);
        assertClose(currentVector.elevation, -1f);
        assertClose(currentVector.distance, 1.3f);

        engine.close();
    }
}
