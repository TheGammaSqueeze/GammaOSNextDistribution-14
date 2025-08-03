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

import static org.junit.Assert.assertEquals;

import android.net.Uri;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScrollTestActivity extends AppCompatActivity {

  protected static final int SURFACE_HEIGHT = 600; /* Surface layout_height is 600dp*/
  protected SurfaceView mFirstSurfaceView;
  protected SurfaceView mSecondSurfaceView;
  protected ExoPlayer mFirstPlayer;
  protected ExoPlayer mSecondPlayer;
  protected static List<String> sVideoUrls = new ArrayList<>();
  protected Player.Listener mPlayerListener;
  protected ScrollView mScrollView;
  protected boolean mIsFirstSurfaceActive;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main_scroll);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    buildPlayer();
  }

  /**
   * Build the two players with two surface view and changes the surface and player according to
   * the top position of the view.
   */
  protected void buildPlayer() {
    mScrollView = this.findViewById(R.id.scroll_view);
    mFirstPlayer = new ExoPlayer.Builder(getApplicationContext()).build();
    mFirstSurfaceView = findViewById(R.id.firstSurface);
    mFirstPlayer.setVideoSurfaceView(mFirstSurfaceView);
    mSecondPlayer = new ExoPlayer.Builder(getApplicationContext()).build();
    mSecondSurfaceView = findViewById(R.id.secondSurface);
    mSecondPlayer.setVideoSurfaceView(mSecondSurfaceView);

    mScrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
      if (mIsFirstSurfaceActive && mScrollView.getScrollY() >= SURFACE_HEIGHT) {
        mFirstPlayer.pause();
        mIsFirstSurfaceActive = false;
        mSecondPlayer.play();
      } else if (!mIsFirstSurfaceActive && mScrollView.getScrollY() < SURFACE_HEIGHT) {
        mSecondPlayer.pause();
        mIsFirstSurfaceActive = true;
        mFirstPlayer.play();
      }
    });
  }

  /**
   * Prepare input list and add alternate urls to first and second player's playlist.
   */
  public void prepareMediaItems(List<String> urls) {
    sVideoUrls = urls != null ? Collections.unmodifiableList(urls) : null;
    if (sVideoUrls == null) {
      return;
    }
    assertEquals(0, (sVideoUrls.size() % 2));
    for (int i = 0; i < sVideoUrls.size(); ) {
      mFirstPlayer.addMediaItem(MediaItem.fromUri(Uri.parse(sVideoUrls.get(i++))));
      mSecondPlayer.addMediaItem(MediaItem.fromUri(Uri.parse(sVideoUrls.get(i++))));
    }
  }

  /**
   * Prepare the player and play the first player.
   */
  public void run() {
    mFirstPlayer.prepare();
    mSecondPlayer.prepare();
    mIsFirstSurfaceActive = true;
    mFirstPlayer.play();
  }

  /**
   * Resume the first player.
   */
  @Override
  protected void onResume() {
    super.onResume();
    mIsFirstSurfaceActive = true;
    mFirstPlayer.play();
  }

  /**
   * Stop both players.
   */
  @Override
  protected void onStop() {
    super.onStop();
    mFirstPlayer.pause();
    mSecondPlayer.pause();
  }

  /**
   * Release the players and destroy the activity.
   */
  @Override
  protected void onDestroy() {
    super.onDestroy();
    mFirstPlayer.release();
    mSecondPlayer.release();
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  /**
   * Register listener to receive events from the player.
   *
   * <p>This method can be called from any thread.
   *
   * @param listener The listener to register.
   */
  public void addPlayerListener(Player.Listener listener) {
    mSecondPlayer.addListener(listener);
    mFirstPlayer.addListener(listener);
    this.mPlayerListener = listener;
  }

  /**
   * Unregister a listener registered through addPlayerListener(Listener). The listener will no
   * longer receive events.
   */
  public void removePlayerListener() {
    mSecondPlayer.removeListener(this.mPlayerListener);
    mFirstPlayer.removeListener(this.mPlayerListener);
  }
}
