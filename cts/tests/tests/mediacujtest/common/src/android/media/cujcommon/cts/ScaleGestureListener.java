/*
 * Copyright 2023 The Android Open Source Project
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

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.media3.ui.PlayerView;

public class ScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
  private float mCurrentScaleFactor = 1.0f;
  private float mStartScaleFactor;
  private final PlayerView mExoPlayerView;

  ScaleGestureListener(PlayerView exoPlayerView) {
    this.mExoPlayerView = exoPlayerView;
  }

  @Override
  public boolean onScale(ScaleGestureDetector detector) {
    mCurrentScaleFactor *= detector.getScaleFactor();
    mExoPlayerView.setScaleX(mCurrentScaleFactor);
    mExoPlayerView.setScaleY(mCurrentScaleFactor);
    return true;
  }

  @Override
  public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
    mStartScaleFactor = mCurrentScaleFactor;
    mCurrentScaleFactor *= detector.getScaleFactor();
    return true;
  }

  @Override
  public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
    assertNotEquals(mStartScaleFactor, mCurrentScaleFactor);
    // Zoom in starts with scaleFactor 1.
    if (mStartScaleFactor == 1.0f) {
      assertTrue(mCurrentScaleFactor > mStartScaleFactor);
    } else {
      assertTrue(mCurrentScaleFactor < mStartScaleFactor);
    }
  }
}
