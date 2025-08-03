/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.service.games.testing;

import android.app.Activity;
import android.content.Intent;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class GetResultActivity extends Activity {
    public static class Result {
        public final int requestCode;
        public final int resultCode;
        public final Intent data;

        public Result(int requestCode, int resultCode, Intent data) {
            this.requestCode = requestCode;
            this.resultCode = resultCode;
            this.data = data;
        }
    }
    public LinkedBlockingQueue<Result> mResult = new LinkedBlockingQueue<>();

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mResult.offer(new Result(requestCode, resultCode, data));
        finish();
    }

    public Result getResult() {
        final Result result;
        try {
            result = mResult.poll(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (result == null) {
            throw new IllegalStateException("Activity didn't receive a Result in 20 seconds");
        }
        return result;
    }
}
