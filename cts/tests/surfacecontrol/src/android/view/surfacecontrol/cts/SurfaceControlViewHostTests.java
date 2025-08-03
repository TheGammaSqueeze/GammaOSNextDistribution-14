/*
 * Copyright (C) 2019 The Android Open Source Project
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
package android.view.surfacecontrol.cts;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.server.wm.CtsWindowInfoUtils.assertAndDumpWindowState;
import static android.server.wm.CtsWindowInfoUtils.tapOnWindow;
import static android.server.wm.CtsWindowInfoUtils.tapOnWindowCenter;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowFocus;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowInfo;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowInfos;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowOnTop;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowVisible;
import static android.server.wm.MockImeHelper.createManagedMockImeSession;
import static android.view.SurfaceControlViewHost.SurfacePackage;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.CtsWindowInfoUtils;
import android.server.wm.FutureConnection;
import android.server.wm.WindowManagerState;
import android.server.wm.scvh.Components;
import android.server.wm.scvh.ICrossProcessSurfaceControlViewHostTestService;
import android.util.ArrayMap;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.window.WindowInfosListenerForTest.WindowInfo;

import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.MockImeSession;
import com.android.window.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Ensure end-to-end functionality of SurfaceControlViewHost.
 * <p>
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceTestCases:SurfaceControlViewHostTests
 */
