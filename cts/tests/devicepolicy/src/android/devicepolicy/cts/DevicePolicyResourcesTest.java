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

package android.devicepolicy.cts;

import static android.Manifest.permission.UPDATE_DEVICE_MANAGEMENT_RESOURCES;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.DevicePolicyDrawableResource;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyResources;
import android.app.admin.DevicePolicyStringResource;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.function.Supplier;

// TODO(b/208084779): Add more tests to cover calling from other packages after adding support for
//  the new APIs in the test sdk.
@RunWith(BedsteadJUnit4.class)
public class DevicePolicyResourcesTest {
    private static final String TAG = "DevicePolicyResourcesTests";

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final DevicePolicyManager sDpm =
            sContext.getSystemService(DevicePolicyManager.class);

    private static final String UPDATABLE_DRAWABLE_ID_1 = "UPDATABLE_DRAWABLE_ID_1";
    private static final String UPDATABLE_DRAWABLE_ID_2 = "UPDATABLE_DRAWABLE_ID_2";
    private static final String DRAWABLE_STYLE_1 = "DRAWABLE_STYLE_1";
    private static final String DRAWABLE_STYLE_2 = "DRAWABLE_STYLE_2";
    private static final String DRAWABLE_SOURCE_1 = "DRAWABLE_SOURCE_1";
    private static final String DRAWABLE_SOURCE_2 = "DRAWABLE_SOURCE_2";

    private static final String UPDATABLE_STRING_ID_1 = "UPDATABLE_STRING_ID_1";
    private static final String UPDATABLE_STRING_ID_2 = "UPDATABLE_STRING_ID_2";

    private static final int INVALID_RESOURCE_ID = -1;

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @After
    public void tearDown() {
        resetAllResources();
    }

    @Before
    public void setup() {
        resetAllResources();
    }

