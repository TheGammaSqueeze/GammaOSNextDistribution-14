/**
 * Copyright (C) 2024 The Android Open Source Project
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

package android.media.cujcommon.cts;

import android.app.Instrumentation;
import android.content.res.Resources;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.media3.common.Player;

public class PinchToZoomTestPlayerListener extends PlayerListener {

  private static final int ZOOM_IN_DURATION_MS = 4000;
  private static final int PINCH_STEP_COUNT = 10;
  private static final float SPAN_GAP = 50.0f;
  private static final int SCREEN_WIDTH = Resources.getSystem().getDisplayMetrics().widthPixels;
  private static final int SCREEN_HEIGHT = Resources.getSystem().getDisplayMetrics().heightPixels;
  private static final float LEFT_MARGIN_WIDTH_FACTOR = 0.1f;
  private static final float RIGHT_MARGIN_WIDTH_FACTOR = 0.9f;
  private static final float STEP_SIZE =
      (RIGHT_MARGIN_WIDTH_FACTOR * SCREEN_WIDTH - LEFT_MARGIN_WIDTH_FACTOR * SCREEN_WIDTH
          - 2 * SPAN_GAP) / (2 * PINCH_STEP_COUNT);

  public PinchToZoomTestPlayerListener(long sendMessagePosition) {
    super();
    this.mSendMessagePosition = sendMessagePosition;
  }

  /**
   * Return a new pointer of the display.
   *
   * @param x x coordinate of the pointer
   * @param y y coordinate of the pointer
   */
  PointerCoords getDisplayPointer(float x, float y) {
    PointerCoords pointerCoords = new PointerCoords();
    pointerCoords.x = x;
    pointerCoords.y = y;
    pointerCoords.pressure = 1;
    pointerCoords.size = 1;
    return pointerCoords;
  }

  @Override
  public TestType getTestType() {
    return TestType.PINCH_TO_ZOOM_TEST;
  }

  @Override
  public void onEventsPlaybackStateChanged(@NonNull Player player) {
    if (player.getPlaybackState() == Player.STATE_READY) {
      // At the first media transition player is not ready. So, add duration of
      // first clip when player is ready
      mExpectedTotalTime += player.getDuration();
      // Register scale gesture detector
      mActivity.mScaleGestureDetector = new ScaleGestureDetector(mActivity,
          new ScaleGestureListener(mActivity.mExoplayerView));
    }
  }

  @Override
  public void onEventsMediaItemTransition(@NonNull Player player) {
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          // Programmatically pinch and zoom in
          pinchAndZoom(true /* zoomIn */);
        }).setLooper(Looper.getMainLooper()).setPosition(mSendMessagePosition)
        .setDeleteAfterDelivery(true)
        .send();
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          // Programmatically pinch and zoom out
          pinchAndZoom(false /* zoomOut */);
        }).setLooper(Looper.getMainLooper())
        .setPosition(mSendMessagePosition + ZOOM_IN_DURATION_MS)
        .setDeleteAfterDelivery(true)
        .send();
  }

  /**
   * Create a new MotionEvent, filling in all of the basic values that define the motion. Then,
   * dispatch a pointer event into a window owned by the instrumented application.
   *
   * @param inst              An instance of {@link Instrumentation} for sending pointer event.
   * @param action            The kind of action being performed.
   * @param pointerCount      The number of pointers that will be in this event.
   * @param pointerProperties An array of <em>pointerCount</em> values providing a
   *                          {@link PointerProperties} property object for each pointer, which must
   *                          include the pointer identifier.
   * @param pointerCoords     An array of <em>pointerCount</em> values providing a
   *                          {@link PointerCoords} coordinate object for each pointer.
   */
  void obtainAndSendPointerEvent(Instrumentation inst, int action, int pointerCount,
      PointerProperties[] pointerProperties, PointerCoords[] pointerCoords) {
    MotionEvent pointerMotionEvent = MotionEvent.obtain(SystemClock.uptimeMillis() /* downTime */,
        SystemClock.uptimeMillis() /* eventTime */, action, pointerCount, pointerProperties,
        pointerCoords, 0 /* metaState */, 0 /* buttonState */, 1 /* xPrecision */,
        1 /* yPrecision */, 0 /* deviceId */, 0 /* edgeFlags */, 0 /* source */, 0 /* flags */);
    inst.sendPointerSync(pointerMotionEvent);
  }

  /**
   * Return array of two PointerCoords.
   *
   * @param isZoomIn  True for zoom in.
   */
  PointerCoords[] getPointerCoords(boolean isZoomIn) {
    PointerCoords leftPointerStartCoords;
    PointerCoords rightPointerStartCoords;
    float midDisplayHeight = SCREEN_HEIGHT / 2.0f;
    if (isZoomIn) {
      float midDisplayWidth = SCREEN_WIDTH / 2.0f;
      // During zoom in, start pinching from middle of the display towards the end.
      leftPointerStartCoords = getDisplayPointer(midDisplayWidth - SPAN_GAP, midDisplayHeight);
      rightPointerStartCoords = getDisplayPointer(midDisplayWidth + SPAN_GAP, midDisplayHeight);
    } else {
      // During zoom out, start pinching from end of the display towards the middle.
      leftPointerStartCoords = getDisplayPointer(LEFT_MARGIN_WIDTH_FACTOR * SCREEN_WIDTH,
          midDisplayHeight);
      rightPointerStartCoords = getDisplayPointer(RIGHT_MARGIN_WIDTH_FACTOR * SCREEN_WIDTH,
          midDisplayHeight);
    }
    return new PointerCoords[]{leftPointerStartCoords, rightPointerStartCoords};
  }

  /**
   * Return array of two PointerProperties.
   */
  PointerProperties[] getPointerProperties() {
    PointerProperties defaultPointerProperties = new PointerProperties();
    defaultPointerProperties.toolType = MotionEvent.TOOL_TYPE_FINGER;
    PointerProperties leftPointerProperties = new PointerProperties(defaultPointerProperties);
    leftPointerProperties.id = 0;
    PointerProperties rightPointerProperties = new PointerProperties(defaultPointerProperties);
    rightPointerProperties.id = 1;
    return new PointerProperties[]{leftPointerProperties, rightPointerProperties};
  }

  /**
   * Simulate pinch gesture to zoom in and zoom out.
   *
   * @param isZoomIn  True for zoom in.
   */
  private void pinchAndZoom(boolean isZoomIn) {
    new Thread(() -> {
      try {
        PointerCoords[] pointerCoords = getPointerCoords(isZoomIn);
        PointerProperties[] pointerProperties = getPointerProperties();

        Instrumentation inst = new Instrumentation();
        // Pinch In
        obtainAndSendPointerEvent(inst, MotionEvent.ACTION_DOWN, 1 /* pointerCount*/,
            pointerProperties, pointerCoords);
        obtainAndSendPointerEvent(inst, MotionEvent.ACTION_POINTER_DOWN + (pointerProperties[1].id
                << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2 /* pointerCount */, pointerProperties,
            pointerCoords);

        for (int i = 0; i < PINCH_STEP_COUNT; i++) {
          if (isZoomIn) {
            pointerCoords[0].x -= STEP_SIZE;
            pointerCoords[1].x += STEP_SIZE;
          } else {
            pointerCoords[0].x += STEP_SIZE;
            pointerCoords[1].x -= STEP_SIZE;
          }
          obtainAndSendPointerEvent(inst, MotionEvent.ACTION_MOVE, 2 /* pointerCount */,
              pointerProperties, pointerCoords);
        }

        // Pinch Out
        obtainAndSendPointerEvent(inst, MotionEvent.ACTION_POINTER_UP + (pointerProperties[1].id
                << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2 /* pointerCount */, pointerProperties,
            pointerCoords);
        obtainAndSendPointerEvent(inst, MotionEvent.ACTION_UP, 1 /* pointerCount */,
            pointerProperties, pointerCoords);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }).start();
  }
}
