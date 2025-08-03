/*
 * Copyright 2024 The Android Open Source Project
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
import static android.server.wm.CtsWindowInfoUtils.sendTap;
import static android.server.wm.CtsWindowInfoUtils.tapOnWindowCenter;
import static android.server.wm.CtsWindowInfoUtils.waitForStableWindowGeometry;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowInfos;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowOnTop;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowVisible;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceControlInputReceiver;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.cts.surfacevalidator.EmbeddedSCVHService;
import android.view.cts.surfacevalidator.IAttachEmbeddedWindow;
import android.view.cts.surfacevalidator.IMotionEventReceiver;
import android.window.InputTransferToken;
import android.window.WindowInfosListenerForTest;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.window.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SurfaceControlInputReceiverTests {
    private static final String TAG = "SurfaceControlInputReceiverTests";
    private static final long WAIT_TIME_S = 5L * HW_TIMEOUT_MULTIPLIER;

    private static final Rect sBounds = new Rect(0, 0, 100, 100);

    @Rule
    public ActivityScenarioRule<TestActivity> mActivityRule =
            new ActivityScenarioRule<>(TestActivity.class);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private TestActivity mActivity;

    private WindowManager mWm;

    @Before
    public void setUp() throws InterruptedException {
        mActivityRule.getScenario().onActivity(a -> mActivity = a);
        mWm = mActivity.getWindowManager();
    }

    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER)
    @Test
    public void testLocalSurfaceControlReceivesInput() throws InterruptedException {
        SurfaceControl sc = new SurfaceControl.Builder()
                .setName("Local Child SurfaceControl")
                .setBufferSize(sBounds.width(), sBounds.height())
                .build();

        Surface surface = new Surface(sc);
        Canvas canvas = surface.lockHardwareCanvas();
        canvas.drawColor(Color.RED);
        surface.unlockCanvasAndPost(canvas);

        AnchorView anchorView = new AnchorView(mActivity.getApplicationContext(), sc);

        Choreographer[] choreographer = new Choreographer[1];
        CountDownLatch choreographerLatch = new CountDownLatch(1);
        mActivity.runOnUiThread(() -> {
            mActivity.setContentView(anchorView);
            choreographer[0] = Choreographer.getInstance();
            choreographerLatch.countDown();
        });
        anchorView.waitForDrawn();
        assertTrue("Failed to get Choreographer",
                choreographerLatch.await(WAIT_TIME_S, TimeUnit.SECONDS));

        try {
            final LinkedBlockingQueue<MotionEvent> motionEvents = new LinkedBlockingQueue<>();
            mWm.registerBatchedSurfaceControlInputReceiver(
                    mActivity.getDisplayId(),
                    mActivity.getWindow().getRootSurfaceControl().getInputTransferToken(), sc,
                    choreographer[0], event -> {
                        if (event instanceof MotionEvent) {
                            try {
                                motionEvents.put(MotionEvent.obtain((MotionEvent) event));
                            } catch (InterruptedException e) {
                                Log.e(TAG, "Failed to add input event to queue", e);
                            }
                        }
                        return false;
                    });

            IBinder clientToken = mWm.getSurfaceControlInputClientToken(sc);
            assertAndDumpWindowState(TAG,
                    "Failed to wait for SurfaceControl with Input to be on top",
                    waitForWindowOnTop(WAIT_TIME_S, TimeUnit.SECONDS, () -> clientToken));
            Point tappedCoords = new Point();
            tapOnWindowCenter(InstrumentationRegistry.getInstrumentation(),
                    () -> clientToken, false /* useGlobalInject */, tappedCoords);

            MotionEvent motionEvent = motionEvents.poll(WAIT_TIME_S, TimeUnit.SECONDS);
            assertAndDumpWindowState(TAG, "Failed to receive touch", motionEvent != null);
            assertMotionEvent(motionEvent, tappedCoords);

        } finally {
            mActivity.runOnUiThread(() -> mWm.unregisterSurfaceControlInputReceiver(sc));
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER)
    @Test
    public void testRemoteSurfaceControlReceivesInput()
            throws InterruptedException, RemoteException {
        SurfaceControl sc = new SurfaceControl.Builder()
                .setName("Anchor SurfaceControl")
                .build();
        AnchorView anchorView = new AnchorView(mActivity.getApplicationContext(), sc);

        CountDownLatch mEmbeddedServiceReady = new CountDownLatch(1);
        IAttachEmbeddedWindow[] attachEmbeddedWindow = new IAttachEmbeddedWindow[1];
        mActivity.runOnUiThread(() -> {
            ServiceConnection mConnection = new ServiceConnection() {
                // Called when the connection with the service is established
                public void onServiceConnected(ComponentName className, IBinder service) {
                    attachEmbeddedWindow[0] = IAttachEmbeddedWindow.Stub.asInterface(service);
                    mEmbeddedServiceReady.countDown();
                }

                public void onServiceDisconnected(ComponentName className) {
                    attachEmbeddedWindow[0] = null;
                }
            };

            Intent intent = new Intent(mActivity, EmbeddedSCVHService.class);
            intent.setAction(IAttachEmbeddedWindow.class.getName());
            mActivity.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

            mActivity.setContentView(anchorView);
        });

        assertTrue("Failed to wait for embedded service to bind",
                mEmbeddedServiceReady.await(WAIT_TIME_S, TimeUnit.SECONDS));

        anchorView.waitForDrawn();

        final LinkedBlockingQueue<MotionEvent> motionEvents = new LinkedBlockingQueue<>();
        try {
            String embeddedName = attachEmbeddedWindow[0].attachEmbeddedSurfaceControl(sc,
                    mActivity.getDisplayId(),
                    mActivity.getWindow().getRootSurfaceControl().getInputTransferToken(),
                    sBounds.width(),
                    sBounds.height(), false, new IMotionEventReceiver.Stub() {
                        @Override
                        public void onMotionEventReceived(MotionEvent motionEvent) {
                            try {
                                motionEvents.put(motionEvent);
                            } catch (InterruptedException e) {
                                Log.e(TAG, "Failed to add input event to queue", e);
                            }
                        }
                    });

            assertNotNull("Failed to receive embedded client name", embeddedName);

            Rect bounds = new Rect();
            boolean success = waitForWindowOnTop(WAIT_TIME_S, TimeUnit.SECONDS,
                    windowInfo -> getBoundsIfWindowIsVisible(windowInfo, mActivity.getDisplayId(),
                            embeddedName, bounds));
            assertAndDumpWindowState(TAG, "Failed to find embedded SC on top", success);

            final Point coord = new Point(bounds.left + bounds.width() / 2,
                    bounds.top + bounds.height() / 2);
            sendTap(InstrumentationRegistry.getInstrumentation(), coord,
                    false /* useGlobalInjection */);

            MotionEvent motionEvent = motionEvents.poll(WAIT_TIME_S, TimeUnit.SECONDS);
            assertAndDumpWindowState(TAG, "Failed to receive touch", motionEvent != null);
            assertMotionEvent(motionEvent, coord);
        } finally {
            attachEmbeddedWindow[0].tearDownEmbeddedSurfaceControl();
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER)
    @Test
    public void testNonBatchedSurfaceControlReceivesInput() throws InterruptedException {
        SurfaceControl sc = new SurfaceControl.Builder()
                .setName("Local Child SurfaceControl")
                .setBufferSize(sBounds.width(), sBounds.height())
                .build();

        Surface surface = new Surface(sc);
        Canvas canvas = surface.lockHardwareCanvas();
        canvas.drawColor(Color.RED);
        surface.unlockCanvasAndPost(canvas);

        AnchorView anchorView = new AnchorView(mActivity.getApplicationContext(), sc);

        CountDownLatch choreographerLatch = new CountDownLatch(1);
        mActivity.runOnUiThread(() -> {
            mActivity.setContentView(anchorView);
            choreographerLatch.countDown();
        });
        anchorView.waitForDrawn();
        assertTrue("Failed to get Choreographer",
                choreographerLatch.await(WAIT_TIME_S, TimeUnit.SECONDS));

        try {
            final LinkedBlockingQueue<MotionEvent> motionEvents = new LinkedBlockingQueue<>();
            mWm.registerUnbatchedSurfaceControlInputReceiver(
                    mActivity.getDisplayId(),
                    mActivity.getWindow().getRootSurfaceControl().getInputTransferToken(), sc,
                    mActivity.getMainLooper(), event -> {
                        if (event instanceof MotionEvent) {
                            try {
                                motionEvents.put(MotionEvent.obtain((MotionEvent) event));
                            } catch (InterruptedException e) {
                                Log.e(TAG, "Failed to add input event to queue", e);
                            }
                        }
                        return false;
                    });

            IBinder clientToken = mWm.getSurfaceControlInputClientToken(sc);
            assertAndDumpWindowState(TAG,
                    "Failed to wait for SurfaceControl with Input to be on top",
                    waitForWindowOnTop(WAIT_TIME_S, TimeUnit.SECONDS,
                            () -> clientToken));
            Point tappedCoords = new Point();
            tapOnWindowCenter(InstrumentationRegistry.getInstrumentation(),
                    () -> clientToken, false /* useGlobalInject */, tappedCoords);

            MotionEvent motionEvent = motionEvents.poll(WAIT_TIME_S, TimeUnit.SECONDS);
            assertAndDumpWindowState(TAG, "Failed to receive input", motionEvent != null);
            assertMotionEvent(motionEvent, tappedCoords);
        } finally {
            mActivity.runOnUiThread(() -> mWm.unregisterSurfaceControlInputReceiver(sc));
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER)
    @Test
    public void testTransferGestureFromHostToEmbedded() throws InterruptedException {
        LocalSurfaceControlInputReceiverHelper helper = new LocalSurfaceControlInputReceiverHelper(
                mActivity);
        try {
            final LinkedBlockingQueue<MotionEvent> embeddedMotionEvent =
                    new LinkedBlockingQueue<>();
            CountDownLatch hostReceivedTouchLatch = new CountDownLatch(1);
            helper.setup(false, (v, event) -> {
                mWm.transferTouchGesture(
                        mActivity.getWindow().getRootSurfaceControl().getInputTransferToken(),
                        helper.mEmbeddedTransferToken);
                hostReceivedTouchLatch.countDown();
                return false;
            }, event -> {
                if (event instanceof MotionEvent) {
                    try {
                        embeddedMotionEvent.put(MotionEvent.obtain((MotionEvent) event));
                    } catch (InterruptedException e) {
                    }
                }
                return false;
            });
            Point tappedCoords = new Point();
            IBinder clientToken = mWm.getSurfaceControlInputClientToken(helper.mEmbeddedSc);
            tapOnWindowCenter(InstrumentationRegistry.getInstrumentation(),
                    () -> clientToken, false /* useGlobalInject */, tappedCoords);
            assertTrue("Failed to receive touch event on host",
                    hostReceivedTouchLatch.await(WAIT_TIME_S, TimeUnit.SECONDS));
            MotionEvent motionEvent = embeddedMotionEvent.poll(WAIT_TIME_S, TimeUnit.SECONDS);
            assertAndDumpWindowState(TAG, "Failed to receive touch", motionEvent != null);
            assertMotionEvent(motionEvent, tappedCoords);
        } finally {
            helper.tearDown();
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER)
    @Test
    public void testTransferGestureFromHostToEmbeddedRemote()
            throws InterruptedException, RemoteException {
        RemoteSurfaceControlInputReceiverHelper helper =
                new RemoteSurfaceControlInputReceiverHelper(mActivity);
        try {
            final LinkedBlockingQueue<MotionEvent> embeddedMotionEvents =
                    new LinkedBlockingQueue<>();
            CountDownLatch hostReceivedTouchLatch = new CountDownLatch(1);
            helper.setup(false /* zOrderOnTop */, false /* transferTouchToHost */, (v, event) -> {
                mWm.transferTouchGesture(
                        mActivity.getWindow().getRootSurfaceControl().getInputTransferToken(),
                        helper.mEmbeddedTransferToken);
                hostReceivedTouchLatch.countDown();
                return false;
            }, new IMotionEventReceiver.Stub() {
                @Override
                public void onMotionEventReceived(MotionEvent motionEvent) {
                    try {
                        embeddedMotionEvents.put(MotionEvent.obtain(motionEvent));
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Failed to add input event to queue", e);
                    }
                }
            });
            Rect bounds = new Rect();
            assertAndDumpWindowState(TAG,
                    "Failed to wait for SurfaceControl with Input to be visible",
                    waitForWindowInfos(
                            windowInfos -> {
                                for (var windowInfo : windowInfos) {
                                    if (getBoundsIfWindowIsVisible(windowInfo,
                                            mActivity.getDisplayId(),
                                            helper.mEmbeddedName, bounds)) {
                                        return true;
                                    }
                                }
                                return false;
                            }, WAIT_TIME_S, TimeUnit.SECONDS));
            final Point coord = new Point(bounds.left + bounds.width() / 2,
                    bounds.top + bounds.height() / 2);
            sendTap(InstrumentationRegistry.getInstrumentation(), coord,
                    false /* useGlobalInjection */);

            assertTrue("Failed to receive touch event on host",
                    hostReceivedTouchLatch.await(WAIT_TIME_S, TimeUnit.SECONDS));
            MotionEvent motionEvent = embeddedMotionEvents.poll(WAIT_TIME_S, TimeUnit.SECONDS);
            assertAndDumpWindowState(TAG, "Failed to receive touch", motionEvent != null);
            assertMotionEvent(motionEvent, coord);
        } finally {
            helper.tearDown();
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER)
    @Test
    public void testTransferGestureFromEmbeddedToHost() throws InterruptedException {
        LocalSurfaceControlInputReceiverHelper helper = new LocalSurfaceControlInputReceiverHelper(
                mActivity);
        try {
            final LinkedBlockingQueue<MotionEvent> hostMotionEvent = new LinkedBlockingQueue<>();
            CountDownLatch embeddedReceivedTouch = new CountDownLatch(1);
            helper.setup(true, (v, event) -> {
                try {
                    hostMotionEvent.put(MotionEvent.obtain((MotionEvent) event));
                } catch (InterruptedException e) {
                }
                return false;
            }, event -> {
                if (event instanceof MotionEvent) {
                    mWm.transferTouchGesture(helper.mEmbeddedTransferToken,
                            mActivity.getWindow()
                                    .getRootSurfaceControl().getInputTransferToken());
                    embeddedReceivedTouch.countDown();
                }
                return false;
            });
            Point tappedCoords = new Point();
            IBinder clientToken = mWm.getSurfaceControlInputClientToken(helper.mEmbeddedSc);
            tapOnWindowCenter(InstrumentationRegistry.getInstrumentation(),
                    () -> clientToken, false /* useGlobalInject */, tappedCoords);
            assertTrue("Failed to receive touch event on embedded",
                    embeddedReceivedTouch.await(WAIT_TIME_S, TimeUnit.SECONDS));
            MotionEvent motionEvent = hostMotionEvent.poll(WAIT_TIME_S, TimeUnit.SECONDS);
            assertAndDumpWindowState(TAG, "Failed to receive touch", motionEvent != null);
            assertMotionEvent(motionEvent, tappedCoords);
        } finally {
            helper.tearDown();
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER)
    @Test
    public void testTransferGestureFromEmbeddedToHostRemote()
            throws InterruptedException, RemoteException {
        RemoteSurfaceControlInputReceiverHelper helper =
                new RemoteSurfaceControlInputReceiverHelper(mActivity);
        try {
            final LinkedBlockingQueue<MotionEvent> hostMotionEvent =
                    new LinkedBlockingQueue<>();
            CountDownLatch embeddedReceivedTouch = new CountDownLatch(1);
            helper.setup(true /* zOrderOnTop */, true /* transferTouchToHost */, (v, event) -> {
                try {
                    hostMotionEvent.put(MotionEvent.obtain(event));
                } catch (InterruptedException e) {
                    Log.e(TAG, "Failed to add input event to queue", e);
                }
                return false;
            }, new IMotionEventReceiver.Stub() {
                @Override
                public void onMotionEventReceived(MotionEvent motionEvent) {
                    Log.d(TAG, "onMotionEventReceived. Transfer");
                    embeddedReceivedTouch.countDown();
                }
            });
            Rect bounds = new Rect();
            assertAndDumpWindowState(TAG,
                    "Failed to wait for SurfaceControl with Input to be visible",
                    waitForWindowInfos(
                            windowInfos -> {
                                for (var windowInfo : windowInfos) {
                                    if (getBoundsIfWindowIsVisible(windowInfo,
                                            mActivity.getDisplayId(),
                                            helper.mEmbeddedName, bounds)) {
                                        return true;
                                    }
                                }
                                return false;
                            }, WAIT_TIME_S, TimeUnit.SECONDS));
            final Point coord = new Point(bounds.left + bounds.width() / 2,
                    bounds.top + bounds.height() / 2);
            sendTap(InstrumentationRegistry.getInstrumentation(), coord,
                    false /* useGlobalInjection */);

            assertTrue("Failed to receive touch event on embedded",
                    embeddedReceivedTouch.await(WAIT_TIME_S, TimeUnit.SECONDS));
            MotionEvent motionEvent = hostMotionEvent.poll(WAIT_TIME_S, TimeUnit.SECONDS);
            assertAndDumpWindowState(TAG, "Failed to receive touch", motionEvent != null);
            assertMotionEvent(motionEvent, coord);
        } finally {
            helper.tearDown();
        }
    }

    private static void assertMotionEvent(MotionEvent event, Point coords) {
        assertEquals(
                "Expected ACTION_DOWN. Received " + MotionEvent.actionToString(event.getAction()),
                MotionEvent.ACTION_DOWN, event.getAction());
        Point receivedCoords = new Point((int) event.getX(), (int) event.getY());
        assertEquals("Expected " + coords + ". Received " + receivedCoords, coords, receivedCoords);
    }

    private static boolean getBoundsIfWindowIsVisible(
            WindowInfosListenerForTest.WindowInfo windowInfo, int displayId, String name,
            Rect outBounds) {
        if (!windowInfo.isVisible || windowInfo.displayId != displayId) {
            return false;
        }
        if (!windowInfo.name.contains(name)) {
            return false;
        }

        if (!windowInfo.bounds.isEmpty()) {
            outBounds.set(windowInfo.bounds);
            return true;
        }
        return false;
    }

    private static class AnchorView extends View {
        SurfaceControl mChild;

        private final CountDownLatch mDrawCompleteLatch = new CountDownLatch(1);

        private boolean mChildScAttached;

        AnchorView(Context c, SurfaceControl child) {
            super(c, null, 0, 0);
            mChild = child;
            getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    attachChildSc();
                    getViewTreeObserver().removeOnPreDrawListener(this);
                    return true;
                }
            });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attachChildSc();
        }

        private void attachChildSc() {
            if (mChildScAttached) {
                return;
            }

            SurfaceControl.Transaction t =
                    getRootSurfaceControl().buildReparentTransaction(mChild);

            if (t == null) {
                // TODO (b/286406553) SurfaceControl was not yet setup. Wait until the draw request
                // to attach since the SurfaceControl will be created by that point. This can be
                // cleaned up when the bug is fixed.
                return;
            }

            t.setLayer(mChild, 1).setVisibility(mChild, true).setCrop(mChild, sBounds)
                    .setPosition(mChild, (float) getWidth() / 2, (float) getHeight() / 2);
            t.addTransactionCommittedListener(Runnable::run, mDrawCompleteLatch::countDown);
            getRootSurfaceControl().applyTransactionOnDraw(t);
            mChildScAttached = true;
        }

        @Override
        protected void onDetachedFromWindow() {
            new SurfaceControl.Transaction().reparent(mChild, null).apply();
            mChild.release();
            mChildScAttached = false;

            super.onDetachedFromWindow();
        }

        public void waitForDrawn() throws InterruptedException {
            assertTrue("Failed to wait for frame to draw",
                    mDrawCompleteLatch.await(WAIT_TIME_S, TimeUnit.SECONDS));
        }
    }

    private static class LocalSurfaceControlInputReceiverHelper {
        private final Activity mActivity;
        private final WindowManager mWm;

        private SurfaceControl mEmbeddedSc;

        private InputTransferToken mEmbeddedTransferToken;

        LocalSurfaceControlInputReceiverHelper(Activity activity) {
            mActivity = activity;
            mWm = mActivity.getWindowManager();
        }

        public void setup(boolean zOrderOnTop, View.OnTouchListener hostTouchListener,
                SurfaceControlInputReceiver embeddedInputReceiver) throws InterruptedException {
            mEmbeddedSc = new SurfaceControl.Builder()
                    .setName("Local Child SurfaceControl")
                    .setBufferSize(sBounds.width(), sBounds.height())
                    .build();

            Surface surface = new Surface(mEmbeddedSc);
            Canvas canvas = surface.lockHardwareCanvas();
            canvas.drawColor(Color.RED);
            surface.unlockCanvasAndPost(canvas);

            final CountDownLatch drawCompleteLatch = new CountDownLatch(1);

            // Place the child z order on top so it gets touch first and can transfer to host
            SurfaceView surfaceView = new SurfaceView(mActivity.getApplicationContext());
            surfaceView.setZOrderOnTop(zOrderOnTop);
            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder holder) {
                    SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                    t.setVisibility(mEmbeddedSc, true).setCrop(mEmbeddedSc, sBounds).reparent(
                            mEmbeddedSc,
                            surfaceView.getSurfaceControl());
                    t.addTransactionCommittedListener(Runnable::run, drawCompleteLatch::countDown);
                    t.apply();
                }

                @Override
                public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                        int height) {
                }

                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                    new SurfaceControl.Transaction().reparent(mEmbeddedSc, null).apply();
                }
            });

            Choreographer[] choreographer = new Choreographer[1];
            CountDownLatch choreographerLatch = new CountDownLatch(1);
            mActivity.runOnUiThread(() -> {
                mActivity.setContentView(surfaceView);
                choreographer[0] = Choreographer.getInstance();
                choreographerLatch.countDown();
            });
            assertTrue("Failed to wait for child SC to draw",
                    drawCompleteLatch.await(WAIT_TIME_S, TimeUnit.SECONDS));
            assertTrue("Failed to get Choreographer",
                    choreographerLatch.await(WAIT_TIME_S, TimeUnit.SECONDS));

            mEmbeddedTransferToken = mWm.registerBatchedSurfaceControlInputReceiver(
                    mActivity.getDisplayId(),
                    surfaceView.getRootSurfaceControl().getInputTransferToken(), mEmbeddedSc,
                    choreographer[0], embeddedInputReceiver);
            surfaceView.setOnTouchListener(hostTouchListener);

            IBinder clientToken = mWm.getSurfaceControlInputClientToken(mEmbeddedSc);
            assertNotNull("SurfaceControl client token was null", clientToken);
            waitForStableWindowGeometry(WAIT_TIME_S, TimeUnit.SECONDS);
            assertAndDumpWindowState(TAG,
                    "Failed to wait for SurfaceControl with Input to be visible",
                    waitForWindowVisible(clientToken));
        }

        private void tearDown() {
            mActivity.runOnUiThread(
                    () -> mWm.unregisterSurfaceControlInputReceiver(mEmbeddedSc));
        }
    }

    private static class RemoteSurfaceControlInputReceiverHelper {
        private final Activity mActivity;

        private IAttachEmbeddedWindow mIAttachEmbeddedWindow;

        private InputTransferToken mEmbeddedTransferToken;

        private String mEmbeddedName;

        RemoteSurfaceControlInputReceiverHelper(Activity activity) {
            mActivity = activity;
        }

        public void setup(boolean zOrderOnTop, boolean transferTouchToHost,
                View.OnTouchListener hostTouchListener,
                IMotionEventReceiver.Stub motionEventReceiver)
                throws InterruptedException, RemoteException {
            SurfaceView surfaceView = new SurfaceView(mActivity.getApplicationContext());
            surfaceView.setZOrderOnTop(zOrderOnTop);

            CountDownLatch surfaceViewCreatedLatch = new CountDownLatch(1);
            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder holder) {
                    surfaceViewCreatedLatch.countDown();
                }

                @Override
                public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                        int height) {
                }

                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                }
            });

            CountDownLatch embeddedServiceReady = new CountDownLatch(1);
            mActivity.runOnUiThread(() -> {
                ServiceConnection mConnection = new ServiceConnection() {
                    // Called when the connection with the service is established
                    public void onServiceConnected(ComponentName className, IBinder service) {
                        mIAttachEmbeddedWindow = IAttachEmbeddedWindow.Stub.asInterface(service);
                        embeddedServiceReady.countDown();
                    }

                    public void onServiceDisconnected(ComponentName className) {
                        mIAttachEmbeddedWindow = null;
                    }
                };

                Intent intent = new Intent(mActivity, EmbeddedSCVHService.class);
                intent.setAction(IAttachEmbeddedWindow.class.getName());
                mActivity.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

                mActivity.setContentView(surfaceView);
            });

            assertTrue("Failed to wait for embedded service to bind",
                    embeddedServiceReady.await(WAIT_TIME_S, TimeUnit.SECONDS));
            assertTrue("Failed to create SurfaceView SurfaceControl",
                    surfaceViewCreatedLatch.await(WAIT_TIME_S, TimeUnit.SECONDS));

            mEmbeddedName = mIAttachEmbeddedWindow.attachEmbeddedSurfaceControl(
                    surfaceView.getSurfaceControl(), mActivity.getDisplayId(),
                    surfaceView.getRootSurfaceControl().getInputTransferToken(), sBounds.width(),
                    sBounds.height(), transferTouchToHost,
                    motionEventReceiver);
            assertNotNull("SurfaceControl client token was null", mEmbeddedName);

            surfaceView.setOnTouchListener(hostTouchListener);
            mEmbeddedTransferToken = mIAttachEmbeddedWindow.getEmbeddedInputTransferToken();
            waitForStableWindowGeometry(WAIT_TIME_S, TimeUnit.SECONDS);
        }

        private void tearDown() throws RemoteException {
            mIAttachEmbeddedWindow.tearDownEmbeddedSurfaceControl();
        }
    }
}
