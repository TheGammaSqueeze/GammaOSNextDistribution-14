/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.media.Ringtone;
import android.media.VolumeShaper;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.telecom.Log;
import android.telecom.Logging.Session;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.SomeArgs;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Plays the default ringtone. Uses {@link Ringtone} in a separate thread so that this class can be
 * used from the main thread.
 */
@VisibleForTesting
public class AsyncRingtonePlayer {
    // Maximum amount of time we will delay playing a ringtone while waiting for audio routing to
    // be ready.
    private static final int PLAY_DELAY_TIMEOUT_MS = 1000;
    // Message codes used with the ringtone thread.
    private static final int EVENT_PLAY = 1;
    private static final int EVENT_STOP = 2;

    /** Handler running on the ringtone thread. */
    private Handler mHandler;

    /** The current ringtone. Only used by the ringtone thread. */
    private Ringtone mRingtone;

    /**
     * Set to true if we are setting up to play or are currently playing. False if we are stopping
     * or have stopped playing.
     */
    private boolean mIsPlaying = false;

    /**
     * Set to true if BT HFP is active and audio is connected.
     */
    private boolean mIsBtActive = false;

    /**
     * A list of pending ringing ready latches, which are used to delay the ringing command until
     * audio paths are set and ringing is ready.
     */
    private final ArrayList<CountDownLatch> mPendingRingingLatches = new ArrayList<>();

    public AsyncRingtonePlayer() {
        // Empty
    }

    /**
     * Plays the appropriate ringtone for the specified call.
     * If {@link VolumeShaper.Configuration} is specified, it is applied to the ringtone to change
     * the volume of the ringtone as it plays.
     *
     * @param ringtoneInfoSupplier The {@link Ringtone} factory.
     * @param ringtoneConsumer The {@link Ringtone} post-creation callback (to start the vibration).
     * @param isHfpDeviceConnected True if there is a HFP BT device connected, false otherwise.
     */
    public void play(@NonNull Supplier<Pair<Uri, Ringtone>> ringtoneInfoSupplier,
            BiConsumer<Pair<Uri, Ringtone>, Boolean> ringtoneConsumer,
            boolean isHfpDeviceConnected) {
        Log.d(this, "Posting play.");
        mIsPlaying = true;
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = ringtoneInfoSupplier;
        args.arg2 = ringtoneConsumer;
        args.arg3 = Log.createSubsession();
        args.arg4 = prepareRingingReadyLatch(isHfpDeviceConnected);
        postMessage(EVENT_PLAY, true /* shouldCreateHandler */, args);
    }

    /** Stops playing the ringtone. */
    public void stop() {
        Log.d(this, "Posting stop.");
        mIsPlaying = false;
        postMessage(EVENT_STOP, false /* shouldCreateHandler */, null);
        // Clear any pending ringing latches so that we do not have to wait for its timeout to pass
        // before calling stop.
        clearPendingRingingLatches();
    }

    /**
     * Called when the BT HFP profile active state changes.
     * @param isBtActive A BT device is connected and audio is active.
     */
    public void updateBtActiveState(boolean isBtActive) {
        Log.i(this, "updateBtActiveState: " + isBtActive);
        synchronized (mPendingRingingLatches) {
            mIsBtActive = isBtActive;
            if (isBtActive) mPendingRingingLatches.forEach(CountDownLatch::countDown);
        }
    }

    /**
     * Prepares a new ringing ready latch and tracks it in a list. Once the ready latch has been
     * used, {@link #removePendingRingingReadyLatch(CountDownLatch)} must be called on this latch.
     * @param isHfpDeviceConnected true if there is a HFP device connected.
     * @return the newly prepared CountDownLatch
     */
    private CountDownLatch prepareRingingReadyLatch(boolean isHfpDeviceConnected) {
        CountDownLatch latch = new CountDownLatch(1);
        synchronized (mPendingRingingLatches) {
            // We only want to delay ringing if BT is connected but not active yet.
            boolean isDelayRequired = isHfpDeviceConnected && !mIsBtActive;
            Log.i(this, "prepareRingingReadyLatch:"
                    + " connected=" + isHfpDeviceConnected
                    + ", BT active=" + mIsBtActive
                    + ", isDelayRequired=" + isDelayRequired);
            if (!isDelayRequired) latch.countDown();
            mPendingRingingLatches.add(latch);
        }
        return latch;
    }

    /**
     * Remove a ringing ready latch that has been used and is no longer pending.
     * @param l The latch to remove.
     */
    private void removePendingRingingReadyLatch(CountDownLatch l) {
        synchronized (mPendingRingingLatches) {
            mPendingRingingLatches.remove(l);
        }
    }

    /**
     * Count down all pending ringing ready latches and then clear the list.
     */
    private void clearPendingRingingLatches() {
        synchronized (mPendingRingingLatches) {
            mPendingRingingLatches.forEach(CountDownLatch::countDown);
            mPendingRingingLatches.clear();
        }
    }

    /**
     * Posts a message to the ringtone-thread handler. Creates the handler if specified by the
     * parameter shouldCreateHandler.
     *
     * @param messageCode The message to post.
     * @param shouldCreateHandler True when a handler should be created to handle this message.
     */
    private void postMessage(int messageCode, boolean shouldCreateHandler, SomeArgs args) {
        synchronized(this) {
            if (mHandler == null && shouldCreateHandler) {
                mHandler = getNewHandler();
            }

            if (mHandler == null) {
                Log.d(this, "Message %d skipped because there is no handler.", messageCode);
            } else {
                mHandler.obtainMessage(messageCode, args).sendToTarget();
            }
        }
    }

