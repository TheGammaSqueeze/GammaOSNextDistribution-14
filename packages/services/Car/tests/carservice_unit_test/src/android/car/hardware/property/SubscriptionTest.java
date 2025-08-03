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

package android.car.hardware.property;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SubscriptionTest {
    @Test
    public void testBuilder_noUpdateRateSet() {
        Subscription.Builder builder = new Subscription.Builder(/* propertyId= */ 52);
        Subscription option = builder.build();

        assertWithMessage("No update rate set PropertyId").that(option
                        .getPropertyId()).isEqualTo(52);
        assertWithMessage("no update rate set UpdateRateHz").that(option
                        .getUpdateRateHz()).isZero();
        assertWithMessage("Null areaId").that(option.getAreaIds()).isEmpty();
    }

    @Test
    public void testBuilder_withUpdateRateSet() {
        int propertyId = 52;
        Subscription.Builder builder = new Subscription.Builder(propertyId);
        builder.setUpdateRateHz(/* updateRateHz= */ 55.01f);
        Subscription option = builder.build();

        assertWithMessage("Update rate set PropertyId").that(option.getPropertyId())
                .isEqualTo(propertyId);
        assertWithMessage("Update rate set UpdateRateHz").that(option.getUpdateRateHz())
                .isEqualTo(55.01f);
        assertWithMessage("Update rate set AreaIds").that(option.getAreaIds())
                .isEmpty();
    }

    @Test
    public void testAddAreaId_withMultipleAreas() {
        Subscription.Builder builder = new Subscription.Builder(/* propertyId= */ 98);
        builder.setUpdateRateHz(/* updateRateHz= */ 55.01f);
        builder.addAreaId(/* areaId= */ 0x1);
        builder.addAreaId(/* areaId= */ 0x2);
        builder.addAreaId(/* areaId= */ 0x4);
        Subscription option = builder.build();

        assertWithMessage("Multiple areaIds PropertyId").that(option.getPropertyId())
                .isEqualTo(98);
        assertWithMessage("Multiple areaIds UpdateRateHz").that(option.getUpdateRateHz())
                .isEqualTo(55.01f);
        assertWithMessage("Multiple AreaIds").that(option.getAreaIds()).asList()
                .containsExactly(0x1, 0x2, 0x4);
    }

    @Test
    public void testAddAreaId_withOverlappingAreas() {
        Subscription.Builder builder = new Subscription.Builder(/* propertyId= */ 98);
        builder.setUpdateRateHz(/* updateRateHz= */ 55.01f);
        builder.addAreaId(/* areaId= */ 0x1);
        builder.addAreaId(/* areaId= */ 0x2);
        builder.addAreaId(/* areaId= */ 0x2);
        Subscription option = builder.build();

        assertWithMessage("Overlapping areaIds PropertyId").that(option.getPropertyId())
                .isEqualTo(98);
        assertWithMessage("Overlapping areaIds UpdateRateHz").that(option.getUpdateRateHz())
                .isEqualTo(55.01f);
        assertWithMessage("Overlapping AreaIds").that(option.getAreaIds()).asList()
                .containsExactly(0x1, 0x2);
    }

    @Test
    public void testSetUpdateRateHz_throws() throws Exception {
        Subscription.Builder builder = new Subscription.Builder(/* propertyId= */ 98);
        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> builder.setUpdateRateHz(150.0f));

        assertWithMessage("150Hz updateRateHz").that(thrown).hasMessageThat()
                .contains("Update rate should be in the range [0.0f, 100.0f]");
    }

    @Test
    public void testSetRateUI() {
        int propertyId = 4231;
        Subscription.Builder builder = new Subscription.Builder(propertyId);
        builder.setUpdateRateUi();
        Subscription option = builder.build();

        assertWithMessage("setUI propertyId").that(option.getPropertyId())
                .isEqualTo(propertyId);
        assertWithMessage("SetUi UpdateRateHz").that(option.getUpdateRateHz())
                .isEqualTo(CarPropertyManager.SENSOR_RATE_UI);
        assertWithMessage("SetUi AreaIds").that(option.getAreaIds())
                .isEmpty();
    }

    @Test
    public void testSetRateNormal() {
        int propertyId = 12;
        Subscription.Builder builder = new Subscription.Builder(propertyId);
        builder.setUpdateRateNormal();
        Subscription option = builder.build();

        assertWithMessage("Set update rate normal PropertyId").that(option
                        .getPropertyId()).isEqualTo(propertyId);
        assertWithMessage("Normal sensor rate").that(option.getUpdateRateHz())
                .isEqualTo(CarPropertyManager.SENSOR_RATE_NORMAL);
        assertWithMessage("Set update rate normal AreaIds").that(option.getAreaIds())
                .isEmpty();
    }

    @Test
    public void testSetRateFast() {
        int propertyId = 33;
        Subscription.Builder builder = new Subscription.Builder(propertyId);
        builder.setUpdateRateFast();
        Subscription option = builder.build();

        assertWithMessage("Set update rate fast PropertyId").that(option.getPropertyId())
                .isEqualTo(propertyId);
        assertWithMessage("Sensor Rate Fast").that(option.getUpdateRateHz())
                .isEqualTo(CarPropertyManager.SENSOR_RATE_FAST);
        assertWithMessage("Set update rate fast AreaIds").that(option.getAreaIds())
                .isEmpty();
    }

    @Test
    public void testSetRateFastest() {
        int propertyId = 24;
        Subscription.Builder builder = new Subscription.Builder(propertyId);
        builder.setUpdateRateFastest();
        Subscription option = builder.build();

        assertWithMessage("Set update rate fastest PropertyId").that(option
                        .getPropertyId()).isEqualTo(propertyId);
        assertWithMessage("Sensor Rate Fastest").that(option.getUpdateRateHz())
                .isEqualTo(CarPropertyManager.SENSOR_RATE_FASTEST);
        assertWithMessage("Set update rate fastest AreaIds").that(option.getAreaIds())
                .isEmpty();
    }

    @Test
    public void testSetResolution() {
        int propertyId = 24;
        float resolution = 0.01f;
        Subscription.Builder builder = new Subscription.Builder(propertyId);
        builder.setResolution(resolution);
        Subscription option = builder.build();

        assertWithMessage("Resolution is set correctly").that(option
                .getResolution()).isEqualTo(resolution);
    }

    @Test
    public void testSetResolutionInvalid() {
        int propertyId = 24;
        float resolution = 0.02f;
        Subscription.Builder builder = new Subscription.Builder(propertyId);
        assertThrows(IllegalArgumentException.class, () -> builder.setResolution(resolution));
    }

    @Test
    public void testSubscripTionOptionBuildTwice() {
        int propertyId = 24;
        Subscription.Builder builder = new Subscription.Builder(propertyId);
        builder.build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> builder.build());
        assertWithMessage("Built twice").that(thrown).hasMessageThat()
                .contains("This Builder should not be reused. Use a new Builder instance instead");
    }
}
