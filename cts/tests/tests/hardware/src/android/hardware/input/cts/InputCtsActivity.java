/*
 * Copyright 2015 The Android Open Source Project
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

package android.hardware.input.cts;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.function.Consumer;

public class InputCtsActivity extends Activity {
    private static final String TAG = "InputCtsActivity";

    private InputCallback mInputCallback;
    private Consumer<Boolean> mPointerCaptureCallback;
    private final ArrayList<Integer> mUnhandledKeys = new ArrayList<>();
    private boolean mConsumeGenericMotionEvents = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (!mConsumeGenericMotionEvents) {
            Log.i(TAG, "Unhandled generic motion event: "  + ev);
            return false;
        }
        if (mInputCallback != null) {
            mInputCallback.onMotionEvent(ev);
        }
        return mConsumeGenericMotionEvents;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mInputCallback != null) {
            mInputCallback.onMotionEvent(ev);
        }
        return true;
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent ev) {
        if (mInputCallback != null) {
            mInputCallback.onMotionEvent(ev);
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        // Do not handle keys in UnhandledKeys list, let it fallback
        if (mUnhandledKeys.contains(ev.getKeyCode())) {
            Log.i(TAG, "Unhandled keyEvent: "  + ev);
            return false;
        }
        if (mInputCallback != null) {
            mInputCallback.onKeyEvent(ev);
        }
        return true;
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        if (mPointerCaptureCallback != null) {
            mPointerCaptureCallback.accept(hasCapture);
        }
    }

    public void setInputCallback(InputCallback callback) {
        mInputCallback = callback;
    }

    public void setPointerCaptureCallback(Consumer<Boolean> callback) {
        mPointerCaptureCallback = callback;
    }

    public void addUnhandleKeyCode(int keyCode) {
        mUnhandledKeys.add(keyCode);
    }

    public void clearUnhandleKeyCode() {
        mUnhandledKeys.clear();
    }

    public void setConsumeGenericMotionEvents(boolean enable) {
        mConsumeGenericMotionEvents = enable;
    }
}
