/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.view.cts;

import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import android.app.Activity;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Window;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A minimal activity for testing {@link android.opengl.GLSurfaceView}.
 * Also accepts non-blank renderers to allow its use for more complex tests.
 */
public abstract class GLSurfaceViewCtsActivity extends Activity {

    private boolean mIsStarted = false;
    private boolean mEnterAnimationComplete = false;

    public static class TestableGLSurfaceView extends GLSurfaceView {

        public TestableGLSurfaceView(Context context) {
            super(context);
        }

        void requestRenderAndWait() {
            CountDownLatch fence = new CountDownLatch(1);

            surfaceRedrawNeededAsync(getHolder(), fence::countDown);

            try {
                assertTrue(fence.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException ex) {
                throw new AssertionError("Interrupted", ex);
            }
        }
    }

    protected TestableGLSurfaceView mView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mView = new TestableGLSurfaceView(this);
        configureGLSurfaceView();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(mView);
    }

    protected abstract void configureGLSurfaceView();

    public TestableGLSurfaceView getView() {
        return mView;
    }

    public void waitForReady() {
        long startMs = System.currentTimeMillis();
        synchronized (this) {
            while (!(mIsStarted && mEnterAnimationComplete)) {
                if ((System.currentTimeMillis() - startMs) > 5000) {
                    fail("Timeout");
                }
                try {
                    wait(100);
                } catch (InterruptedException ex) {
                    throw new AssertionError("Interrupted", ex);
                }
            }
        }
        mView.requestRenderAndWait();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mView.onResume();
        synchronized (this) {
            mIsStarted = true;
            notifyAll();
        }
    }

    @Override
    public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        synchronized (this) {
            mEnterAnimationComplete = true;
            notifyAll();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mView.onPause();
        synchronized (this) {
            mIsStarted = false;
            mEnterAnimationComplete = false;
        }
    }
}
