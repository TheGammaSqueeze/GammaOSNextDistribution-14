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

package android.virtualdevice.cts.core;

import static android.Manifest.permission.ADD_ALWAYS_UNLOCKED_DISPLAY;
import static android.Manifest.permission.CAPTURE_VIDEO_OUTPUT;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.flags.Flags;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.Display;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = " cannot be accessed by instant apps")
public class VirtualDisplayTest {

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.withAdditionalPermissions(
            ADD_ALWAYS_UNLOCKED_DISPLAY);

    private VirtualDeviceManager mVirtualDeviceManager;
    private VirtualDevice mVirtualDevice;

    @Before
    public void setUp() {
        mVirtualDeviceManager =
                getApplicationContext().getSystemService(VirtualDeviceManager.class);
        mVirtualDevice = mRule.createManagedVirtualDevice();
    }

    @Test
    public void createVirtualDisplay_shouldSucceed() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice);
        verifyDisplay(virtualDisplay);
    }

    @Test
    public void createVirtualDisplay_deprecatedOverload_shouldSucceed() {
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                /* width= */ VirtualDeviceRule.DEFAULT_VIRTUAL_DISPLAY_WIDTH,
                /* height= */ VirtualDeviceRule.DEFAULT_VIRTUAL_DISPLAY_HEIGHT,
                /* densityDpi= */ VirtualDeviceRule.DEFAULT_VIRTUAL_DISPLAY_DPI,
                /* surface= */ null,
                /* flags= */ 0,
                /* executor= */null,
                /* callback= */null);

        assertThat(virtualDisplay).isNotNull();
        try {
            verifyDisplay(virtualDisplay);
        } finally {
            virtualDisplay.release();
            mRule.assertDisplayDoesNotExist(virtualDisplay.getDisplay().getDisplayId());
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_CONSISTENT_DISPLAY_FLAGS)
    @Test
    public void createVirtualDisplay_defaultVirtualDisplayFlags() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice);

        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(display.getFlags()).isEqualTo(
                Display.FLAG_PRIVATE | Display.FLAG_TOUCH_FEEDBACK_DISABLED);
        // Private displays always destroy their content on removal
        assertThat(display.getRemoveMode()).isEqualTo(Display.REMOVE_MODE_DESTROY_CONTENT);
    }

    @RequiresFlagsDisabled(Flags.FLAG_CONSISTENT_DISPLAY_FLAGS)
    @Test
    public void createVirtualDisplay_defaultVirtualDisplayFlags_compat() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice);

        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(display.getFlags()).isEqualTo(Display.FLAG_ROTATES_WITH_CONTENT
                | Display.FLAG_TOUCH_FEEDBACK_DISABLED);
        assertThat(display.getRemoveMode()).isEqualTo(Display.REMOVE_MODE_DESTROY_CONTENT);
    }

    /**
     * Tests that a virtual device is not allowed create a virtual display with just
     * VIRTUAL_DISPLAY_FLAG_PUBLIC flag if screen mirroring is disabled, as DisplayManagerService
     * tries to create an auto-mirror display by default for public virtual displays.
     */
    @RequiresFlagsDisabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @RequiresFlagsEnabled(Flags.FLAG_CONSISTENT_DISPLAY_FLAGS)
    @Test
    public void createVirtualDisplay_public_throwsException() {
        // Try creating public display without CAPTURE_VIDEO_OUTPUT permission.
        assertThrows(SecurityException.class,
                () -> mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC));
    }

    /**
     * Tests that a virtual device is not allowed create a virtual display with
     * VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR flag if screen mirroring is disabled.
     */
    @RequiresFlagsDisabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @RequiresFlagsEnabled(Flags.FLAG_CONSISTENT_DISPLAY_FLAGS)
    @Test
    public void createVirtualDisplay_autoMirror_throwsException() {
        // Try creating auto-mirror display without CAPTURE_VIDEO_OUTPUT permission.
        assertThrows(SecurityException.class,
                () -> mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR));
    }

    /**
     * Tests that a virtual device is not allowed create a virtual display with
     * VIRTUAL_DISPLAY_FLAG_PUBLIC or VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR flags if screen mirroring is
     * disabled.
     */
    @RequiresFlagsDisabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @RequiresFlagsEnabled(Flags.FLAG_CONSISTENT_DISPLAY_FLAGS)
    @Test
    public void createVirtualDisplay_publicAutoMirror_throwsException() {
        // Try creating public auto-mirror display without CAPTURE_VIDEO_OUTPUT permission.
        assertThrows(SecurityException.class,
                () -> mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR));
    }

    @RequiresFlagsEnabled({
            Flags.FLAG_INTERACTIVE_SCREEN_MIRROR, Flags.FLAG_CONSISTENT_DISPLAY_FLAGS})
    @Test
    public void createVirtualDisplay_public_createsMirrorDisplay() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isTrue();
    }

    @RequiresFlagsEnabled({
            Flags.FLAG_INTERACTIVE_SCREEN_MIRROR, Flags.FLAG_CONSISTENT_DISPLAY_FLAGS})
    @Test
    public void createVirtualDisplay_autoMirror_createsMirrorDisplay() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isTrue();
    }

    @RequiresFlagsEnabled({
            Flags.FLAG_INTERACTIVE_SCREEN_MIRROR, Flags.FLAG_CONSISTENT_DISPLAY_FLAGS})
    @Test
    public void createVirtualDisplay_publicAutoMirror_createsMirrorDisplay() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isTrue();
    }


    @RequiresFlagsEnabled({
            Flags.FLAG_INTERACTIVE_SCREEN_MIRROR, Flags.FLAG_CONSISTENT_DISPLAY_FLAGS})
    @Test
    public void createVirtualDisplay_ownContentOnly_doesNotCreateMirrorDisplay() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isFalse();
    }

    @RequiresFlagsEnabled({
            Flags.FLAG_INTERACTIVE_SCREEN_MIRROR, Flags.FLAG_CONSISTENT_DISPLAY_FLAGS})
    @Test
    public void createVirtualDisplay_autoMirrorAndOwnContentOnly_doesNotCreateMirrorDisplay() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isFalse();
    }

    @RequiresFlagsEnabled({
            Flags.FLAG_INTERACTIVE_SCREEN_MIRROR, Flags.FLAG_CONSISTENT_DISPLAY_FLAGS})
    @Test
    public void createVirtualDisplay_autoMirror_flagAlwaysUnlockedNotSet() {
        VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                        .build());
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(virtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.getFlags() & Display.FLAG_ALWAYS_UNLOCKED).isEqualTo(0);
    }

    @RequiresFlagsEnabled({
            Flags.FLAG_INTERACTIVE_SCREEN_MIRROR, Flags.FLAG_CONSISTENT_DISPLAY_FLAGS})
    @Test
    public void createVirtualDisplay_public_flagAlwaysUnlockedNotSet() {
        VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                        .build());
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(virtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.getFlags() & Display.FLAG_ALWAYS_UNLOCKED).isEqualTo(0);
    }

    @RequiresFlagsEnabled({
            Flags.FLAG_INTERACTIVE_SCREEN_MIRROR, Flags.FLAG_CONSISTENT_DISPLAY_FLAGS})
    @Test
    public void createVirtualDisplay_autoMirror_flagPresentationNotSet() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.getFlags() & Display.FLAG_PRESENTATION).isEqualTo(0);
    }

    @RequiresFlagsEnabled({
            Flags.FLAG_INTERACTIVE_SCREEN_MIRROR, Flags.FLAG_CONSISTENT_DISPLAY_FLAGS})
    @Test
    public void createVirtualDisplay_public_flagPresentationNotSet() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.getFlags() & Display.FLAG_PRESENTATION).isEqualTo(0);
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_invalidDisplay_returnsFalse() {
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(Display.INVALID_DISPLAY))
                .isFalse();
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_defaultDisplay_returnsFalse() {
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(Display.DEFAULT_DISPLAY))
                .isFalse();
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_unownedAutoMirrorDisplay_returnsFalse() {
        final VirtualDisplay virtualDisplay = mRule.runWithTemporaryPermission(
                () -> mRule.createManagedUnownedVirtualDisplayWithFlags(
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR),
                CAPTURE_VIDEO_OUTPUT);

        final int displayId = virtualDisplay.getDisplay().getDisplayId();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(displayId)).isFalse();
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_unownedPublicDisplay_returnsFalse() {
        final VirtualDisplay virtualDisplay = mRule.runWithTemporaryPermission(
                () -> mRule.createManagedUnownedVirtualDisplayWithFlags(
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR),
                CAPTURE_VIDEO_OUTPUT);

        final int displayId = virtualDisplay.getDisplay().getDisplayId();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(displayId)).isFalse();
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_unownedPublicAutoMirrorDisplay_returnsFalse() {
        final VirtualDisplay virtualDisplay = mRule.runWithTemporaryPermission(
                () -> mRule.createManagedUnownedVirtualDisplayWithFlags(
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR),
                CAPTURE_VIDEO_OUTPUT);

        final int displayId = virtualDisplay.getDisplay().getDisplayId();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(displayId)).isFalse();
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_unownedDisplay_returnsFalse() {
        VirtualDisplay virtualDisplay = mRule.createManagedUnownedVirtualDisplay();

        final int displayId = virtualDisplay.getDisplay().getDisplayId();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(displayId)).isFalse();
    }

    @Test
    public void createVirtualDisplay_trustedDisplay_shouldSpecifyOwnFocusFlag() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        int displayFlags = display.getFlags();
        assertWithMessage(
                String.format(
                        "Virtual display flags (0x%x) should contain FLAG_TRUSTED",
                        displayFlags))
                .that(displayFlags & Display.FLAG_TRUSTED)
                .isEqualTo(Display.FLAG_TRUSTED);
        assertWithMessage(
                String.format(
                        "Virtual display flags (0x%x) should contain FLAG_OWN_FOCUS",
                        displayFlags))
                .that(displayFlags & Display.FLAG_OWN_FOCUS)
                .isEqualTo(Display.FLAG_OWN_FOCUS);
    }

    @Test
    public void createVirtualDisplay_alwaysUnlocked_shouldSpecifyAlwaysUnlockedFlag() {
        VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                        .build());
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(virtualDevice);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        int displayFlags = display.getFlags();
        assertWithMessage(
                String.format(
                        "Virtual display flags (0x%x) should contain FLAG_ALWAYS_UNLOCKED",
                        displayFlags))
                .that(displayFlags & Display.FLAG_ALWAYS_UNLOCKED)
                .isEqualTo(Display.FLAG_ALWAYS_UNLOCKED);
    }

    @Test
    public void createVirtualDisplay_nullExecutorButNonNullCallback_shouldThrow() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.createVirtualDisplay(
                        VirtualDeviceRule.DEFAULT_VIRTUAL_DISPLAY_CONFIG, /*executor=*/ null,
                        new VirtualDisplay.Callback() {
                        }));
    }

    @Test
    public void virtualDisplay_createAndRemoveSeveralDisplays() {
        ArrayList<VirtualDisplay> displays = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            displays.add(mRule.createManagedVirtualDisplay(mVirtualDevice));
        }

        // Releasing several displays in quick succession should not cause deadlock
        displays.forEach(VirtualDisplay::release);

        for (VirtualDisplay display : displays) {
            mRule.assertDisplayDoesNotExist(display.getDisplay().getDisplayId());
        }
    }

    @Test
    public void virtualDisplay_releasedWhenDeviceIsClosed() {
        ArrayList<VirtualDisplay> displays = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            displays.add(mRule.createManagedVirtualDisplay(mVirtualDevice));
        }

        // Closing the virtual device should automatically release displays.
        mVirtualDevice.close();

        for (VirtualDisplay display : displays) {
            mRule.assertDisplayDoesNotExist(display.getDisplay().getDisplayId());
        }
    }

    @Test
    public void getDeviceIdForDisplayId_returnsCorrectId() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice);

        assertThat(mVirtualDeviceManager.getDeviceIdForDisplayId(
                virtualDisplay.getDisplay().getDisplayId())).isEqualTo(
                mVirtualDevice.getDeviceId());
    }

    @Test
    public void getDeviceIdForDisplayId_returnsDefaultIdForReleasedDisplay() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice);
        virtualDisplay.release();

        mRule.assertDisplayDoesNotExist(virtualDisplay.getDisplay().getDisplayId());
        assertThat(mVirtualDeviceManager.getDeviceIdForDisplayId(
                virtualDisplay.getDisplay().getDisplayId()))
                .isEqualTo(Context.DEVICE_ID_DEFAULT);
    }

    @Test
    public void createAndRelease_isInvalidForReleasedDisplay() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice);

        mVirtualDevice.close();
        mRule.assertDisplayDoesNotExist(virtualDisplay.getDisplay().getDisplayId());

        // Check whether display associated with virtual device is valid.
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isFalse();
    }

    private void verifyDisplay(VirtualDisplay virtualDisplay) {
        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(display.getWidth()).isEqualTo(VirtualDeviceRule.DEFAULT_VIRTUAL_DISPLAY_WIDTH);
        assertThat(display.getHeight()).isEqualTo(VirtualDeviceRule.DEFAULT_VIRTUAL_DISPLAY_HEIGHT);
    }
}
