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

package com.android.documentsui;

import static com.android.documentsui.base.Shared.EXTRA_BENCHMARK;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LauncherActivity extends Activity {
    private static final int BENCHMARK_REQUEST_CODE = 1986;

    static final Intent OPEN_DOCUMENT_INTENT = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    static {
        OPEN_DOCUMENT_INTENT.addCategory(Intent.CATEGORY_OPENABLE);
        OPEN_DOCUMENT_INTENT.putExtra(EXTRA_BENCHMARK, true);
        OPEN_DOCUMENT_INTENT.setType("*/*");
    }

    private CountDownLatch mTestCaseLatch;

    /** Starts DocumentsUi and returns the duration until the result is received. */
    long startAndWaitDocumentsUi() throws InterruptedException {
        mTestCaseLatch = new CountDownLatch(1);
        final long startTime = SystemClock.elapsedRealtime();
        startActivityForResult(OPEN_DOCUMENT_INTENT, BENCHMARK_REQUEST_CODE);
        if (!mTestCaseLatch.await(10, TimeUnit.SECONDS)) {
            throw new RuntimeException("DocumentsUi is not responding");
        }
        return SystemClock.elapsedRealtime() - startTime;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == BENCHMARK_REQUEST_CODE) {
            mTestCaseLatch.countDown();
        }
    }
}
