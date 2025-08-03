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

package android.car.cts.builtin.view;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.Activity;
import android.car.builtin.input.InputManagerHelper;
import android.car.builtin.view.TouchableInsetsProvider;
import android.car.test.AbstractExpectableTestCase;
import android.car.test.PermissionsCheckerRule;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.content.Context;
import android.graphics.Region;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@EnsureHasPermission(Manifest.permission.INJECT_EVENTS)
public final class TouchableInsetsProviderTest extends AbstractExpectableTestCase {
    private static final String TAG = TouchableInsetsProviderTest.class.getSimpleName();
    private static final int INPUT_EVENT_PROPAGATION_DELAY_MS = 100;

    @Rule
    public final ActivityScenarioRule<TestActivity> mActivityRule = new ActivityScenarioRule<>(
            TestActivity.class);

    @Rule
    public final PermissionsCheckerRule mPermissionsCheckerRule = new PermissionsCheckerRule();

    public static final class TestActivity extends Activity {
        private TestView mTestView;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mTestView = new TestView(this);
            setContentView(mTestView);
        }
    }

    /**
     * The View which is testing {@link TouchableInsetsProvider}. It can capture the TouchEvent
     * on it, and the top-left corner is specified as the obscured touch region.
     * <p>So it can accept the touch on the region, and the touch events besides it would be
     * passed through below.
     */
    private static final class TestView extends View {
        private final TouchableInsetsProvider mTouchableInsetsProvider;
        private MotionEvent mCapturedEvent;

        private TestView(@NonNull Context context) {
            super(context, /* attrs= */ null, /* defStyle= */ 0, /* defStyleRes= */ 0);
            mTouchableInsetsProvider = new TouchableInsetsProvider(this);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            Log.d(TAG, "onTouchEvent: event=" + event);
            mCapturedEvent = event;
            return super.onTouchEvent(event);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            mTouchableInsetsProvider.addToViewTreeObserver();
        }

        @Override
        protected void onDetachedFromWindow() {
            mTouchableInsetsProvider.removeFromViewTreeObserver();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            // Sets the top left corner as the obscured touch region.
            Region obscuredRegion = new Region(left, top,
                    left + (right - left) / 4, top + (bottom - top) / 4);
            mTouchableInsetsProvider.setObscuredTouchRegion(obscuredRegion);
            super.onLayout(changed, left, top, right, bottom);
        }
    }

    private InputManager mInputManager;
    private TestActivity mActivity;

    @Before
    public void setup() {
        mInputManager = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSystemService(InputManager.class);
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
    }

    @Test
    public void testTouchableInsetsProvider_InputEventDeliveredOnObscuredRegion() {
        View view = mActivity.getWindow().getDecorView();
        tapOnView(view, view.getWidth() / 8, view.getHeight() / 8);

        assertThat(mActivity.mTestView.mCapturedEvent).isNotNull();
    }

    @Test
    public void testViewWithTouchableInsetsProvider_InputEventIsNotDelivered() {
        View view = mActivity.getWindow().getDecorView();
        tapOnView(view, view.getWidth() / 2, view.getHeight() / 2);

        expectWithMessage("No touch on center").that(mActivity.mTestView.mCapturedEvent).isNull();

        mActivity.mTestView.mCapturedEvent = null;
        tapOnView(view, view.getWidth() / 8 * 7, view.getHeight() / 8);

        expectWithMessage("No touch on top-right corner")
                .that(mActivity.mTestView.mCapturedEvent).isNull();

        mActivity.mTestView.mCapturedEvent = null;
        tapOnView(view, view.getWidth() / 8, view.getHeight() / 8 * 7);

        expectWithMessage("No touch on bottom-left corner")
                .that(mActivity.mTestView.mCapturedEvent).isNull();

        mActivity.mTestView.mCapturedEvent = null;
        tapOnView(view, view.getWidth() / 8 * 7, view.getHeight() / 8 * 7);

        expectWithMessage("No touch on bottom-right corner")
                .that(mActivity.mTestView.mCapturedEvent).isNull();
    }

    private void tapOnView(View view, int x, int y) {
        injectInputEvent(obtainMotionEvent(MotionEvent.ACTION_DOWN, view, x, y));
        injectInputEvent(obtainMotionEvent(MotionEvent.ACTION_UP, view, x, y));
        SystemClock.sleep(INPUT_EVENT_PROPAGATION_DELAY_MS);
    }

    private static MotionEvent obtainMotionEvent(int action, View view, int x, int y) {
        long eventTime = SystemClock.uptimeMillis();
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        MotionEvent event = MotionEvent.obtain(eventTime, eventTime, action,
                loc[0] + x, loc[1] + y, /* metaState= */ 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        return event;
    }

    private void injectInputEvent(MotionEvent event) {
        Log.d(TAG, "injectInputEvent: event=" + event);
        InputManagerHelper.injectInputEvent(mInputManager, event);
    }
}
