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

import static android.view.WindowManager.DISPLAY_IME_POLICY_FALLBACK_DISPLAY;
import static android.view.WindowManager.DISPLAY_IME_POLICY_HIDE;
import static android.view.WindowManager.DISPLAY_IME_POLICY_LOCAL;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.flags.Flags;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.inputmethodservice.InputMethodService;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.Display;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.virtualdevice.cts.applaunch.AppComponents.EmptyActivity;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Tests for IME behavior on virtual devices.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
@RequiresFlagsEnabled(Flags.FLAG_VDM_CUSTOM_IME)
public class VirtualDeviceImeTest {

    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(5);

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    private final Context mContext = getInstrumentation().getContext();
    private final InputMethodManager mInputMethodManager =
            mContext.getSystemService(InputMethodManager.class);

    private String mDefaultDeviceDefaultImeId;
    private String mVirtualDeviceImeId;
    private int mVirtualDisplayId = Display.INVALID_DISPLAY;

    private interface ImeListener {
        void onShow(int displayId);
    }

    @Mock
    private ImeListener mDefaultDeviceImeListener;
    @Mock
    private ImeListener mVirtualDeviceImeListener;

    @Before
    public void setUp() throws Exception {
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_INPUT_METHODS));

        DefaultDeviceTestIme.sImeListener = mDefaultDeviceImeListener;
        VirtualDeviceTestIme.sImeListener = mVirtualDeviceImeListener;

        mDefaultDeviceDefaultImeId =
                enableTestIme(DefaultDeviceTestIme.class, /* makeDefault= */ true);
        mVirtualDeviceImeId = enableTestIme(VirtualDeviceTestIme.class, /* makeDefault= */ false);
    }

    @After
    public void tearDown() throws Exception {
        DefaultDeviceTestIme.sImeListener = null;
        VirtualDeviceTestIme.sImeListener = null;
        SystemUtil.runShellCommandOrThrow("ime reset");
    }

    /** The virtualDeviceOnly attribute is propagated to InputMethodInfo. */
    @ApiTest(apis = {"android.R.attr#isVirtualDeviceOnly"})
    @Test
    public void virtualDeviceOnlyIme_reflectedInInputMethodInfo() {
        final InputMethodInfo virtualDeviceImi =
                getInputMethodInfo(VirtualDeviceTestIme.class.getName());

        assertThat(virtualDeviceImi).isNotNull();
        assertThat(virtualDeviceImi.isVirtualDeviceOnly()).isTrue();

        final InputMethodInfo defaultDeviceImi =
                getInputMethodInfo(DefaultDeviceTestIme.class.getName());

        assertThat(defaultDeviceImi).isNotNull();
        assertThat(defaultDeviceImi.isVirtualDeviceOnly()).isFalse();
    }

    /** The default IME is used on virtual devices when there's no custom IME component. */
    @Test
    public void noCustomImeComponent_defaultImeShouldBeOnVirtualDisplay() {
        createVirtualDeviceAndDisplay(/* imeComponent= */ Optional.empty());

        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mDefaultDeviceImeListener, timeout(TIMEOUT_MILLIS).atLeastOnce())
                .onShow(mVirtualDisplayId);
    }

    /** The default IME is used on virtual devices when the custom IME component is {@code null}. */
    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceParams.Builder#setInputMethodComponent"})
    @Test
    public void nullCustomImeComponent_defaultImeShouldBeOnVirtualDisplay() {
        createVirtualDeviceAndDisplay(/* imeComponent= */ Optional.ofNullable(null));

        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mDefaultDeviceImeListener, timeout(TIMEOUT_MILLIS).atLeastOnce())
                .onShow(mVirtualDisplayId);
    }

    /** No IME is used on virtual devices when the custom IME component doesn't exist. */
    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceParams.Builder#setInputMethodComponent"})
    @Test
    public void nonExistentCustomImeComponent_noImeOnVirtualDisplay() {
        createVirtualDeviceAndDisplay(
                /* imeComponent= */ Optional.of(new ComponentName("foo.bar", "foo.bar.Baz")));

        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mDefaultDeviceImeListener, after(TIMEOUT_MILLIS).never()).onShow(anyInt());
    }

    /** No IME is used on virtual devices when the custom IME component is invalid. */
    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceParams.Builder#setInputMethodComponent"})
    @Test
    public void invalidCustomImeComponent_noImeOnVirtualDisplay() {
        createVirtualDeviceAndDisplay(/* imeComponent= */ Optional.of(
                new ComponentName(mContext, EmptyActivity.class.getName())));

        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mDefaultDeviceImeListener, after(TIMEOUT_MILLIS).never()).onShow(anyInt());
    }

    /**
     * The virtual device custom IME is used on its virtual displays but the default IME is still
     * used on the displays not owned by that virtual device.
     */
    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceParams.Builder#setInputMethodComponent"})
    @Test
    public void validCustomImeComponent_customImeShouldBeOnVirtualDisplay() {
        createVirtualDeviceAndDisplay(/* imeComponent= */ Optional.of(
                new ComponentName(mContext, VirtualDeviceTestIme.class.getName())));

        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mVirtualDeviceImeListener, timeout(TIMEOUT_MILLIS).atLeastOnce())
                .onShow(mVirtualDisplayId);
        assertThat(mInputMethodManager.getCurrentInputMethodInfo().getId())
                .isEqualTo(mVirtualDeviceImeId);

        showSoftInputOnDisplay(Display.DEFAULT_DISPLAY);
        verify(mDefaultDeviceImeListener, timeout(TIMEOUT_MILLIS).atLeastOnce())
                .onShow(Display.DEFAULT_DISPLAY);
        assertThat(mInputMethodManager.getCurrentInputMethodInfo().getId())
                .isEqualTo(mDefaultDeviceDefaultImeId);
    }

    /**
     * If the default device IME changes while the virtual one is in use it should be restored.
     */
    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceParams.Builder#setInputMethodComponent"})
    @Test
    public void customImeComponent_changeDefaultDeviceIme() {
        createVirtualDeviceAndDisplay(/* imeComponent= */ Optional.of(
                new ComponentName(mContext, VirtualDeviceTestIme.class.getName())));
        SystemUtil.runShellCommandOrThrow("ime disable " + mDefaultDeviceDefaultImeId);
        assertThat(mInputMethodManager.getCurrentInputMethodInfo().getId())
                .isNotEqualTo(mDefaultDeviceDefaultImeId);

        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mVirtualDeviceImeListener, timeout(TIMEOUT_MILLIS).atLeastOnce())
                .onShow(mVirtualDisplayId);

        SystemUtil.runShellCommandOrThrow("ime enable " + mDefaultDeviceDefaultImeId);
        SystemUtil.runShellCommandOrThrow("ime set " + mDefaultDeviceDefaultImeId);
        assertThat(mInputMethodManager.getCurrentInputMethodInfo().getId())
                .isEqualTo(mVirtualDeviceImeId);

        showSoftInputOnDisplay(Display.DEFAULT_DISPLAY);
        verify(mDefaultDeviceImeListener, timeout(TIMEOUT_MILLIS).atLeastOnce())
                .onShow(Display.DEFAULT_DISPLAY);
        assertThat(mInputMethodManager.getCurrentInputMethodInfo().getId())
                .isEqualTo(mDefaultDeviceDefaultImeId);
    }

    /**
     * If the custom IME is disabled while the virtual one is in use it should not be restored.
     */
    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceParams.Builder#setInputMethodComponent"})
    @Test
    public void customImeComponent_disableDefaultDeviceIme() {
        createVirtualDeviceAndDisplay(/* imeComponent= */ Optional.of(
                new ComponentName(mContext, VirtualDeviceTestIme.class.getName())));
        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mVirtualDeviceImeListener, timeout(TIMEOUT_MILLIS).atLeastOnce())
                .onShow(mVirtualDisplayId);

        SystemUtil.runShellCommandOrThrow("ime disable " + mDefaultDeviceDefaultImeId);
        assertThat(mInputMethodManager.getCurrentInputMethodInfo().getId())
                .isEqualTo(mVirtualDeviceImeId);

        reset(mVirtualDeviceImeListener);
        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mVirtualDeviceImeListener, timeout(TIMEOUT_MILLIS).atLeastOnce())
                .onShow(mVirtualDisplayId);

        showSoftInputOnDisplay(Display.DEFAULT_DISPLAY);
        assertThat(mInputMethodManager.getCurrentInputMethodInfo().getId())
                .isNotEqualTo(mDefaultDeviceDefaultImeId);
        assertThat(mInputMethodManager.getCurrentInputMethodInfo().getId())
                .isNotEqualTo(mVirtualDeviceImeId);
        verify(mDefaultDeviceImeListener, after(TIMEOUT_MILLIS).never()).onShow(anyInt());
    }

    @Test
    public void localImePolicy_isDefault() {
        createVirtualDeviceAndDisplay();
        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mDefaultDeviceImeListener, timeout(TIMEOUT_MILLIS).atLeastOnce())
                .onShow(mVirtualDisplayId);
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayImePolicy"})
    @Test
    public void setDisplayImePolicy_invalidDisplay_throws() {
        VirtualDevice virtualDevice = createVirtualDeviceAndDisplay();
        assertThrows(SecurityException.class,
                () -> virtualDevice.setDisplayImePolicy(Display.INVALID_DISPLAY,
                        DISPLAY_IME_POLICY_FALLBACK_DISPLAY));
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayImePolicy"})
    @Test
    public void setDisplayImePolicy_defaultDisplay_throws() {
        VirtualDevice virtualDevice = createVirtualDeviceAndDisplay();
        assertThrows(SecurityException.class,
                () -> virtualDevice.setDisplayImePolicy(Display.DEFAULT_DISPLAY,
                        DISPLAY_IME_POLICY_FALLBACK_DISPLAY));
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayImePolicy"})
    @Test
    public void setDisplayImePolicy_unownedDisplay_throws() {
        VirtualDevice virtualDevice = createVirtualDeviceAndDisplay();
        VirtualDisplay unownedDisplay = mRule.createManagedUnownedVirtualDisplayWithFlags(
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        assertThrows(SecurityException.class,
                () -> virtualDevice.setDisplayImePolicy(unownedDisplay.getDisplay().getDisplayId(),
                        DISPLAY_IME_POLICY_FALLBACK_DISPLAY));
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayImePolicy"})
    @Test
    public void setDisplayImePolicy_untrustedDisplay_throws() {
        VirtualDevice virtualDevice = createVirtualDeviceAndDisplay();
        VirtualDisplay untrustedDisplay = mRule.createManagedVirtualDisplayWithFlags(virtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        assertThrows(SecurityException.class,
                () -> virtualDevice.setDisplayImePolicy(
                        untrustedDisplay.getDisplay().getDisplayId(),
                        DISPLAY_IME_POLICY_FALLBACK_DISPLAY));
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayImePolicy"})
    @Test
    public void fallbackDisplayImePolicy_imeShowsOnDefaultDisplay() {
        VirtualDevice virtualDevice = createVirtualDeviceAndDisplay();
        virtualDevice.setDisplayImePolicy(mVirtualDisplayId, DISPLAY_IME_POLICY_FALLBACK_DISPLAY);

        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mDefaultDeviceImeListener, timeout(TIMEOUT_MILLIS).atLeastOnce())
                .onShow(Display.DEFAULT_DISPLAY);
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayImePolicy"})
    @Test
    public void hideImePolicy_noIme() {
        VirtualDevice virtualDevice = createVirtualDeviceAndDisplay();
        virtualDevice.setDisplayImePolicy(mVirtualDisplayId, DISPLAY_IME_POLICY_HIDE);

        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mDefaultDeviceImeListener, after(TIMEOUT_MILLIS).never()).onShow(anyInt());
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayImePolicy"})
    @Test
    public void setDisplayImePolicy_changeAtRuntime() {
        VirtualDevice virtualDevice = createVirtualDeviceAndDisplay();
        virtualDevice.setDisplayImePolicy(mVirtualDisplayId, DISPLAY_IME_POLICY_FALLBACK_DISPLAY);
        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mDefaultDeviceImeListener, timeout(TIMEOUT_MILLIS).atLeastOnce())
                .onShow(Display.DEFAULT_DISPLAY);

        virtualDevice.setDisplayImePolicy(mVirtualDisplayId, DISPLAY_IME_POLICY_LOCAL);
        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mDefaultDeviceImeListener, timeout(TIMEOUT_MILLIS).atLeastOnce())
                .onShow(mVirtualDisplayId);

        virtualDevice.setDisplayImePolicy(mVirtualDisplayId, DISPLAY_IME_POLICY_HIDE);
        reset(mDefaultDeviceImeListener);
        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mDefaultDeviceImeListener, after(TIMEOUT_MILLIS).never()).onShow(anyInt());
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayImePolicy"})
    @Test
    public void setDisplayImePolicy_differentPoliciesForDifferentDisplays() {
        VirtualDevice virtualDevice = createVirtualDeviceAndDisplay();
        virtualDevice.setDisplayImePolicy(mVirtualDisplayId, DISPLAY_IME_POLICY_FALLBACK_DISPLAY);

        VirtualDisplay localImeDisplay = mRule.createManagedVirtualDisplayWithFlags(virtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        final int localImeDisplayId = localImeDisplay.getDisplay().getDisplayId();
        virtualDevice.setDisplayImePolicy(localImeDisplayId, DISPLAY_IME_POLICY_LOCAL);

        VirtualDisplay noImeDisplay = mRule.createManagedVirtualDisplayWithFlags(virtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        final int noImeDisplayId = noImeDisplay.getDisplay().getDisplayId();
        virtualDevice.setDisplayImePolicy(noImeDisplayId, DISPLAY_IME_POLICY_HIDE);

        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mDefaultDeviceImeListener, timeout(TIMEOUT_MILLIS).atLeastOnce())
                .onShow(Display.DEFAULT_DISPLAY);

        showSoftInputOnDisplay(localImeDisplayId);
        verify(mDefaultDeviceImeListener, timeout(TIMEOUT_MILLIS).atLeastOnce())
                .onShow(localImeDisplayId);

        reset(mDefaultDeviceImeListener);
        showSoftInputOnDisplay(noImeDisplayId);
        verify(mDefaultDeviceImeListener, after(TIMEOUT_MILLIS).never()).onShow(anyInt());
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setInputMethodComponent",
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayImePolicy"})
    @Test
    public void fallbackDisplayImePolicy_customImeComponentIgnored() {
        VirtualDevice virtualDevice = createVirtualDeviceAndDisplay(/* imeComponent= */ Optional.of(
                new ComponentName(mContext, VirtualDeviceTestIme.class.getName())));
        virtualDevice.setDisplayImePolicy(mVirtualDisplayId, DISPLAY_IME_POLICY_FALLBACK_DISPLAY);

        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mVirtualDeviceImeListener, after(TIMEOUT_MILLIS).never()).onShow(anyInt());
        verify(mDefaultDeviceImeListener, timeout(TIMEOUT_MILLIS)).onShow(Display.DEFAULT_DISPLAY);
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setInputMethodComponent",
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayImePolicy"})
    @Test
    public void hideImePolicy_customImeComponentIgnored() {
        VirtualDevice virtualDevice = createVirtualDeviceAndDisplay(/* imeComponent= */ Optional.of(
                new ComponentName(mContext, VirtualDeviceTestIme.class.getName())));
        virtualDevice.setDisplayImePolicy(mVirtualDisplayId, DISPLAY_IME_POLICY_HIDE);

        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mVirtualDeviceImeListener, after(TIMEOUT_MILLIS).never()).onShow(anyInt());
        verify(mDefaultDeviceImeListener, never()).onShow(Display.DEFAULT_DISPLAY);
    }

    private InputMethodInfo getInputMethodInfo(String className) {
        final String imeId = new ComponentName(mContext, className).flattenToShortString();
        return mInputMethodManager.getInputMethodList().stream()
                .filter(imi -> imi.getId().equals(imeId)).findFirst().orElse(null);
    }

    private void showSoftInputOnDisplay(int displayId) {
        EmptyActivity activity = mRule.startActivityOnDisplaySync(displayId, EmptyActivity.class);

        InputMethodManager activityInputMethodManager =
                activity.getSystemService(InputMethodManager.class);

        getInstrumentation().runOnMainSync(
                () -> activityInputMethodManager.showSoftInput(
                        activity.getWindow().getDecorView(), 0));
        getInstrumentation().waitForIdleSync();
    }

    private VirtualDevice createVirtualDeviceAndDisplay() {
        return createVirtualDeviceAndDisplay(Optional.empty());
    }

    private VirtualDevice createVirtualDeviceAndDisplay(Optional<ComponentName> imeComponent) {
        VirtualDeviceParams.Builder builder = new VirtualDeviceParams.Builder();
        imeComponent.ifPresent(componentName -> builder.setInputMethodComponent(componentName));
        VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(builder.build());

        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(virtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        mVirtualDisplayId = virtualDisplay.getDisplay().getDisplayId();
        return virtualDevice;
    }

    private <T extends InputMethodService> String enableTestIme(
            Class<T> imeClass, boolean makeDefault) {
        final String imeId =
                new ComponentName(mContext, imeClass.getName()).flattenToShortString();
        SystemUtil.runShellCommandOrThrow("ime enable " + imeId);
        if (makeDefault) {
            SystemUtil.runShellCommandOrThrow("ime set " + imeId);
            assertThat(mInputMethodManager.getCurrentInputMethodInfo().getId()).isEqualTo(imeId);
        }
        return imeId;
    }

    /**
     * Simple IME implementation forwarding the show input requests to a listener along with a
     * display id.
     */
    public static class DefaultDeviceTestIme extends InputMethodService {

        static ImeListener sImeListener = null;

        @Override
        public boolean onShowInputRequested(int flags, boolean configChange) {
            if (sImeListener != null) {
                Display display = getWindow().getWindow().getDecorView().getDisplay();
                if (display != null) {
                    sImeListener.onShow(display.getDisplayId());
                }
            }
            return true;
        }
    }

    /**
     * Simple IME implementation forwarding the show input requests to a listener along with a
     * display id.
     */
    public static class VirtualDeviceTestIme extends InputMethodService {

        static ImeListener sImeListener = null;

        @Override
        public boolean onShowInputRequested(int flags, boolean configChange) {
            if (sImeListener != null) {
                Display display = getWindow().getWindow().getDecorView().getDisplay();
                if (display != null) {
                    sImeListener.onShow(display.getDisplayId());
                }
            }
            return true;
        }
    }
}