    /**
     * Creates a new ringtone Handler running in its own thread.
     */
    private Handler getNewHandler() {
        Preconditions.checkState(mHandler == null);

        HandlerThread thread = new HandlerThread("ringtone-player");
        thread.start();

        return new Handler(thread.getLooper(), null /*callback*/, true /*async*/) {
            @Override
            public void handleMessage(Message msg) {
                switch(msg.what) {
                    case EVENT_PLAY:
                        handlePlay((SomeArgs) msg.obj);
                        break;
                    case EVENT_STOP:
                        handleStop();
                        break;
                }
            }
        };
    }

    /**
     * Starts the actual playback of the ringtone. Executes on ringtone-thread.
     */
    private void handlePlay(SomeArgs args) {
        Supplier<Pair<Uri, Ringtone>> ringtoneInfoSupplier =
                (Supplier<Pair<Uri, Ringtone>>) args.arg1;
        BiConsumer<Pair<Uri, Ringtone>, Boolean> ringtoneConsumer =
                (BiConsumer<Pair<Uri, Ringtone>, Boolean>) args.arg2;
        Session session = (Session) args.arg3;
        CountDownLatch ringingReadyLatch = (CountDownLatch) args.arg4;
        args.recycle();

        Log.continueSession(session, "ARP.hP");
        try {
            // Don't bother with any of this if there is an EVENT_STOP waiting, but give the
            // consumer a chance to do anything no matter what.
            if (mHandler.hasMessages(EVENT_STOP)) {
                Log.i(this, "handlePlay: skipping play early due to pending STOP");
                removePendingRingingReadyLatch(ringingReadyLatch);
                ringtoneConsumer.accept(null, /* stopped= */ true);
                return;
            }
            Ringtone ringtone = null;
            Uri ringtoneUri = null;
            boolean hasStopped = false;
            try {
                try {
                    Log.i(this, "handlePlay: delay ring for ready signal...");
                    boolean reachedZero = ringingReadyLatch.await(PLAY_DELAY_TIMEOUT_MS,
                            TimeUnit.MILLISECONDS);
                    Log.i(this, "handlePlay: ringing ready, timeout=" + !reachedZero);
                } catch (InterruptedException e) {
                    Log.w(this, "handlePlay: latch exception: " + e);
                }
                if (ringtoneInfoSupplier != null && ringtoneInfoSupplier.get() != null) {
                    ringtoneUri = ringtoneInfoSupplier.get().first;
                    ringtone = ringtoneInfoSupplier.get().second;
                }

                // Ringtone supply can be slow or stop command could have been issued while waiting
                // for BT to move to CONNECTED state. Re-check for stop event.
                if (mHandler.hasMessages(EVENT_STOP)) {
                    Log.i(this, "handlePlay: skipping play due to pending STOP");
                    hasStopped = true;
                    if (ringtone != null) ringtone.stop();  // proactively release the ringtone.
                    return;
                }
                // setRingtone even if null - it also stops any current ringtone to be consistent
                // with the overall state.
                setRingtone(ringtone);
                if (mRingtone == null) {
                    // The ringtoneConsumer can still vibrate at this stage.
                    Log.w(this, "No ringtone was found bail out from playing.");
                    return;
                }
                String uriString = ringtoneUri != null ? ringtoneUri.toSafeString() : "";
                Log.i(this, "handlePlay: Play ringtone. Uri: " + uriString);
                mRingtone.setLooping(true);
                if (mRingtone.isPlaying()) {
                    Log.d(this, "Ringtone already playing.");
                    return;
                }
                mRingtone.play();
                Log.i(this, "Play ringtone, looping.");
            } finally {
                removePendingRingingReadyLatch(ringingReadyLatch);
                ringtoneConsumer.accept(new Pair(ringtoneUri, ringtone), hasStopped);
            }
        } finally {
            Log.cancelSubsession(session);
        }
    }

    /**
     * Stops the playback of the ringtone. Executes on the ringtone-thread.
     */
    private void handleStop() {
        ThreadUtil.checkNotOnMainThread();
        Log.i(this, "Stop ringtone.");

        setRingtone(null);

        synchronized(this) {
            if (mHandler.hasMessages(EVENT_PLAY)) {
                Log.v(this, "Keeping alive ringtone thread for subsequent play request.");
            } else {
                mHandler.removeMessages(EVENT_STOP);
                mHandler.getLooper().quitSafely();
                mHandler = null;
                Log.v(this, "Handler cleared.");
            }
        }
    }

    /**
     * @return true if we are currently preparing or playing a ringtone, false if we are not.
     */
    public boolean isPlaying() {
        return mIsPlaying;
    }

    private void setRingtone(@Nullable Ringtone ringtone) {
        Log.i(this, "setRingtone: ringtone null="  + (ringtone == null));
        // Make sure that any previously created instance of Ringtone is stopped so the MediaPlayer
        // can be released, before replacing mRingtone with a new instance. This is always created
        // as a looping Ringtone, so if not stopped it will keep playing on the background.
        if (mRingtone != null) {
            Log.d(this, "Ringtone.stop() invoked.");
            mRingtone.stop();
        }
        mRingtone = ringtone;
    }
}
