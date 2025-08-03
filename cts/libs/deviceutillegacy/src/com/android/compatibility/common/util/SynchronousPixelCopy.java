/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.compatibility.common.util;

import static org.junit.Assert.fail;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.PixelCopy.OnPixelCopyFinishedListener;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;

import java.util.function.Consumer;

public class SynchronousPixelCopy implements OnPixelCopyFinishedListener {
    private static final long TIMEOUT_MILLIS = 1000;
    private static Handler sHandler;
    static {
        HandlerThread thread = new HandlerThread("PixelCopyHelper");
        thread.start();
        sHandler = new Handler(thread.getLooper());
    }

    private int mStatus = -1;

    public int request(Surface source, Bitmap dest) {
        synchronized (this) {
            mStatus = -1;
            PixelCopy.request(source, dest, this, sHandler);
            return getResultLocked();
        }
    }

    public int request(Surface source, Rect srcRect, Bitmap dest) {
        synchronized (this) {
            mStatus = -1;
            PixelCopy.request(source, srcRect, dest, this, sHandler);
            return getResultLocked();
        }
    }

    public int request(SurfaceView source, Bitmap dest) {
        synchronized (this) {
            mStatus = -1;
            PixelCopy.request(source, dest, this, sHandler);
            return getResultLocked();
        }
    }

    public int request(SurfaceView source, Rect srcRect, Bitmap dest) {
        synchronized (this) {
            mStatus = -1;
            PixelCopy.request(source, srcRect, dest, this, sHandler);
            return getResultLocked();
        }
    }

    public int request(Window source, Bitmap dest) {
        synchronized (this) {
            mStatus = -1;
            PixelCopy.request(source, dest, this, sHandler);
            return getResultLocked();
        }
    }

    public int request(Window source, Rect srcRect, Bitmap dest) {
        synchronized (this) {
            mStatus = -1;
            PixelCopy.request(source, srcRect, dest, this, sHandler);
            return getResultLocked();
        }
    }

    private int getResultLocked() {
        long now = SystemClock.uptimeMillis();
        final long end = now + TIMEOUT_MILLIS;
        while (mStatus == -1 && now <= end) {
            try {
                this.wait(end - now);
            } catch (InterruptedException e) { }
            now = SystemClock.uptimeMillis();
        }
        if (mStatus == -1) {
            fail("PixelCopy request didn't complete within " + TIMEOUT_MILLIS + "ms");
        }
        return mStatus;
    }

    @Override
    public void onPixelCopyFinished(int copyResult) {
        synchronized (this) {
            mStatus = copyResult;
            this.notify();
        }
    }

    private static final class BlockingResult implements Consumer<PixelCopy.Result> {
        private PixelCopy.Result mResult;

        public PixelCopy.Result getResult() {
            synchronized (this) {
                long now = SystemClock.uptimeMillis();
                final long end = now + TIMEOUT_MILLIS;
                while (mResult == null && now <= end) {
                    try {
                        this.wait(end - now);
                    } catch (InterruptedException e) { }
                    now = SystemClock.uptimeMillis();
                }
                if (mResult == null) {
                    fail("PixelCopy request didn't complete within " + TIMEOUT_MILLIS + "ms");
                }
                return mResult;
            }
        }

        @Override
        public void accept(PixelCopy.Result copyResult) {
            synchronized (this) {
                mResult = copyResult;
                this.notify();
            }
        }
    }

    /** happy lint */
    public static PixelCopy.Result requestSync(PixelCopy.Request request) {
        BlockingResult resultWaiter = new BlockingResult();
        PixelCopy.request(request, Runnable::run, resultWaiter);
        return resultWaiter.getResult();
    }

    /** happy lint */
    public static PixelCopy.Result copySurface(Surface source,
            Consumer<PixelCopy.Request.Builder> func) {
        PixelCopy.Request.Builder request = PixelCopy.Request.Builder.ofSurface(source);
        if (func != null) {
            func.accept(request);
        }
        return requestSync(request.build());
    }

    /** happy lint */
    public static PixelCopy.Result copySurface(Surface source) {
        return copySurface(source, null);
    }

    /** happy lint */
    public static PixelCopy.Result copySurface(SurfaceView source,
            Consumer<PixelCopy.Request.Builder> func) {
        PixelCopy.Request.Builder request = PixelCopy.Request.Builder.ofSurface(source);
        if (func != null) {
            func.accept(request);
        }
        return requestSync(request.build());
    }

    /** happy lint */
    public static PixelCopy.Result copySurface(SurfaceView source) {
        return copySurface(source, null);
    }

    /** happy lint */
    public static PixelCopy.Result copyWindow(View source,
            Consumer<PixelCopy.Request.Builder> func) {
        PixelCopy.Request.Builder request = PixelCopy.Request.Builder.ofWindow(source);
        if (func != null) {
            func.accept(request);
        }
        return requestSync(request.build());
    }

    /** happy lint */
    public static PixelCopy.Result copyWindow(View source) {
        return copyWindow(source, null);
    }

    /** happy lint */
    public static PixelCopy.Result copyWindow(Window source,
            Consumer<PixelCopy.Request.Builder> func) {
        PixelCopy.Request.Builder request = PixelCopy.Request.Builder.ofWindow(source);
        if (func != null) {
            func.accept(request);
        }
        return requestSync(request.build());
    }

    /** happy lint */
    public static PixelCopy.Result copyWindow(Window source) {
        return copyWindow(source, null);
    }
}
