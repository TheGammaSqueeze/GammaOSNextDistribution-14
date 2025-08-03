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

package android.app.stubs.shared;

import android.content.Context;
import android.view.View;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class FakeView extends View {
    private static final int BACKGROUND_COLOR = 0xFFFFFF00;
    private final BlockingQueue<String> mCalls = new ArrayBlockingQueue<>(3);

    public FakeView(Context context) {
        super(context);
        setBackgroundColor(BACKGROUND_COLOR);
    }

    @Override
    public void onCloseSystemDialogs(String reason) {
        mCalls.add(reason);
    }

    public String getNextCloseSystemDialogsCallReason(long timeoutMs) throws InterruptedException {
        return mCalls.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
