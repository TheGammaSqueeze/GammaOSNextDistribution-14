/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.translation.cts;

import android.icu.util.ULocale;
import android.util.Log;
import android.view.translation.UiTranslationStateCallback;

/**
 * The test implementation for {@link UiTranslationStateCallback}.
 */
public class FakeTranslationStateCallback implements UiTranslationStateCallback {

    private static final String TAG = "MockTranslationStateCallback";

    private ULocale mSourceLocale;
    private ULocale mTargetLocale;
    private String mStartedPackageName;
    private String mResumedPackageName;
    private String mPausedPackageName;
    private String mFinishedPackageName;
    private int mStartedCallCount = 0;
    private int mResumedCallCount = 0;
    private int mPausedCallCount = 0;
    private int mFinishedCallCount = 0;

    FakeTranslationStateCallback() {
        resetStates();
    }

    void resetStates() {
        synchronized (this) {
            mSourceLocale = null;
            mTargetLocale = null;
            mStartedPackageName = null;
            mResumedPackageName = null;
            mPausedPackageName = null;
            mFinishedPackageName = null;
            mStartedCallCount = 0;
            mResumedCallCount = 0;
            mPausedCallCount = 0;
            mFinishedCallCount = 0;
        }
    }

    ULocale getStartedSourceLocale() {
        return mSourceLocale;
    }

    ULocale getStartedTargetLocale() {
        return mTargetLocale;
    }

    String getStartedPackageName() {
        return mStartedPackageName;
    }

    String getResumedPackageName() {
        return mResumedPackageName;
    }

    String getPausedPackageName() {
        return mPausedPackageName;
    }

    String getFinishedPackageName() {
        return mFinishedPackageName;
    }

    int getStartedCallCount() {
        return mStartedCallCount;
    }

    int getResumedCallCount() {
        return mResumedCallCount;
    }

    int getPausedCallCount() {
        return mPausedCallCount;
    }

    int getFinishedCallCount() {
        return mFinishedCallCount;
    }

    @Override
    public void onStarted(ULocale sourceLocale, ULocale targetLocale, String packageName) {
        Log.d(TAG, "onStarted, source=" + sourceLocale.getLanguage() + " targetLocale="
                + targetLocale.getLanguage());
        synchronized (this) {
            mSourceLocale = sourceLocale;
            mTargetLocale = targetLocale;
            mStartedPackageName = packageName;
            mStartedCallCount++;
        }
    }

    @Override
    public void onResumed(ULocale sourceLocale, ULocale targetLocale, String packageName) {
        Log.d(TAG, "onResumed, source=" + sourceLocale.getLanguage() + " targetLocale="
                + targetLocale.getLanguage() + " packageName=" + packageName);
        synchronized (this) {
            mResumedPackageName = packageName;
            mResumedCallCount++;
        }
    }

    @Override
    public void onPaused(String packageName) {
        Log.d(TAG, "onPaused");
        synchronized (this) {
            mPausedPackageName = packageName;
            mPausedCallCount++;
        }
    }

    @Override
    public void onFinished(String packageName) {
        Log.d(TAG, "onFinished");
        synchronized (this) {
            mFinishedPackageName = packageName;
            mFinishedCallCount++;
        }
    }

    // Old callback methods below shouldn't be called
    // TODO: Add a separate callback class and test to get test coverage for these methods

    @Override
    public void onStarted(ULocale sourceLocale, ULocale targetLocale) {
        throw new RuntimeException("Old callback methods shouldn't be called.");
    }

    @Override
    public void onPaused() {
        throw new RuntimeException("Old callback methods shouldn't be called.");
    }

    @Override
    public void onResumed(ULocale sourceLocale, ULocale targetLocale) {
        throw new RuntimeException("Old callback methods shouldn't be called.");
    }

    @Override
    public void onFinished() {
        throw new RuntimeException("Old callback methods shouldn't be called.");
    }
}