    private void resetAllResources() {
        TestApis.devicePolicy().resources().drawables().reset(
                UPDATABLE_DRAWABLE_ID_1, UPDATABLE_DRAWABLE_ID_2);
        TestApis.devicePolicy().resources().strings().reset(
                UPDATABLE_STRING_ID_1, UPDATABLE_STRING_ID_2);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureDoesNotHavePermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_withoutRequiredPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                sDpm.getResources().setDrawables(createDrawable(
                        UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1)));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_withRequiredPermission_doesNotThrowSecurityException() {
        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_updatesCorrectUpdatableDrawable() {
        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        Drawable drawable = sDpm.getResources().getDrawable(
                UPDATABLE_DRAWABLE_ID_1,
                DRAWABLE_STYLE_1,
                /* default= */ () -> null);
        assertThat(areSameDrawables(drawable, sContext.getDrawable(R.drawable.test_drawable_1)))
                .isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_updatesCurrentlyUpdatedDrawable() {
        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_2));

        Drawable drawable = sDpm.getResources().getDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, /* default= */ () -> null);
        assertThat(areSameDrawables(drawable, sContext.getDrawable(R.drawable.test_drawable_2)))
                .isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_doesNotUpdateOtherUpdatableDrawables() {
        Drawable defaultDrawable = sContext.getDrawable(R.drawable.test_drawable_2);
        sDpm.getResources().resetDrawables(Set.of(UPDATABLE_DRAWABLE_ID_2));

        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        Drawable drawable = sDpm.getResources().getDrawable(
                UPDATABLE_DRAWABLE_ID_2, DRAWABLE_STYLE_1, /* default= */ () -> defaultDrawable);
        assertThat(drawable).isEqualTo(defaultDrawable);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_updateForSource_updatesCorrectly() {
        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        sDpm.getResources().setDrawables(createDrawableForSource(
                DRAWABLE_SOURCE_1, UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1,
                R.drawable.test_drawable_2));

        Drawable drawable = sDpm.getResources().getDrawable(
                UPDATABLE_DRAWABLE_ID_1,
                DRAWABLE_STYLE_1,
                DRAWABLE_SOURCE_1,
                /* default= */ () -> null);
        assertThat(areSameDrawables(drawable, sContext.getDrawable(R.drawable.test_drawable_2)))
                .isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_updateForMultipleSources_updatesCorrectly() {
        sDpm.getResources().setDrawables(createDrawableForSource(
                DRAWABLE_SOURCE_1, UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1,
                R.drawable.test_drawable_1));
        sDpm.getResources().setDrawables(createDrawableForSource(
                DRAWABLE_SOURCE_2, UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1,
                R.drawable.test_drawable_2));

        Drawable drawable1 = sDpm.getResources().getDrawable(
                UPDATABLE_DRAWABLE_ID_1,
                DRAWABLE_STYLE_1,
                DRAWABLE_SOURCE_1,
                /* default= */ () -> null);
        assertThat(areSameDrawables(drawable1, sContext.getDrawable(R.drawable.test_drawable_1)))
                .isTrue();
        Drawable drawable2 = sDpm.getResources().getDrawable(
                UPDATABLE_DRAWABLE_ID_1,
                DRAWABLE_STYLE_1,
                DRAWABLE_SOURCE_2,
                /* default= */ () -> null);
        assertThat(areSameDrawables(drawable2, sContext.getDrawable(R.drawable.test_drawable_2)))
                .isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_updateForSource_returnsGenericForUndefinedSource() {
        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        sDpm.getResources().setDrawables(createDrawableForSource(
                DRAWABLE_SOURCE_1, UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1,
                R.drawable.test_drawable_2));

        Drawable drawable = sDpm.getResources().getDrawable(
                UPDATABLE_DRAWABLE_ID_1,
                DRAWABLE_STYLE_1,
                /* default= */ () -> null);
        assertThat(areSameDrawables(drawable, sContext.getDrawable(R.drawable.test_drawable_1)))
                .isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_updateForSource_returnsGenericForUndefinedStyle() {
        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        sDpm.getResources().setDrawables(createDrawableForSource(
                DRAWABLE_SOURCE_1, UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_2,
                R.drawable.test_drawable_2));

        Drawable drawable = sDpm.getResources().getDrawable(
                UPDATABLE_DRAWABLE_ID_1,
                DRAWABLE_STYLE_1,
                DRAWABLE_SOURCE_1,
                /* default= */ () -> null);
        assertThat(areSameDrawables(drawable, sContext.getDrawable(R.drawable.test_drawable_1)))
                .isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_drawableChangedFromNull_sendsBroadcast() {
        sDpm.getResources().resetDrawables(Set.of(UPDATABLE_DRAWABLE_ID_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_drawableChangedFromOtherDrawable_sendsBroadcast() {
        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_2));

        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    @Ignore("b/208237942")
    public void setDrawables_drawableNotChanged_doesNotSendBroadcast() {
        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        assertThat(broadcastReceiver.awaitForBroadcast()).isNull();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureDoesNotHavePermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetDrawables_withoutRequiredPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> sDpm.getResources().resetDrawables(
                Set.of(UPDATABLE_DRAWABLE_ID_1)));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetDrawables_withRequiredPermission_doesNotThrowSecurityException() {
        sDpm.getResources().resetDrawables(Set.of(UPDATABLE_DRAWABLE_ID_1));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetDrawables_removesPreviouslySetDrawables() {
        Drawable defaultDrawable = sContext.getDrawable(R.drawable.test_drawable_2);
        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        sDpm.getResources().resetDrawables(Set.of(UPDATABLE_DRAWABLE_ID_1));

        Drawable drawable = sDpm.getResources().getDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, /* default= */ () -> defaultDrawable);
        assertThat(drawable).isEqualTo(defaultDrawable);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetDrawables_doesNotResetOtherSetDrawables() {
        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));
        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_2, DRAWABLE_STYLE_1, R.drawable.test_drawable_2));

        sDpm.getResources().resetDrawables(Set.of(UPDATABLE_DRAWABLE_ID_1));

        Drawable drawable = sDpm.getResources().getDrawable(
                UPDATABLE_DRAWABLE_ID_2, DRAWABLE_STYLE_1, /* default= */ () -> null);
        assertThat(areSameDrawables(drawable, sContext.getDrawable(R.drawable.test_drawable_2)))
                .isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetDrawables_drawableChanged_sendsBroadcast() {
        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.getResources().resetDrawables(Set.of(UPDATABLE_DRAWABLE_ID_1));

        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    @Ignore("b/208237942")
    public void resetDrawables_drawableNotChanged_doesNotSendBroadcast() {
        sDpm.getResources().resetDrawables(Set.of(UPDATABLE_DRAWABLE_ID_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.getResources().resetDrawables(Set.of(UPDATABLE_DRAWABLE_ID_1));

        assertThat(broadcastReceiver.awaitForBroadcast()).isNull();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getDrawable_drawableIsSet_returnsUpdatedDrawable() {
        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        Drawable drawable = sDpm.getResources().getDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, /* default= */ () -> null);

        assertThat(areSameDrawables(drawable, sContext.getDrawable(R.drawable.test_drawable_1)))
                .isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getDrawable_drawableIsNotSet_returnsDefaultDrawable() {
        Drawable defaultDrawable = sContext.getDrawable(R.drawable.test_drawable_1);
        sDpm.getResources().resetDrawables(Set.of(UPDATABLE_DRAWABLE_ID_1));

        Drawable drawable = sDpm.getResources().getDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, /* default= */ () -> defaultDrawable);

        assertThat(drawable).isEqualTo(defaultDrawable);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getDrawable_defaultLoaderIsNull_throwsException() {
        sDpm.getResources().resetDrawables(Set.of(UPDATABLE_DRAWABLE_ID_1));

        assertThrows(NullPointerException.class, () -> sDpm.getResources().getDrawable(
                UPDATABLE_DRAWABLE_ID_1,
                DRAWABLE_STYLE_1,
                /* default= */ (Supplier<Drawable>) null));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getDrawable_defaultLoaderReturnsNull_returnsNull() {
        sDpm.getResources().resetDrawables(Set.of(UPDATABLE_DRAWABLE_ID_1));

        assertThat(sDpm.getResources().getDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, /* default= */ () -> null)).isNull();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getDrawableForDensity_drawableIsSet_returnsUpdatedDrawable() {
        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        Drawable drawable = sDpm.getResources().getDrawableForDensity(
                UPDATABLE_DRAWABLE_ID_1,
                DRAWABLE_STYLE_1,
                /* density= */ 0,
                /* default= */ () -> null);

        assertThat(areSameDrawables(drawable, sContext.getDrawable(R.drawable.test_drawable_1)))
                .isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getDrawableForDensity_drawableIsNotSet_returnsDefaultDrawable() {
        Drawable defaultDrawable = sContext.getDrawable(R.drawable.test_drawable_1);
        sDpm.getResources().resetDrawables(Set.of(UPDATABLE_DRAWABLE_ID_1));

        Drawable drawable = sDpm.getResources().getDrawableForDensity(
                UPDATABLE_DRAWABLE_ID_1,
                DRAWABLE_STYLE_1,
                /* density= */ 0,
                /* default= */ () -> defaultDrawable);

        assertThat(drawable).isEqualTo(defaultDrawable);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getDrawable_drawableIsSet_returnsUpdatedIcon() {
        sDpm.getResources().setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        Icon icon = sDpm.getResources().getDrawableAsIcon(
                UPDATABLE_DRAWABLE_ID_1,
                DRAWABLE_STYLE_1,
                /* default= */ Icon.createWithResource(sContext, R.drawable.test_drawable_2));

        assertThat(icon.getResId()).isEqualTo(R.drawable.test_drawable_1);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getDrawable_drawableIsNotSet_returnsDefaultIcon() {
        Icon defaultIcon = Icon.createWithResource(sContext, R.drawable.test_drawable_2);
        sDpm.getResources().resetDrawables(Set.of(UPDATABLE_DRAWABLE_ID_1));

        Icon icon = sDpm.getResources().getDrawableAsIcon(
                UPDATABLE_DRAWABLE_ID_1,
                DRAWABLE_STYLE_1,
                defaultIcon);

        assertThat(icon).isEqualTo(defaultIcon);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void constructDevicePolicyDrawableResource_withNonExistentDrawable_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> new DevicePolicyDrawableResource(
                sContext, UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, INVALID_RESOURCE_ID));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void constructDevicePolicyDrawableResource_withNonDrawableResource_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> new DevicePolicyDrawableResource(
                sContext, UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.string.test_string_1));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getDrawableId_returnsCorrectValue() {
        DevicePolicyDrawableResource resource = new DevicePolicyDrawableResource(
                sContext, UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1);

        assertThat(resource.getDrawableId()).isEqualTo(UPDATABLE_DRAWABLE_ID_1);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getDrawableStyle_returnsCorrectValue() {
        DevicePolicyDrawableResource resource = new DevicePolicyDrawableResource(
                sContext, UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1);

        assertThat(resource.getDrawableStyle()).isEqualTo(DRAWABLE_STYLE_1);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getDrawableStyle_sourceNotSpecified_returnsUndefined() {
        DevicePolicyDrawableResource resource = new DevicePolicyDrawableResource(
                sContext, UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1);

        assertThat(resource.getDrawableSource()).isEqualTo(
                DevicePolicyResources.UNDEFINED);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getDrawableStyle_sourceSpecified_returnsCorrectValue() {
        DevicePolicyDrawableResource resource = new DevicePolicyDrawableResource(
                sContext, UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, DRAWABLE_SOURCE_1,
                R.drawable.test_drawable_1);

        assertThat(resource.getDrawableSource()).isEqualTo(DRAWABLE_SOURCE_1);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void devicePolicyDrawableResource_getResourceIdInCallingPackage_returnsCorrectValue() {
        DevicePolicyDrawableResource resource = new DevicePolicyDrawableResource(
                sContext, UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1);

        assertThat(resource.getResourceIdInCallingPackage()).isEqualTo(R.drawable.test_drawable_1);
    }

    // TODO(b/16348282): extract to a common place to make it reusable.
    private static boolean areSameDrawables(Drawable drawable1, Drawable drawable2) {
        return drawable1 == drawable2 || getBitmap(drawable1).sameAs(getBitmap(drawable2));
    }

    private static Bitmap getBitmap(Drawable drawable) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        // Some drawables have no intrinsic width - e.g. solid colours.
        if (width <= 0) {
            width = 1;
        }
        if (height <= 0) {
            height = 1;
        }
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return result;
    }

    private Set<DevicePolicyDrawableResource> createDrawable(
            String updatableDrawableId, String style, int resourceId) {
        return Set.of(new DevicePolicyDrawableResource(
                sContext, updatableDrawableId, style, resourceId));
    }

    private Set<DevicePolicyDrawableResource> createDrawableForSource(
            String source, String updatableDrawableId, String style, int resourceId) {
        return Set.of(new DevicePolicyDrawableResource(
                sContext, updatableDrawableId, style, source, resourceId));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureDoesNotHavePermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setStrings_withoutRequiredPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> sDpm.getResources().setStrings(
                createString(UPDATABLE_STRING_ID_1, R.string.test_string_1)));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setStrings_withRequiredPermission_doesNotThrowSecurityException() {
        sDpm.getResources().setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setStrings_updatesCorrectUpdatableString() {
        sDpm.getResources().setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));

        String string = sDpm.getResources().getString(
                UPDATABLE_STRING_ID_1, /* default= */ () -> null);
        assertThat(string).isEqualTo(sContext.getString(R.string.test_string_1));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setStrings_updatesCurrentlyUpdatedString() {
        sDpm.getResources().setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));

        sDpm.getResources().setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_2));

        String string = sDpm.getResources().getString(
                UPDATABLE_STRING_ID_1, /* default= */ () -> null);
        assertThat(string).isEqualTo(sContext.getString(R.string.test_string_2));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setStrings_doesNotUpdateOtherUpdatableStrings() {
        sDpm.getResources().resetStrings(Set.of(UPDATABLE_STRING_ID_2));
        String defaultString = sContext.getString(R.string.test_string_2);

        sDpm.getResources().setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));

        assertThat(sDpm.getResources().getString(
                UPDATABLE_STRING_ID_2, /* default= */ () -> defaultString))
                .isEqualTo(defaultString);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setStrings_stringChangedFromNull_sendsBroadcast() {
        sDpm.getResources().resetStrings(Set.of(UPDATABLE_STRING_ID_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.getResources().setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));

        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setStrings_stringChangedFromOtherString_sendsBroadcast() {
        sDpm.getResources().setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.getResources().setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_2));

        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    @Ignore("b/208237942")
    public void setStrings_stringNotChanged_doesNotSendBroadcast() {
        sDpm.getResources().setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.getResources().setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));

        assertThat(broadcastReceiver.awaitForBroadcast()).isNull();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureDoesNotHavePermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetStrings_withoutRequiredPermission_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> sDpm.getResources().resetStrings(Set.of(UPDATABLE_STRING_ID_1)));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetStrings_withRequiredPermission_doesNotThrowSecurityException() {
        sDpm.getResources().resetStrings(Set.of(UPDATABLE_STRING_ID_1));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetStrings_removesPreviouslySetStrings() {
        String defaultString = sContext.getString(R.string.test_string_2);
        sDpm.getResources().setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));

        sDpm.getResources().resetStrings(Set.of(UPDATABLE_STRING_ID_1));

        assertThat(sDpm.getResources().getString(
                UPDATABLE_STRING_ID_1, /* default= */ () -> defaultString))
                .isEqualTo(defaultString);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetStrings_doesNotResetOtherSetStrings() {
        sDpm.getResources().setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));
        sDpm.getResources().setStrings(createString(UPDATABLE_STRING_ID_2, R.string.test_string_2));

        sDpm.getResources().resetStrings(Set.of(UPDATABLE_STRING_ID_1));

        String string = sDpm.getResources().getString(
                UPDATABLE_STRING_ID_2, /* default= */ () -> null);
        assertThat(string).isEqualTo(sContext.getString(R.string.test_string_2));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetStrings_stringChanged_sendsBroadcast() {
        sDpm.getResources().setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.getResources().resetStrings(Set.of(UPDATABLE_STRING_ID_1));

        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    @Ignore("b/208237942")
    public void resetStrings_stringNotChanged_doesNotSendBroadcast() {
        sDpm.getResources().resetStrings(Set.of(UPDATABLE_STRING_ID_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.getResources().resetStrings(Set.of(UPDATABLE_STRING_ID_1));

        assertThat(broadcastReceiver.awaitForBroadcast()).isNull();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getString_stringIsSet_returnsUpdatedString() {
        sDpm.getResources().setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));

        String string = sDpm.getResources().getString(
                UPDATABLE_STRING_ID_1, /* default= */ () -> null);

        assertThat(string).isEqualTo(sContext.getString(R.string.test_string_1));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getString_stringIsNotSet_returnsDefaultString() {
        sDpm.getResources().resetStrings(Set.of(UPDATABLE_STRING_ID_1));
        String defaultString = sContext.getString(R.string.test_string_1);

        String string = sDpm.getResources().getString(
                UPDATABLE_STRING_ID_1, /* default= */ () -> defaultString);

        assertThat(string).isEqualTo(defaultString);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getString_defaultLoaderIsNull_throwsException() {
        sDpm.getResources().resetStrings(Set.of(UPDATABLE_STRING_ID_1));

        assertThrows(NullPointerException.class,
                () -> sDpm.getResources().getString(
                        UPDATABLE_STRING_ID_1, /* default= */ null));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getString_defaultLoaderReturnsNull_returnsNull() {
        sDpm.getResources().resetStrings(Set.of(UPDATABLE_STRING_ID_1));

        assertThat(sDpm.getResources().getString(UPDATABLE_STRING_ID_1, /* default= */ () -> null))
                .isNull();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getString_stringWithArgs_returnsFormattedString() {
        sDpm.getResources().setStrings(
                createString(UPDATABLE_STRING_ID_1, R.string.test_string_with_arg));
        String testArg = "test arg";

        String string = sDpm.getResources().getString(
                UPDATABLE_STRING_ID_1, /* default= */ () -> null, testArg);

        assertThat(string).isEqualTo(sContext.getString(R.string.test_string_with_arg, testArg));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void constructDevicePolicyStringResource_withNonExistentString_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> new DevicePolicyStringResource(
                sContext, UPDATABLE_STRING_ID_1, INVALID_RESOURCE_ID));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void constructDevicePolicyStringResource_withNonStringResource_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> new DevicePolicyStringResource(
                sContext, UPDATABLE_STRING_ID_1, R.drawable.test_drawable_2));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getStringId_returnsCorrectValue() {
        DevicePolicyStringResource resource = new  DevicePolicyStringResource(
                sContext, UPDATABLE_STRING_ID_1, R.string.test_string_1);

        assertThat(resource.getStringId()).isEqualTo(UPDATABLE_STRING_ID_1);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void devicePolicyStringResource_getResourceIdInCallingPackage_returnsCorrectValue() {
        DevicePolicyStringResource resource = new  DevicePolicyStringResource(
                sContext, UPDATABLE_STRING_ID_1, R.string.test_string_1);

        assertThat(resource.getResourceIdInCallingPackage()).isEqualTo(R.string.test_string_1);
    }

    private Set<DevicePolicyStringResource> createString(
            String updatableStringId, int resourceId) {
        return Set.of(new DevicePolicyStringResource(
                sContext, updatableStringId, resourceId));
    }
}
