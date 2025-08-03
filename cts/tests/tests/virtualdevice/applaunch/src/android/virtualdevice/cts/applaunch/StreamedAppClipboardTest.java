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

package android.virtualdevice.cts.applaunch;

import static android.Manifest.permission.ADD_ALWAYS_UNLOCKED_DISPLAY;
import static android.Manifest.permission.READ_CLIPBOARD_IN_BACKGROUND;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CLIPBOARD;
import static android.content.Intent.EXTRA_RESULT_RECEIVER;
import static android.virtualdevice.cts.common.StreamedAppConstants.ACTION_READ;
import static android.virtualdevice.cts.common.StreamedAppConstants.ACTION_WRITE;
import static android.virtualdevice.cts.common.StreamedAppConstants.CLIPBOARD_TEST_ACTIVITY;
import static android.virtualdevice.cts.common.StreamedAppConstants.EXTRA_DEVICE_ID;
import static android.virtualdevice.cts.common.StreamedAppConstants.EXTRA_HAS_CLIP_DATA;
import static android.virtualdevice.cts.common.StreamedAppConstants.EXTRA_CLIP_DATA;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.flags.Flags;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.wm.LockScreenSession;
import android.view.Display;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.FeatureUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Tests for clipboard access on virtual devices.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class StreamedAppClipboardTest {

    private static final ClipData CLIP_DATA = ClipData.newPlainText("label", "Hello World");

    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(3);

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.withAdditionalPermissions(
            ADD_ALWAYS_UNLOCKED_DISPLAY,
            READ_CLIPBOARD_IN_BACKGROUND);

    private final ArrayList<DeviceEnvironment> mDeviceEnvironments = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        // Skip tests on devices that don't support Autofill (such as WearOS)
        assumeTrue(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOFILL));
    }

    @After
    public void tearDown() {
        for (int i = 0; i < mDeviceEnvironments.size(); ++i) {
            mDeviceEnvironments.get(i).close();
        }
    }

    /** The virtual device owner has access to its device's clipboard. */
    @Test
    public void deviceOwnerCanAccessClipboard() {
        DeviceEnvironment virtualDevice = new DeviceEnvironment(DEVICE_POLICY_DEFAULT);
        virtualDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);

        virtualDevice.verifyClipChanged();
        assertThat(virtualDevice.mClipboardManager.hasPrimaryClip()).isTrue();
        verifyClipData(virtualDevice.mClipboardManager.getPrimaryClip());
    }

    /** Activities running on the virtual device can read that device's clipboard. */
    @Test
    public void streamedAppCanReadClipboard() {
        DeviceEnvironment virtualDevice = new DeviceEnvironment(DEVICE_POLICY_DEFAULT);
        virtualDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);

        verifyClipData(virtualDevice.readClipboardFromActivity());
    }

    /** Activities running on the virtual device can write that device's clipboard. */
    @Test
    public void streamedAppCanWriteClipboard() {
        DeviceEnvironment virtualDevice = new DeviceEnvironment(DEVICE_POLICY_DEFAULT);
        virtualDevice.writeClipboardFromActivity();

        virtualDevice.verifyClipChanged();
        assertThat(virtualDevice.mClipboardManager.hasPrimaryClip()).isTrue();
        verifyClipData(virtualDevice.mClipboardManager.getPrimaryClip());
    }

    /**
     * Activities running on the virtual device can read that device's clipboard even when the
     * host device is locked.
     */
    @Test
    public void streamedAppCanReadClipboard_hostDeviceIsLocked() {
        DeviceEnvironment virtualDevice = new DeviceEnvironment(DEVICE_POLICY_DEFAULT);
        virtualDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);

        try (SecureLockScreenSession unused = new SecureLockScreenSession()) {
            verifyClipData(virtualDevice.readClipboardFromActivity());
        }
    }

    /**
     * Activities running on the virtual device can write that device's clipboard even when the
     * host device is locked.
     */
    @Test
    public void streamedAppCanWriteClipboard_hostDeviceIsLocked() {
        DeviceEnvironment virtualDevice = new DeviceEnvironment(DEVICE_POLICY_DEFAULT);

        try (SecureLockScreenSession unused = new SecureLockScreenSession()) {
            virtualDevice.writeClipboardFromActivity();

            virtualDevice.verifyClipChanged();
            assertThat(virtualDevice.mClipboardManager.hasPrimaryClip()).isTrue();
            verifyClipData(virtualDevice.mClipboardManager.getPrimaryClip());
        }
    }

    /** Virtual and default device's clipboards are isolated. */
    @Test
    public void clipboardsAreIsolated() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice = new DeviceEnvironment(DEVICE_POLICY_DEFAULT);

        defaultDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);
        assertThat(virtualDevice.mClipboardManager.hasPrimaryClip()).isFalse();

        defaultDevice.mClipboardManager.clearPrimaryClip();

        virtualDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);
        assertThat(defaultDevice.mClipboardManager.hasPrimaryClip()).isFalse();
    }

    /** Activities running on the virtual device cannot read default device's clipboard. */
    @Test
    public void streamedAppCanNotReadDefaultDeviceClipboard() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        defaultDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);

        DeviceEnvironment virtualDevice = new DeviceEnvironment(DEVICE_POLICY_DEFAULT);

        assertThat(virtualDevice.readClipboardFromActivity(defaultDevice.mDeviceId)).isNull();
    }

    /** Activities running on the virtual device cannot write default device's clipboard. */
    @Test
    public void streamedAppCanNotWriteDefaultDeviceClipboard() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice = new DeviceEnvironment(DEVICE_POLICY_DEFAULT);

        virtualDevice.writeClipboardFromActivity(defaultDevice.mDeviceId);

        assertThat(defaultDevice.mClipboardManager.hasPrimaryClip()).isFalse();
    }


    /** Virtual and default device's clipboards are the same if the policy is custom. */
    @RequiresFlagsEnabled(Flags.FLAG_CROSS_DEVICE_CLIPBOARD)
    @Test
    public void customPolicy_clipboardsAreShared() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice = new DeviceEnvironment(DEVICE_POLICY_CUSTOM);

        defaultDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);
        assertThat(virtualDevice.mClipboardManager.hasPrimaryClip()).isTrue();
        verifyClipData(virtualDevice.mClipboardManager.getPrimaryClip());

        defaultDevice.mClipboardManager.clearPrimaryClip();
        assertThat(virtualDevice.mClipboardManager.hasPrimaryClip()).isFalse();

        virtualDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);
        assertThat(defaultDevice.mClipboardManager.hasPrimaryClip()).isTrue();
        verifyClipData(defaultDevice.mClipboardManager.getPrimaryClip());

        virtualDevice.mClipboardManager.clearPrimaryClip();
        assertThat(defaultDevice.mClipboardManager.hasPrimaryClip()).isFalse();
    }

    /**
     * Activities running on the virtual device can read default device's clipboard if the virtual
     * device clipboard policy is custom.
     */
    @RequiresFlagsEnabled(Flags.FLAG_CROSS_DEVICE_CLIPBOARD)
    @Test
    public void customPolicy_streamedAppReadsFromDefaultDeviceClipboard() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice = new DeviceEnvironment(DEVICE_POLICY_CUSTOM);

        defaultDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);

        verifyClipData(virtualDevice.readClipboardFromActivity(virtualDevice.mDeviceId));
    }

    /**
     * Activities running on the virtual device can write default device's clipboard if the virtual
     * device clipboard policy is custom.
     */
    @RequiresFlagsEnabled(Flags.FLAG_CROSS_DEVICE_CLIPBOARD)
    @Test
    public void customPolicy_streamedAppWritesToDefaultDeviceClipboard() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice = new DeviceEnvironment(DEVICE_POLICY_CUSTOM);

        virtualDevice.writeClipboardFromActivity(virtualDevice.mDeviceId);

        defaultDevice.verifyClipChanged();
        assertThat(defaultDevice.mClipboardManager.hasPrimaryClip()).isTrue();
        verifyClipData(defaultDevice.mClipboardManager.getPrimaryClip());
    }

    /** Activities running on the virtual device cannot read another virtual device's clipboard. */
    @Test
    public void streamedAppCanNotReadAnotherVirtualDeviceClipboard() {
        DeviceEnvironment virtualDevice = new DeviceEnvironment(DEVICE_POLICY_DEFAULT);
        DeviceEnvironment anotherVirtualDevice = new DeviceEnvironment(DEVICE_POLICY_DEFAULT);
        anotherVirtualDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);

        assertThat(virtualDevice.readClipboardFromActivity(anotherVirtualDevice.mDeviceId))
                .isNull();
    }

    /** Activities running on the virtual device cannot write another virtual device's clipboard. */
    @Test
    public void streamedAppCanNotWriteAnotherVirtualDeviceClipboard() {
        DeviceEnvironment virtualDevice = new DeviceEnvironment(DEVICE_POLICY_DEFAULT);
        DeviceEnvironment anotherVirtualDevice = new DeviceEnvironment(DEVICE_POLICY_DEFAULT);

        virtualDevice.writeClipboardFromActivity(anotherVirtualDevice.mDeviceId);

        assertThat(anotherVirtualDevice.mClipboardManager.hasPrimaryClip()).isFalse();
    }

    /** Activities running on the default device cannot read virtual device's clipboard. */
    @Test
    public void defaultDeviceAppCanNotReadVirtualDeviceClipboard() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice = new DeviceEnvironment(DEVICE_POLICY_DEFAULT);
        virtualDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);

        assertThat(defaultDevice.readClipboardFromActivity(virtualDevice.mDeviceId)).isNull();
    }

    /** Activities running on the default device cannot write virtual device's clipboard. */
    @Test
    public void defaultDeviceAppCanNotWriteVirtualDeviceClipboard() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice = new DeviceEnvironment(DEVICE_POLICY_DEFAULT);

        defaultDevice.writeClipboardFromActivity(virtualDevice.mDeviceId);

        assertThat(virtualDevice.mClipboardManager.hasPrimaryClip()).isFalse();
    }

    /**
     * Activities running on the default device can read virtual device's clipboard if the virtual
     * device clipboard policy is custom.
     */
    @RequiresFlagsEnabled(Flags.FLAG_CROSS_DEVICE_CLIPBOARD)
    @Test
    public void customPolicy_defaultDeviceAppCanReadVirtualDeviceClipboard() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice = new DeviceEnvironment(DEVICE_POLICY_CUSTOM);

        virtualDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);

        verifyClipData(defaultDevice.readClipboardFromActivity(virtualDevice.mDeviceId));
    }

    /**
     * Activities running on the default device can write virtual device's clipboard if the virtual
     * device clipboard policy is custom.
     */
    @RequiresFlagsEnabled(Flags.FLAG_CROSS_DEVICE_CLIPBOARD)
    @Test
    public void customPolicy_defaultDeviceAppCanWriteVirtualDeviceClipboard() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice = new DeviceEnvironment(DEVICE_POLICY_CUSTOM);

        defaultDevice.writeClipboardFromActivity(virtualDevice.mDeviceId);

        virtualDevice.verifyClipChanged();
        assertThat(virtualDevice.mClipboardManager.hasPrimaryClip()).isTrue();
        verifyClipData(virtualDevice.mClipboardManager.getPrimaryClip());
    }

    private void verifyClipData(ClipData actual) {
        assertThat(actual).isNotNull();
        assertThat(actual.getItemCount()).isEqualTo(1);
        assertThat(actual.getDescription().getLabel().toString()).isEqualTo(
                CLIP_DATA.getDescription().getLabel().toString());
        assertThat(actual.getItemAt(0).getText().toString())
                .isEqualTo(CLIP_DATA.getItemAt(0).getText().toString());
    }

    /**
     * Helper wrapper of the test environment for a single device, default or virtual.
     */
    private final class DeviceEnvironment {

        private final Context mContext;
        private ClipboardManager mClipboardManager;
        private final int mDisplayId;
        private final int mDeviceId;
        @Mock
        private ClipboardManager.OnPrimaryClipChangedListener mOnPrimaryClipChangedListener;
        @Mock
        private RemoteCallback.OnResultListener mResultReceiver;
        private RemoteCallback mRemoteCallback;

        DeviceEnvironment() {
            MockitoAnnotations.initMocks(this);
            mContext = getApplicationContext();
            mDisplayId = Display.DEFAULT_DISPLAY;
            mDeviceId = Context.DEVICE_ID_DEFAULT;
            initialize();
        }

        DeviceEnvironment(@VirtualDeviceParams.DevicePolicy int clipboardPolicy) {
            MockitoAnnotations.initMocks(this);
            VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                    new VirtualDeviceParams.Builder()
                            .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                            .setDevicePolicy(POLICY_TYPE_CLIPBOARD, clipboardPolicy)
                            .build());
            VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(
                    virtualDevice,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
            mDisplayId = virtualDisplay.getDisplay().getDisplayId();
            mDeviceId = virtualDevice.getDeviceId();
            mContext = getApplicationContext().createDeviceContext(mDeviceId);
            initialize();
        }

        private void initialize() {
            mRemoteCallback = new RemoteCallback(mResultReceiver);
            mDeviceEnvironments.add(this);
            mClipboardManager = mContext.getSystemService(ClipboardManager.class);
            mClipboardManager.clearPrimaryClip();
            assertThat(mClipboardManager.hasPrimaryClip()).isFalse();
            mClipboardManager.addPrimaryClipChangedListener(mOnPrimaryClipChangedListener);
        }

        private void close() {
            mClipboardManager.removePrimaryClipChangedListener(mOnPrimaryClipChangedListener);
        }

        public ClipData readClipboardFromActivity() {
            return readClipboardFromActivity(mDeviceId);
        }

        public ClipData readClipboardFromActivity(int deviceId) {
            launchTestActivity(ACTION_READ, deviceId);

            ArgumentCaptor<Bundle> bundle = ArgumentCaptor.forClass(Bundle.class);
            verify(mResultReceiver, timeout(TIMEOUT_MILLIS)).onResult(bundle.capture());
            ClipData clipData = bundle.getValue().getParcelable(EXTRA_CLIP_DATA, ClipData.class);
            if (clipData == null) {
                assertThat(bundle.getValue().getBoolean(EXTRA_HAS_CLIP_DATA, true)).isFalse();
            } else {
                assertThat(bundle.getValue().getBoolean(EXTRA_HAS_CLIP_DATA, false)).isTrue();
            }
            return clipData;
        }

        public void writeClipboardFromActivity() {
            launchTestActivity(ACTION_WRITE, mDeviceId);
        }

        public void writeClipboardFromActivity(int deviceId) {
            launchTestActivity(ACTION_WRITE, deviceId);
        }

        private void launchTestActivity(String action, int deviceId) {
            final Intent intent = new Intent(action)
                    .setComponent(CLIPBOARD_TEST_ACTIVITY)
                    .putExtra(EXTRA_RESULT_RECEIVER, mRemoteCallback)
                    .putExtra(EXTRA_DEVICE_ID, deviceId)
                    .putExtra(EXTRA_CLIP_DATA, CLIP_DATA)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            mRule.sendIntentToDisplay(intent, mDisplayId);
        }

        private void verifyClipChanged() {
            // We use atLeastOnce for onPrimaryClipChanged because it can fire more than once as
            // text classification results on the clipboard content become available.
            verify(mOnPrimaryClipChangedListener, timeout(TIMEOUT_MILLIS).atLeastOnce())
                    .onPrimaryClipChanged();
        }
    }

    /**
     * A secure lock screen session that cleans up after itself.
     */
    private class SecureLockScreenSession extends LockScreenSession {
        SecureLockScreenSession() {
            super(InstrumentationRegistry.getInstrumentation(), mRule.getWmState());
            assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN));
            setLockCredential().gotoKeyguard();
            mRule.getWmState().assertKeyguardShowingAndNotOccluded();
        }

        @Override
        public void close() {
            mRule.runWithTemporaryPermission(() -> unlockDevice().enterAndConfirmLockCredential());
            mRule.getWmState().waitAndAssertKeyguardGone();
            super.close();
        }
    }
}