@Presubmit
public class SurfaceControlViewHostTests extends ActivityManagerTestBase implements
        SurfaceHolder.Callback {

    public static class TestActivity extends Activity {
        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
            if (keyguardManager.isKeyguardLocked()) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        }
    }

    private static final String TAG = "SurfaceControlViewHostTests";

    private static final long WAIT_TIMEOUT_S = 5L * HW_TIMEOUT_MULTIPLIER;

    private static final ComponentName TEST_ACTIVITY = new ComponentName(
            getInstrumentation().getContext(), TestActivity.class);

    private final ActivityTestRule<TestActivity> mActivityRule = new ActivityTestRule<>(
            TestActivity.class);

    @Rule
    public TestName mName = new TestName();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private Instrumentation mInstrumentation;
    private CtsTouchUtils mCtsTouchUtils;
    private Activity mActivity;
    private SurfaceView mSurfaceView;
    private ViewGroup mViewParent;

    private SurfaceControlViewHost mVr;
    private View mEmbeddedView;
    private WindowManager.LayoutParams mEmbeddedLayoutParams;

    private volatile boolean mClicked = false;
    private volatile boolean mPopupClicked = false;
    private volatile PopupWindow mPopupWindow;

    private SurfaceControlViewHost.SurfacePackage mRemoteSurfacePackage;

    private final Map<String,
            FutureConnection<ICrossProcessSurfaceControlViewHostTestService>> mConnections =
            new ArrayMap<>();
    private ICrossProcessSurfaceControlViewHostTestService mTestService = null;
    private static final long TIMEOUT_MS = 3000L;

    /*
     * Configurable state to control how the surfaceCreated callback
     * will initialize the embedded view hierarchy.
     */
    int mEmbeddedViewWidth = 100;
    int mEmbeddedViewHeight = 100;

    private static final int DEFAULT_SURFACE_VIEW_WIDTH = 100;
    private static final int DEFAULT_SURFACE_VIEW_HEIGHT = 100;
    MockImeSession mImeSession;

    Consumer<MotionEvent> mSurfaceViewMotionConsumer = null;

    private CountDownLatch mSvCreatedLatch;

    class MotionConsumingSurfaceView extends SurfaceView {
        MotionConsumingSurfaceView(Context c) {
            super(c);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (mSurfaceViewMotionConsumer == null) {
                return false;
            } else {
                mSurfaceViewMotionConsumer.accept(ev);
                return true;
            }
        }
    }

    boolean mHostGotEvent = false;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mClicked = false;
        mEmbeddedLayoutParams = null;
        mPopupWindow = null;
        mRemoteSurfacePackage = null;

        if (supportsInstallableIme()) {
            mImeSession = createManagedMockImeSession(this);
        }

        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mCtsTouchUtils = new CtsTouchUtils(mInstrumentation.getTargetContext());
        mActivity = mActivityRule.launchActivity(null);
        mInstrumentation.waitForIdleSync();
        // Wait for device animation that shows above the activity to leave.
        waitForWindowOnTop(mActivity.getWindow());

        // This is necessary to call waitForWindowInfos
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.ACCESS_SURFACE_FLINGER);

        mSvCreatedLatch = new CountDownLatch(1);
    }

    @After
    public void tearDown() throws Throwable {
        for (FutureConnection<ICrossProcessSurfaceControlViewHostTestService> connection :
                mConnections.values()) {
            mInstrumentation.getContext().unbindService(connection);
        }
        mConnections.clear();
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    private void addSurfaceView(int width, int height) throws Throwable {
        addSurfaceView(width, height, true);
    }

    private void addSurfaceView(int width, int height, boolean onTop) throws Throwable {
        addSurfaceView(width, height, onTop, 0 /* leftMargin */, 0 /* topMargin */);
    }

    private void addSurfaceView(int width, int height, boolean onTop, int leftMargin, int topMargin)
            throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            final FrameLayout content = new FrameLayout(mActivity);
            mSurfaceView = new MotionConsumingSurfaceView(mActivity);
            mSurfaceView.setBackgroundColor(Color.BLACK);
            mSurfaceView.setZOrderOnTop(onTop);
            final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    width, height, Gravity.LEFT | Gravity.TOP);
            lp.leftMargin = leftMargin;
            lp.topMargin = topMargin;
            content.addView(mSurfaceView, lp);
            mViewParent = content;
            mActivity.setContentView(content,
                    new ViewGroup.LayoutParams(width + leftMargin, height + topMargin));
            mSurfaceView.getHolder().addCallback(this);
        });
    }

    private void addViewToSurfaceView(SurfaceView sv, View v, int width, int height) {
        mVr = new SurfaceControlViewHost(mActivity, mActivity.getDisplay(), sv.getHostToken());

        if (mEmbeddedLayoutParams == null) {
            mVr.setView(v, width, height);
        } else {
            mVr.setView(v, mEmbeddedLayoutParams);
        }

        sv.setChildSurfacePackage(mVr.getSurfacePackage());

        assertEquals(v, mVr.getView());
    }

    private void requestSurfaceViewFocus() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mSurfaceView.setFocusableInTouchMode(true);
            mSurfaceView.requestFocusFromTouch();
        });
    }

    private void assertWindowFocused(final View view, boolean hasWindowFocus) {
        if (!waitForWindowFocus(view, hasWindowFocus)) {
            fail();
        }
    }

    private void waitUntilViewDrawn(View view) throws Throwable {
        // We use frameCommitCallback because we need to ensure HWUI
        // has actually queued the frame.
        final CountDownLatch latch = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            view.getViewTreeObserver().registerFrameCommitCallback(
                    latch::countDown);
            view.invalidate();
        });
        assertTrue(latch.await(HW_TIMEOUT_MULTIPLIER, TimeUnit.SECONDS));
    }

    private void waitUntilEmbeddedViewDrawn() throws Throwable {
        waitUntilViewDrawn(mEmbeddedView);
    }

    private String getTouchableRegionFromDump() {
        final String output = runCommandAndPrintOutput("dumpsys window windows");
        boolean foundWindow = false;
        for (String line : output.split("\\n")) {
            if (line.contains("SurfaceControlViewHostTests$TestActivity")) {
                foundWindow = true;
            }
            if (foundWindow && line.contains("touchable region")) {
                return line;
            }
        }
        return null;
    }

    private boolean waitForTouchableRegionChanged(String originalTouchableRegion) {
        int retries = 0;
        while (retries < 50) {
            if (getTouchableRegionFromDump() != originalTouchableRegion) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (Exception e) {
            }
        }
        return false;
    }

    public static boolean waitForViewFocus(final View view, boolean hasViewFocus) {
        final CountDownLatch latch = new CountDownLatch(1);

        view.getHandler().post(() -> {
            if (view.hasFocus() == hasViewFocus) {
                latch.countDown();
                return;
            }
            view.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasViewFocus == hasFocus) {
                    view.setOnFocusChangeListener(null);
                    latch.countDown();
                }
            });
        });

        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mTestService == null) {
            if (mEmbeddedView != null) {
                addViewToSurfaceView(mSurfaceView, mEmbeddedView,
                        mEmbeddedViewWidth, mEmbeddedViewHeight);
            }
        } else if (mRemoteSurfacePackage == null) {
            try {
                mRemoteSurfacePackage = mTestService.getSurfacePackage(mSurfaceView.getHostToken());
            } catch (Exception e) {
            }
            mSurfaceView.setChildSurfacePackage(mRemoteSurfacePackage);
        } else {
            mSurfaceView.setChildSurfacePackage(mRemoteSurfacePackage);
        }
        mSvCreatedLatch.countDown();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
    }

    @Test
    public void testEmbeddedViewReceivesInput() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertTrue(mClicked);
    }

    @Test
    public void testEmbeddedViewReceivesRawInputCoordinatesInDisplaySpace() throws Throwable {
        final UiAutomation uiAutomation = mInstrumentation.getUiAutomation();
        final int viewX = DEFAULT_SURFACE_VIEW_WIDTH / 2;
        final int viewY = DEFAULT_SURFACE_VIEW_HEIGHT / 2;

        // Verify the input coordinates received by the embedded view in three different locations.
        for (int i = 0; i < 3; i++) {
            final List<MotionEvent> events = new ArrayList<>();
            mEmbeddedView = new View(mActivity);
            mEmbeddedView.setOnTouchListener((v, e) -> events.add(MotionEvent.obtain(e)));

            // Add a margin to the SurfaceView to offset the embedded view's location on the screen.
            final int leftMargin = i * 20;
            final int topMargin = i * 10;
            addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT, true /*onTop*/,
                    leftMargin, topMargin);
            mInstrumentation.waitForIdleSync();
            waitUntilEmbeddedViewDrawn();

            final int[] surfaceLocation = new int[2];
            mSurfaceView.getLocationOnScreen(surfaceLocation);

            final int displayX = surfaceLocation[0] + viewX;
            final int displayY = surfaceLocation[1] + viewY;
            final long downTime = SystemClock.uptimeMillis();
            mCtsTouchUtils.injectDownEvent(mInstrumentation, downTime, displayX, displayY,
                    null /*eventInjectionListener*/, /* useGlobalInjection=*/ true);
            mCtsTouchUtils.injectUpEvent(mInstrumentation, downTime, true /*useCurrentEventTime*/,
                    displayX, displayY, /* waitForAnimations= */ true,
                    /* eventInjectionListener= */ null, /* useGlobalInjection= */ true);

            assertEquals("Expected to capture all injected events.", 2, events.size());
            final float epsilon = 0.001f;
            events.forEach(e -> {
                assertEquals("Expected to get the x coordinate in View space.",
                        viewX, e.getX(), epsilon);
                assertEquals("Expected to get the y coordinate in View space.",
                        viewY, e.getY(), epsilon);
                assertEquals("Expected to get raw x coordinate in Display space.",
                        displayX, e.getRawX(), epsilon);
                assertEquals("Expected to get raw y coordinate in Display space.",
                        displayY, e.getRawY(), epsilon);
            });
        }
    }

    private static int getGlEsVersion(Context context) {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo configInfo = activityManager.getDeviceConfigurationInfo();
        if (configInfo.reqGlEsVersion != ConfigurationInfo.GL_ES_VERSION_UNDEFINED) {
            return getMajorVersion(configInfo.reqGlEsVersion);
        } else {
            return 1; // Lack of property means OpenGL ES version 1
        }
    }

    /**
     * @see FeatureInfo#getGlEsVersion()
     */
    private static int getMajorVersion(int glEsVersion) {
        return ((glEsVersion & 0xffff0000) >> 16);
    }

    @Test
    @RequiresDevice
    public void testEmbeddedViewIsHardwareAccelerated() throws Throwable {
        // Hardware accel may not be supported on devices without GLES 2.0
        if (getGlEsVersion(mActivity) < 2) {
            return;
        }
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();

        // If we don't support hardware acceleration on the main activity the embedded
        // view also won't be.
        if (!mSurfaceView.isHardwareAccelerated()) {
            return;
        }

        assertTrue(mEmbeddedView.isHardwareAccelerated());
    }

    @Test
    public void testEmbeddedViewResizes() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });

        final int bigEdgeLength = mEmbeddedViewWidth * 3;

        // We make the SurfaceView more than twice as big as the embedded view
        // so that a touch in the middle of the SurfaceView won't land
        // on the embedded view.
        addSurfaceView(bigEdgeLength, bigEdgeLength);
        mInstrumentation.waitForIdleSync();

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertFalse(mClicked);

        mActivityRule.runOnUiThread(() -> {
            mVr.relayout(bigEdgeLength, bigEdgeLength);
        });
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // But after the click should hit.
        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertTrue(mClicked);
    }

    @Test
    public void testEmbeddedViewReleases() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertTrue(mClicked);

        mActivityRule.runOnUiThread(() -> {
            mVr.release();
        });
        mInstrumentation.waitForIdleSync();

        mClicked = false;
        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertFalse(mClicked);
    }

    @Test
    public void testDisableInputTouch() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });

        mEmbeddedLayoutParams = new WindowManager.LayoutParams(mEmbeddedViewWidth,
                mEmbeddedViewHeight, WindowManager.LayoutParams.TYPE_APPLICATION, 0,
                PixelFormat.OPAQUE);

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(() -> {
            mEmbeddedLayoutParams.flags |= FLAG_NOT_TOUCHABLE;
            mVr.relayout(mEmbeddedLayoutParams);
        });
        mInstrumentation.waitForIdleSync();

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertFalse(mClicked);

        mActivityRule.runOnUiThread(() -> {
            mEmbeddedLayoutParams.flags &= ~FLAG_NOT_TOUCHABLE;
            mVr.relayout(mEmbeddedLayoutParams);
        });
        mInstrumentation.waitForIdleSync();

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertTrue(mClicked);
    }

    @Test
    public void testFocusable() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // When surface view is focused, it should transfer focus to the embedded view.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, true);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        // When surface view is no longer focused, it should transfer focus back to the host window.
        mActivityRule.runOnUiThread(() -> mSurfaceView.setFocusable(false));
        assertWindowFocused(mEmbeddedView, false);
        // assert host has focus
        assertWindowFocused(mSurfaceView, true);
    }

    @Test
    public void testFocusWithTouch() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // Tap where the embedded window is placed to ensure focus is given via touch
        assertTrue("Failed to tap on embedded",
                tapOnWindowCenter(mInstrumentation, () -> mEmbeddedView.getWindowToken(),
                        /* useGlobalInjection= */ true, null /* outCoords */));
        assertWindowFocused(mEmbeddedView, true);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        // Tap where the host window is placed to ensure focus is given back to host when touched
        assertTrue("Failed to tap on host",
                tapOnWindowCenter(mInstrumentation, () -> mViewParent.getWindowToken(),
                        /* useGlobalInjection= */ true, null /* outCoords */));
        assertWindowFocused(mEmbeddedView, false);
        // assert host does not have focus
        assertWindowFocused(mViewParent, true);
    }

    @Test
    public void testChildWindowFocusable() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setBackgroundColor(Color.BLUE);
        View embeddedViewChild = new Button(mActivity);
        embeddedViewChild.setBackgroundColor(Color.RED);
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        mActivityRule.runOnUiThread(() -> {
            final WindowManager.LayoutParams embeddedViewChildParams =
                    new WindowManager.LayoutParams(25, 25,
                            WindowManager.LayoutParams.TYPE_APPLICATION, 0, PixelFormat.OPAQUE);
            embeddedViewChildParams.token = mEmbeddedView.getWindowToken();
            WindowManager wm = mActivity.getSystemService(WindowManager.class);
            wm.addView(embeddedViewChild, embeddedViewChildParams);
        });

        waitUntilViewDrawn(embeddedViewChild);

        assertTrue("Failed to tap on embedded child",
                tapOnWindowCenter(mInstrumentation, embeddedViewChild::getWindowToken,
                        /* useGlobalInjection= */ true, null /* outCoords */));
        // When tapping on the child embedded window, it should gain focus.
        assertWindowFocused(embeddedViewChild, true);
        // assert parent embedded window does not have focus.
        assertWindowFocused(mEmbeddedView, false);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        assertTrue("Failed to tap on embedded parent",
                tapOnWindow(mInstrumentation, () -> mEmbeddedView.getWindowToken(),
                        new Point(1, 1) /* offset */, /* useGlobalInjection= */ true));
        // When tapping on the parent embedded window, it should gain focus.
        assertWindowFocused(mEmbeddedView, true);
        // assert child embedded window does not have focus.
        assertWindowFocused(embeddedViewChild, false);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);
    }

    @Test
    public void testFocusWithTouchCrossProcess() throws Throwable {
        mTestService = getService();
        assertNotNull(mTestService);

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mSvCreatedLatch.await(5, TimeUnit.SECONDS);

        // Tap where the embedded window is placed to ensure focus is given via touch
        assertTrue("Failed to tap on embedded",
                tapOnWindowCenter(mInstrumentation, () -> {
                    try {
                        return mTestService.getWindowToken();
                    } catch (RemoteException e) {
                        return null;
                    }
                }, /* useGlobalInjection= */ true, null /* outCoords */));
        assertTrue(mTestService.waitForFocus(true));
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        // Tap where the host window is placed to ensure focus is given back to host when touched
        assertTrue("Failed to tap on host",
                tapOnWindowCenter(mInstrumentation, () -> mViewParent.getWindowToken(),
                        /* useGlobalInjection= */ true, null /* outCoords */));
        assertTrue(mTestService.waitForFocus(false));
        // assert host does not have focus
        assertWindowFocused(mViewParent, true);
    }

    @Test
    public void testWindowResumes_FocusTransfersToEmbedded() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // When surface view is focused, it should transfer focus to the embedded view.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, true);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        WindowManager wm = mActivity.getSystemService(WindowManager.class);
        View childView = new Button(mActivity);
        mActivityRule.runOnUiThread(() -> {
            final WindowManager.LayoutParams childWindowParams =
                    new WindowManager.LayoutParams(25, 25,
                            WindowManager.LayoutParams.TYPE_APPLICATION, 0, PixelFormat.OPAQUE);
            wm.addView(childView, childWindowParams);
        });
        waitUntilViewDrawn(childView);
        assertWindowFocused(childView, true);
        // Neither host or embedded should be focus
        assertWindowFocused(mSurfaceView, false);
        assertWindowFocused(mEmbeddedView, false);

        mActivityRule.runOnUiThread(() -> wm.removeView(childView));
        mInstrumentation.waitForIdleSync();

        assertWindowFocused(mEmbeddedView, true);
        assertWindowFocused(mSurfaceView, false);
    }

    @Test
    public void testImeVisible() throws Throwable {
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());
        EditText editText = new EditText(mActivity);

        mEmbeddedView = editText;
        editText.setBackgroundColor(Color.BLUE);
        editText.setPrivateImeOptions("Hello reader! This is a random string");
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // When surface view is focused, it should transfer focus to the embedded view.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, true);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        final ImeEventStream stream = mImeSession.openEventStream();
        expectEvent(stream, editorMatcher("onStartInputView",
                editText.getPrivateImeOptions()), TIMEOUT_MS);
    }

    @Test
    public void testNotFocusable() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mEmbeddedLayoutParams = new WindowManager.LayoutParams(mEmbeddedViewWidth,
                mEmbeddedViewHeight, WindowManager.LayoutParams.TYPE_APPLICATION, 0,
                PixelFormat.OPAQUE);
        mActivityRule.runOnUiThread(() -> {
            mEmbeddedLayoutParams.flags |= FLAG_NOT_FOCUSABLE;
            mVr.relayout(mEmbeddedLayoutParams);
        });
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // When surface view is focused, nothing should happen since the embedded view is not
        // focusable.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, false);
        // assert host has focus
        assertWindowFocused(mSurfaceView, true);
    }

    @Test
    public void testFocusBeforeAddingEmbedded() throws Throwable {
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        // Request focus to the SV before adding the embedded.
        requestSurfaceViewFocus();
        mSvCreatedLatch.await();
        assertTrue("Failed to wait for sv to gain focus", waitForViewFocus(mSurfaceView, true));

        mEmbeddedView = new Button(mActivity);
        mActivityRule.runOnUiThread(() -> {
            addViewToSurfaceView(mSurfaceView, mEmbeddedView, mEmbeddedViewWidth,
                    mEmbeddedViewHeight);
        });
        waitForWindowVisible(mEmbeddedView);
        assertWindowFocused(mEmbeddedView, true);
        assertWindowFocused(mSurfaceView, false);
    }

    private static class SurfaceCreatedCallback implements SurfaceHolder.Callback {
        private final CountDownLatch mSurfaceCreated;

        SurfaceCreatedCallback(CountDownLatch latch) {
            mSurfaceCreated = latch;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mSurfaceCreated.countDown();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        }
    }

    @Test
    public void testCanCopySurfacePackage() throws Throwable {
        // Create a surface view and wait for its surface to be created.
        CountDownLatch surfaceCreated = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            final FrameLayout content = new FrameLayout(mActivity);
            mSurfaceView = new SurfaceView(mActivity);
            mSurfaceView.setZOrderOnTop(true);
            content.addView(mSurfaceView, new FrameLayout.LayoutParams(
                    DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT,
                    Gravity.LEFT | Gravity.TOP));
            mActivity.setContentView(content, new ViewGroup.LayoutParams(DEFAULT_SURFACE_VIEW_WIDTH,
                    DEFAULT_SURFACE_VIEW_HEIGHT));
            mSurfaceView.getHolder().addCallback(new SurfaceCreatedCallback(surfaceCreated));

            // Create an embedded view.
            mVr = new SurfaceControlViewHost(mActivity, mActivity.getDisplay(),
                    mSurfaceView.getHostToken());
            mEmbeddedView = new Button(mActivity);
            mEmbeddedView.setOnClickListener((View v) -> mClicked = true);
            mVr.setView(mEmbeddedView, mEmbeddedViewWidth, mEmbeddedViewHeight);

        });
        assertTrue("Failed to wait for SurfaceView created",
                surfaceCreated.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));

        // Make a copy of the SurfacePackage and release the original package.
        SurfacePackage surfacePackage = mVr.getSurfacePackage();
        SurfacePackage copy = new SurfacePackage(surfacePackage);
        surfacePackage.release();

        CountDownLatch surfacePackageReparented = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            mSurfaceView.setChildSurfacePackage(copy);
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.addTransactionCommittedListener(Runnable::run, surfacePackageReparented::countDown);
            mSurfaceView.getRootSurfaceControl().applyTransactionOnDraw(t);
        });
        assertTrue("Failed to wait for surface package to get reparented",
                surfacePackageReparented.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));

        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // Check if SurfacePackage copy remains valid even though the original package has
        // been released.
        boolean success = tapOnWindowCenter(mInstrumentation,
                () -> mEmbeddedView.getWindowToken(), /* useGlobalInjection= */ true,
                null /* outCoords */);
        assertAndDumpWindowState(TAG, "Failed to find embedded window to tap", success);
        assertTrue(mClicked);
    }

    @Test
    public void testTransferSurfacePackage() throws Throwable {
        // Create a surface view and wait for its surface to be created.
        CountDownLatch surfaceCreated = new CountDownLatch(1);
        CountDownLatch surface2Created = new CountDownLatch(1);
        CountDownLatch viewDetached = new CountDownLatch(1);
        AtomicReference<SurfacePackage> surfacePackageRef = new AtomicReference<>(null);
        AtomicReference<SurfacePackage> surfacePackageCopyRef = new AtomicReference<>(null);
        AtomicReference<SurfaceView> secondSurfaceRef = new AtomicReference<>(null);

        mActivityRule.runOnUiThread(() -> {
            final FrameLayout content = new FrameLayout(mActivity);
            mSurfaceView = new SurfaceView(mActivity);
            mSurfaceView.setZOrderOnTop(true);
            content.addView(mSurfaceView, new FrameLayout.LayoutParams(DEFAULT_SURFACE_VIEW_WIDTH,
                    DEFAULT_SURFACE_VIEW_HEIGHT, Gravity.LEFT | Gravity.TOP));
            mActivity.setContentView(content, new ViewGroup.LayoutParams(DEFAULT_SURFACE_VIEW_WIDTH,
                    DEFAULT_SURFACE_VIEW_HEIGHT));
            mSurfaceView.getHolder().addCallback(new SurfaceCreatedCallback(surfaceCreated));

            // Create an embedded view.
            mVr = new SurfaceControlViewHost(mActivity, mActivity.getDisplay(),
                    mSurfaceView.getHostToken());
            mEmbeddedView = new Button(mActivity);
            mEmbeddedView.setOnClickListener((View v) -> mClicked = true);
            mVr.setView(mEmbeddedView, mEmbeddedViewWidth, mEmbeddedViewHeight);

            SurfacePackage surfacePackage = mVr.getSurfacePackage();
            surfacePackageRef.set(surfacePackage);
            surfacePackageCopyRef.set(new SurfacePackage(surfacePackage));

            // Assign the surface package to the first surface
            mSurfaceView.setChildSurfacePackage(surfacePackage);

            // Create the second surface view to which we'll assign the surface package copy
            SurfaceView secondSurface = new SurfaceView(mActivity);
            secondSurfaceRef.set(secondSurface);

            mSurfaceView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    viewDetached.countDown();
                }
            });

            secondSurface.getHolder().addCallback(new SurfaceCreatedCallback(surface2Created));

        });
        surfaceCreated.await();

        // Add the second surface view and assign it the surface package copy
        mActivityRule.runOnUiThread(() -> {
            ViewGroup content = (ViewGroup) mSurfaceView.getParent();
            content.addView(secondSurfaceRef.get(),
                    new FrameLayout.LayoutParams(DEFAULT_SURFACE_VIEW_WIDTH,
                            DEFAULT_SURFACE_VIEW_HEIGHT, Gravity.TOP | Gravity.LEFT));
            secondSurfaceRef.get().setZOrderOnTop(true);
            surfacePackageRef.get().release();
            secondSurfaceRef.get().setChildSurfacePackage(surfacePackageCopyRef.get());

            content.removeView(mSurfaceView);
        });

        // Wait for the first surface to be removed
        surface2Created.await();
        viewDetached.await();

        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // Check if SurfacePackage copy remains valid even though the original package has
        // been released and the original surface view removed.
        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule,
                secondSurfaceRef.get());
        assertTrue(mClicked);
    }

    @Test
    public void testCanReplaceSurfacePackage() throws Throwable {
        // Create a surface view and wait for its surface to be created.
        CountDownLatch surfaceCreated = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            final FrameLayout content = new FrameLayout(mActivity);
            mSurfaceView = new SurfaceView(mActivity);
            mSurfaceView.setZOrderOnTop(true);
            content.addView(mSurfaceView, new FrameLayout.LayoutParams(
                    DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT,
                    Gravity.LEFT | Gravity.TOP));
            mActivity.setContentView(content, new ViewGroup.LayoutParams(
                    DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT));
            mSurfaceView.getHolder().addCallback(new SurfaceCreatedCallback(surfaceCreated));

            // Create an embedded view without click handling.
            mVr = new SurfaceControlViewHost(mActivity, mActivity.getDisplay(),
                    mSurfaceView.getHostToken());
            mEmbeddedView = new Button(mActivity);
            mVr.setView(mEmbeddedView, mEmbeddedViewWidth, mEmbeddedViewHeight);
            mSurfaceView.setChildSurfacePackage(mVr.getSurfacePackage());
        });
        surfaceCreated.await();
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        CountDownLatch hostReady = new CountDownLatch(1);
        // Create a second surface view and wait for its surface to be created.
        mActivityRule.runOnUiThread(() -> {
            // Create an embedded view.
            mVr = new SurfaceControlViewHost(mActivity, mActivity.getDisplay(),
                    mSurfaceView.getHostToken());
            mEmbeddedView = new Button(mActivity);
            mEmbeddedView.setOnClickListener((View v) -> mClicked = true);
            mVr.setView(mEmbeddedView, mEmbeddedViewWidth, mEmbeddedViewHeight);
            hostReady.countDown();
            mSurfaceView.setChildSurfacePackage(mVr.getSurfacePackage());
        });
        hostReady.await();
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();


        // Check to see if the click went through - this only would happen if the surface package
        // was replaced
        boolean success = tapOnWindowCenter(mInstrumentation,
                () -> mEmbeddedView.getWindowToken(), /* useGlobalInjection= */ true,
                null /* outCoords */);
        assertAndDumpWindowState(TAG, "Failed to tap on window", success);
        assertTrue(mClicked);
    }

    class MotionRecordingSurfaceView extends SurfaceView {
        boolean mGotEvent = false;

        MotionRecordingSurfaceView(Context c) {
            super(c);
        }

        public boolean onTouchEvent(MotionEvent e) {
            super.onTouchEvent(e);
            synchronized (this) {
                mGotEvent = true;
            }
            return true;
        }

        boolean gotEvent() {
            synchronized (this) {
                return mGotEvent;
            }
        }

        void reset() {
            synchronized (this) {
                mGotEvent = false;
            }
        }
    }

    static class TouchPunchingView extends View {
        TouchPunchingView(Context context) {
            super(context);
        }

        void punchHoleInTouchableRegion() {
            getRootSurfaceControl().setTouchableRegion(new Region());
        }
    }

    private void addMotionRecordingSurfaceView(int width, int height) throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            final FrameLayout content = new FrameLayout(mActivity);
            mSurfaceView = new MotionRecordingSurfaceView(mActivity);
            mSurfaceView.setZOrderOnTop(true);
            content.addView(mSurfaceView, new FrameLayout.LayoutParams(
                    width, height, Gravity.LEFT | Gravity.TOP));
            mActivity.setContentView(content, new ViewGroup.LayoutParams(width, height));
            mSurfaceView.getHolder().addCallback(this);
        });
    }

    class ForwardingSurfaceView extends SurfaceView {
        SurfaceControlViewHost.SurfacePackage mPackage;

        ForwardingSurfaceView(Context c) {
            super(c);
        }

        @Override
        protected void onDetachedFromWindow() {
            mPackage.notifyDetachedFromWindow();
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            mPackage.notifyConfigurationChanged(newConfig);
        }

        @Override
        public void setChildSurfacePackage(SurfaceControlViewHost.SurfacePackage p) {
            super.setChildSurfacePackage(p);
            mPackage = p;
        }
    }

    class DetachRecordingView extends View {
        boolean mDetached = false;

        DetachRecordingView(Context c) {
            super(c);
        }

        @Override
        protected void onDetachedFromWindow() {
            mDetached = true;
        }
    }

    class ConfigRecordingView extends View {
        CountDownLatch mLatch;

        ConfigRecordingView(Context c, CountDownLatch latch) {
            super(c);
            mLatch = latch;
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            mLatch.countDown();
        }
    }

    private void addForwardingSurfaceView(int width, int height) throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            final FrameLayout content = new FrameLayout(mActivity);
            mSurfaceView = new ForwardingSurfaceView(mActivity);
            mSurfaceView.setZOrderOnTop(true);
            content.addView(mSurfaceView, new FrameLayout.LayoutParams(
                    width, height, Gravity.LEFT | Gravity.TOP));
            mViewParent = content;
            mActivity.setContentView(content, new ViewGroup.LayoutParams(width, height));
            mSurfaceView.getHolder().addCallback(this);
        });
    }

    @Test
    public void testEmbeddedViewCanSetTouchableRegion() throws Throwable {
        TouchPunchingView tpv;
        mEmbeddedView = tpv = new TouchPunchingView(mActivity);

        addMotionRecordingSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();
        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        mInstrumentation.waitForIdleSync();

        MotionRecordingSurfaceView mrsv = (MotionRecordingSurfaceView) mSurfaceView;
        assertFalse(mrsv.gotEvent());
        mActivityRule.runOnUiThread(() -> {
            tpv.punchHoleInTouchableRegion();
        });
        mInstrumentation.waitForIdleSync();
        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        mInstrumentation.waitForIdleSync();
        assertTrue(mrsv.gotEvent());
    }

    @Test
    public void forwardDetachedFromWindow() throws Throwable {
        DetachRecordingView drv = new DetachRecordingView(mActivity);
        mEmbeddedView = drv;
        addForwardingSurfaceView(100, 100);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        assertFalse(drv.mDetached);
        mActivityRule.runOnUiThread(() -> {
            mViewParent.removeView(mSurfaceView);
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(drv.mDetached);
    }

    @Test
    public void forwardConfigurationChange() throws Throwable {
        if (!supportsOrientationRequest()) {
            return;
        }
        final CountDownLatch embeddedConfigLatch = new CountDownLatch(1);
        ConfigRecordingView crv = new ConfigRecordingView(mActivity, embeddedConfigLatch);
        mEmbeddedView = crv;
        addForwardingSurfaceView(100, 100);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();
        mActivityRule.runOnUiThread(() -> {
            int orientation = mActivity.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            } else {
                orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            }
            mActivity.setRequestedOrientation(orientation);
        });
        embeddedConfigLatch.await(3, TimeUnit.SECONDS);
        mInstrumentation.waitForIdleSync();
        mActivityRule.runOnUiThread(() -> {
            assertEquals(mEmbeddedView.getResources().getConfiguration().orientation,
                    mSurfaceView.getResources().getConfiguration().orientation);
        });
    }

    @Test
    public void testEmbeddedViewReceivesInputOnBottom() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT, false);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // We should receive no input until we punch a hole
        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView,
                /* waitForAnimations= */ true, /* useGlobalInjection= */ true);
        mInstrumentation.waitForIdleSync();
        assertFalse(mClicked);

        String originalRegion = getTouchableRegionFromDump();

        mActivityRule.runOnUiThread(() -> {
            mSurfaceView.getRootSurfaceControl().setTouchableRegion(new Region(0, 0, 1, 1));
        });
        mInstrumentation.waitForIdleSync();
        // ViewRootImpl sends the touchable region to the WM via a one-way call, which is great
        // for performance...however not so good for testability, we have no way
        // to verify it has arrived! It doesn't make so much sense to bloat
        // the system image size with a completion callback for just this one test
        // so we settle for some inelegant spin-polling on the WM dump.
        // In the future when we revisit WM/Client interface and transactionalize
        // everything, we should have a standard way to wait on the completion of async
        // operations
        waitForTouchableRegionChanged(originalRegion);

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView,
                /* waitForAnimations= */ true, /* useGlobalInjection= */ true);
        mInstrumentation.waitForIdleSync();
        assertTrue(mClicked);
    }

    private ICrossProcessSurfaceControlViewHostTestService getService() throws Exception {
        return mConnections.computeIfAbsent("android.server.wm.scvh", this::connect)
                .get(TIMEOUT_MS);
    }

    private static ComponentName repackage(String packageName, ComponentName baseComponent) {
        return new ComponentName(packageName, baseComponent.getClassName());
    }

    private FutureConnection<ICrossProcessSurfaceControlViewHostTestService> connect(
            String packageName) {
        FutureConnection<ICrossProcessSurfaceControlViewHostTestService> connection =
                new FutureConnection<>(
                        ICrossProcessSurfaceControlViewHostTestService.Stub::asInterface);
        Intent intent = new Intent();
        intent.setComponent(repackage(packageName,
                Components.CrossProcessSurfaceControlViewHostTestService.COMPONENT));
        assertTrue(mInstrumentation.getContext().bindService(intent,
                connection, Context.BIND_AUTO_CREATE));
        return connection;
    }

    @Test
    public void testHostInputTokenAllowsObscuredTouches() throws Throwable {
        mTestService = getService();
        assertTrue(mTestService != null);

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT, false);
        assertTrue("Failed to wait for SV to get created",
                mSvCreatedLatch.await(5, TimeUnit.SECONDS));
        mActivityRule.runOnUiThread(() -> {
            mSurfaceView.getRootSurfaceControl().setTouchableRegion(new Region());
        });
        // TODO(b/279051608): Add touchable regions in WindowInfo test so we can make sure the
        // touchable regions for the host have been set before proceeding.
        assertTrue("Failed to wait for host window to be visible",
                waitForWindowVisible(mSurfaceView));
        assertTrue("Failed to wait for embedded window to be visible",
                waitForWindowVisible(mTestService.getWindowToken()));
        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView,
                /* waitForAnimations= */ true, /* useGlobalInjection= */ true);

        int retryCount = 0;
        boolean isViewTouchedAndObscured = mTestService.getViewIsTouchedAndObscured();
        while (!isViewTouchedAndObscured && retryCount < 3) {
            retryCount++;
            Thread.sleep(100);
            isViewTouchedAndObscured = mTestService.getViewIsTouchedAndObscured();
        }

        assertTrue(isViewTouchedAndObscured);
    }

    @Test
    public void testNoHostInputTokenDisallowsObscuredTouches() throws Throwable {
        mTestService = getService();
        mRemoteSurfacePackage = mTestService.getSurfacePackage(new Binder());
        assertTrue(mRemoteSurfacePackage != null);

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT, false);
        assertTrue("Failed to wait for SV to get created",
                mSvCreatedLatch.await(5, TimeUnit.SECONDS));
        mActivityRule.runOnUiThread(() -> {
            mSurfaceView.getRootSurfaceControl().setTouchableRegion(new Region());
        });
        // TODO(b/279051608): Add touchable regions in WindowInfo test so we can make sure the
        // touchable regions for the host have been set before proceeding.
        assertTrue("Failed to wait for host window to be visible",
                waitForWindowVisible(mSurfaceView));
        assertTrue("Failed to wait for embedded window to be visible",
                waitForWindowVisible(mTestService.getWindowToken()));

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView,
                /* waitForAnimations= */ true, /* useGlobalInjection= */ true);

        assertFalse(mTestService.getViewIsTouched());
    }

    @Test
    public void testPopupWindowReceivesInput() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        mActivityRule.runOnUiThread(() -> {
            PopupWindow pw = new PopupWindow();
            mPopupWindow = pw;
            Button popupButton = new Button(mActivity);
            popupButton.setOnClickListener((View v) -> {
                mPopupClicked = true;
            });
            pw.setWidth(DEFAULT_SURFACE_VIEW_WIDTH);
            pw.setHeight(DEFAULT_SURFACE_VIEW_HEIGHT);
            pw.setContentView(popupButton);
            pw.showAsDropDown(mEmbeddedView);
        });
        mInstrumentation.waitForIdleSync();

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertTrue(mPopupClicked);
        assertFalse(mClicked);

        mActivityRule.runOnUiThread(() -> {
            mPopupWindow.dismiss();
        });
        mInstrumentation.waitForIdleSync();

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        mInstrumentation.waitForIdleSync();
        assertTrue(mClicked);
    }

    @Test
    public void testPopupWindowPosition() throws Throwable {
        mEmbeddedView = new View(mActivity);
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        mActivityRule.runOnUiThread(() -> {
            View popupContent = new View(mActivity);
            popupContent.setBackgroundColor(Color.BLUE);

            mPopupWindow = new PopupWindow();
            mPopupWindow.setWidth(50);
            mPopupWindow.setHeight(50);
            mPopupWindow.setContentView(popupContent);
            mPopupWindow.showAtLocation(mEmbeddedView, Gravity.BOTTOM | Gravity.RIGHT, 0, 0);
        });

        Predicate<List<WindowInfo>> hasExpectedFrame = windowInfos -> {
            if (mPopupWindow == null) {
                return false;
            }

            IBinder parentWindowToken = mEmbeddedView.getWindowToken();
            IBinder popupWindowToken = mPopupWindow.getContentView().getWindowToken();
            if (parentWindowToken == null || popupWindowToken == null) {
                return false;
            }

            Rect parentBounds = null;
            Rect popupBounds = null;
            for (WindowInfo windowInfo : windowInfos) {
                if (!windowInfo.isVisible) {
                    continue;
                }
                if (windowInfo.windowToken == parentWindowToken) {
                    parentBounds = windowInfo.bounds;
                } else if (windowInfo.windowToken == popupWindowToken) {
                    popupBounds = windowInfo.bounds;
                }
            }

            if (parentBounds == null) {
                return false;
            }

            var expectedBounds = new Rect(parentBounds.left + 50, parentBounds.top + 50,
                    parentBounds.left + 100, parentBounds.top + 100);
            return expectedBounds.equals(popupBounds);
        };
        assertTrue(waitForWindowInfos(hasExpectedFrame, 5, TimeUnit.SECONDS));
    }

    @Test
    public void testFloatingWindowWrapContent() throws Throwable {
        mEmbeddedView = new View(mActivity);
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        View popupContent = new View(mActivity);
        popupContent.setBackgroundColor(Color.BLUE);
        popupContent.setLayoutParams(new ViewGroup.LayoutParams(50, 50));

        FrameLayout popupView = new FrameLayout(mActivity);
        popupView.addView(popupContent);

        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.setTitle("FloatingWindow");
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        layoutParams.token = mEmbeddedView.getWindowToken();

        mActivityRule.runOnUiThread(() -> {
            WindowManager windowManager = mActivity.getSystemService(WindowManager.class);
            windowManager.addView(popupView, layoutParams);
        });

        Predicate<WindowInfo> hasExpectedDimensions =
                windowInfo -> windowInfo.bounds.width() == 50 && windowInfo.bounds.height() == 50;
        // We pass popupView::getWindowToken as a java.util.function.Supplier
        // because the popupView is initially unattached and doesn't have a
        // window token. The supplier is called each time the predicate is
        // tested, eventually returning the window token.
        assertTrue(waitForWindowInfo(hasExpectedDimensions, 5, TimeUnit.SECONDS,
                popupView::getWindowToken, mActivity.getDisplay().getDisplayId()));
    }

    @Test
    public void testFloatingWindowMatchParent() throws Throwable {
        mEmbeddedView = new View(mActivity);
        mEmbeddedViewWidth = 50;
        mEmbeddedViewHeight = 50;
        addSurfaceView(100, 100);
        mInstrumentation.waitForIdleSync();

        View popupView = new FrameLayout(mActivity);
        popupView.setBackgroundColor(Color.BLUE);

        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.setTitle("FloatingWindow");
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.token = mEmbeddedView.getWindowToken();

        mActivityRule.runOnUiThread(() -> {
            WindowManager windowManager = mActivity.getSystemService(WindowManager.class);
            windowManager.addView(popupView, layoutParams);
        });

        Predicate<WindowInfo> hasExpectedDimensions =
                windowInfo -> windowInfo.bounds.width() == 50 && windowInfo.bounds.height() == 50;
        assertTrue(waitForWindowInfo(hasExpectedDimensions, 5, TimeUnit.SECONDS,
                popupView::getWindowToken, mActivity.getDisplay().getDisplayId()));
    }

    class TouchTransferringView extends View {
        boolean mExpectsFirstMotion = true;
        boolean mExpectsCancel = false;
        boolean mGotCancel = false;
        // True if the test should use the WindowManager#transferTouchGesture API.
        private final boolean mUseTransferTouchGestureApi;

        TouchTransferringView(Context c, boolean useTransferTouchGestureApi) {
            super(c);
            mUseTransferTouchGestureApi = useTransferTouchGestureApi;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            int action = ev.getAction();
            synchronized (this) {
                if (mExpectsFirstMotion) {
                    assertEquals(action, MotionEvent.ACTION_DOWN);
                    if (mUseTransferTouchGestureApi) {
                        assertTrue(mWm.transferTouchGesture(
                                mVr.getSurfacePackage().getInputTransferToken(),
                                mSurfaceView.getRootSurfaceControl().getInputTransferToken()));
                    } else {
                        assertTrue(mVr.transferTouchGestureToHost());
                    }
                    mExpectsFirstMotion = false;
                    mExpectsCancel = true;
                } else if (mExpectsCancel) {
                    assertEquals(action, MotionEvent.ACTION_CANCEL);
                    mExpectsCancel = false;
                    mGotCancel = true;
                }
                this.notifyAll();
            }
            return true;
        }

        void waitForEmbeddedTouch() {
            synchronized (this) {
                if (!mExpectsFirstMotion) {
                    assertTrue(mExpectsCancel || mGotCancel);
                    return;
                }
                try {
                    this.wait();
                } catch (Exception e) {
                }
                assertFalse(mExpectsFirstMotion);
            }
        }

        void waitForCancel() {
            synchronized (this) {
                if (!mExpectsCancel) {
                    return;
                }
                try {
                    this.wait();
                } catch (Exception e) {
                }
                assertTrue(mGotCancel);
            }
        }
    }

    private void testEmbeddedWindowCanTransferTouchGestureToHost(boolean useTransferTouchGestureApi)
            throws Throwable {
        // Inside the embedded view hierarchy, we set up a view that transfers touch
        // to the host upon receiving a touch event
        TouchTransferringView ttv = new TouchTransferringView(mActivity,
                useTransferTouchGestureApi);
        mEmbeddedView = ttv;
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();
        // On the host SurfaceView, we set a motion consumer which expects to receive one event.
        mHostGotEvent = false;
        mSurfaceViewMotionConsumer = (ev) -> {
            synchronized (this) {
                mHostGotEvent = true;
                this.notifyAll();
            }
        };

        // Prepare to inject an event offset one pixel from the top of the SurfaceViews location
        // on-screen.
        final int[] viewOnScreenXY = new int[2];
        mSurfaceView.getLocationOnScreen(viewOnScreenXY);
        final int injectedX = viewOnScreenXY[0] + 1;
        final int injectedY = viewOnScreenXY[1] + 1;
        final UiAutomation uiAutomation = mInstrumentation.getUiAutomation();
        long downTime = SystemClock.uptimeMillis();

        // We inject a down event
        mCtsTouchUtils.injectDownEvent(mInstrumentation, downTime, injectedX, injectedY, null,
                /* useGlobalInjection= */ false);


        // And this down event should arrive on the embedded view, which should transfer the touch
        // focus
        ttv.waitForEmbeddedTouch();
        ttv.waitForCancel();

        downTime = SystemClock.uptimeMillis();
        // Now we inject an up event
        mCtsTouchUtils.injectUpEvent(mInstrumentation, downTime, false, injectedX, injectedY, null);
        // This should arrive on the host now, since we have transferred the touch focus
        synchronized (this) {
            if (!mHostGotEvent) {
                try {
                    this.wait();
                } catch (Exception e) {
                }
            }
        }
        assertTrue(mHostGotEvent);
    }

    @Test
    public void testEmbeddedWindowCanTransferTouchGestureToHost_transferTouchGestureToHost()
            throws Throwable {
        testEmbeddedWindowCanTransferTouchGestureToHost(false);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER)
    @Test
    public void testEmbeddedWindowCanTransferTouchGestureToHost_transferTouchGesture()
            throws Throwable {
        testEmbeddedWindowCanTransferTouchGestureToHost(true);
    }

    @Test
    public void testKeepScreenOn() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        mWmState.computeState();
        WindowManagerState.WindowState windowState = mWmState.getWindowState(TEST_ACTIVITY);
        // Assert the KEEP_SCREEN_ON flag is not set on the main window yet.
        assertNotEquals(FLAG_KEEP_SCREEN_ON, (windowState.getFlags() & FLAG_KEEP_SCREEN_ON));

        final CountDownLatch keepScreenOnSetLatch = new CountDownLatch(2);
        mActivityRule.runOnUiThread(() -> {
            mEmbeddedView.setKeepScreenOn(true);
            mEmbeddedView.getViewTreeObserver().addOnDrawListener(keepScreenOnSetLatch::countDown);
            mSurfaceView.getViewTreeObserver().addOnDrawListener(keepScreenOnSetLatch::countDown);
        });
        keepScreenOnSetLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS);

        mWmState.computeState();
        windowState = mWmState.getWindowState(TEST_ACTIVITY);
        // Assert the KEEP_SCREEN_ON flag is now set on the main window.
        assertEquals(FLAG_KEEP_SCREEN_ON, (windowState.getFlags() & FLAG_KEEP_SCREEN_ON));

        final CountDownLatch keepScreenOnUnsetLatch = new CountDownLatch(2);
        mActivityRule.runOnUiThread(() -> {
            mEmbeddedView.setKeepScreenOn(false);
            mEmbeddedView.getViewTreeObserver().addOnDrawListener(
                    keepScreenOnUnsetLatch::countDown);
            mSurfaceView.getViewTreeObserver().addOnDrawListener(keepScreenOnUnsetLatch::countDown);
        });
        keepScreenOnUnsetLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS);

        mWmState.computeState();
        windowState = mWmState.getWindowState(TEST_ACTIVITY);
        // Assert the KEEP_SCREEN_ON flag is removed from the main window.
        assertNotEquals(FLAG_KEEP_SCREEN_ON, (windowState.getFlags() & FLAG_KEEP_SCREEN_ON));
    }

    @Test
    public void testKeepScreenOnCrossProcess() throws Throwable {
        mTestService = getService();
        assertNotNull(mTestService);

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mSvCreatedLatch.await(5, TimeUnit.SECONDS);

        mWmState.computeState();
        WindowManagerState.WindowState windowState = mWmState.getWindowState(TEST_ACTIVITY);
        // Assert the KEEP_SCREEN_ON flag is not set on the main window yet.
        assertNotEquals(FLAG_KEEP_SCREEN_ON, (windowState.getFlags() & FLAG_KEEP_SCREEN_ON));

        final CountDownLatch countDownLatch = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> mSurfaceView.getViewTreeObserver().addOnDrawListener(
                countDownLatch::countDown));
        mTestService.setKeepScreenOnFlag(true);
        countDownLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS);

        mWmState.computeState();
        windowState = mWmState.getWindowState(TEST_ACTIVITY);
        // Assert the KEEP_SCREEN_ON flag is now set on the main window.
        assertEquals(FLAG_KEEP_SCREEN_ON, (windowState.getFlags() & FLAG_KEEP_SCREEN_ON));

        final CountDownLatch countDownLatch2 = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> mSurfaceView.getViewTreeObserver().addOnDrawListener(
                countDownLatch2::countDown));
        mTestService.setKeepScreenOnFlag(false);
        countDownLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS);

        mWmState.computeState();
        windowState = mWmState.getWindowState(TEST_ACTIVITY);
        // Assert the KEEP_SCREEN_ON flag is removed from the main window.
        assertNotEquals(FLAG_KEEP_SCREEN_ON, (windowState.getFlags() & FLAG_KEEP_SCREEN_ON));
    }

    @Test
    public void testKeepScreenOnAfterDetachSCVH() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setKeepScreenOn(true);
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        mWmState.computeState();
        WindowManagerState.WindowState windowState = mWmState.getWindowState(TEST_ACTIVITY);
        // Assert the KEEP_SCREEN_ON flag is not set on the main window yet.
        assertEquals(FLAG_KEEP_SCREEN_ON, (windowState.getFlags() & FLAG_KEEP_SCREEN_ON));

        // Remove the SurfaceView from main window.
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            mViewParent.removeView(mSurfaceView);
            mSurfaceView.getViewTreeObserver().addOnDrawListener(countDownLatch::countDown);
        });
        countDownLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS);

        mWmState.computeState();
        windowState = mWmState.getWindowState(TEST_ACTIVITY);
        // Assert the KEEP_SCREEN_ON flag is removed from the main window.
        assertNotEquals(FLAG_KEEP_SCREEN_ON, (windowState.getFlags() & FLAG_KEEP_SCREEN_ON));
    }

    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER)
    @Test
    public void testTransferHostTouchGestureToEmbedded() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT, false /* onTop */);
        waitUntilEmbeddedViewDrawn();

        CountDownLatch receivedTouches = new CountDownLatch(1);
        boolean[] hostGotEvent = new boolean[1];
        boolean[] embeddedGotEvent = new boolean[1];
        mSurfaceViewMotionConsumer = (ev) -> {
            if (hostGotEvent[0]) {
                return;
            }
            hostGotEvent[0] = true;
            mActivity.getWindowManager().transferTouchGesture(
                    mSurfaceView.getRootSurfaceControl().getInputTransferToken(),
                    mVr.getSurfacePackage().getInputTransferToken());
            receivedTouches.countDown();
        };

        mEmbeddedView.setOnTouchListener((v, event) -> {
            if (embeddedGotEvent[0]) {
                return false;
            }
            embeddedGotEvent[0] = true;
            receivedTouches.countDown();
            return false;
        });

        final int[] viewInWindow = new int[2];
        mSurfaceView.getLocationInWindow(viewInWindow);
        Point point = new Point(viewInWindow[0] + 1, viewInWindow[1] + 1);

        CtsWindowInfoUtils.tapOnWindow(mInstrumentation, () -> mSurfaceView.getWindowToken(),
                point, /* useGlobalInjection= */ false);

        assertTrue("Failed to receive touch from host=" + hostGotEvent[0] + " or embedded="
                + embeddedGotEvent[0], receivedTouches.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));

        assertTrue("Failed to receive touch event in host window", hostGotEvent[0]);
        assertTrue("Failed to receive touch event in embedded window", embeddedGotEvent[0]);
    }
}
