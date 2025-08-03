/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.textclassifier.downloader;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import com.android.textclassifier.common.TextClassifierServiceExecutors;
import com.android.textclassifier.common.base.TcLog;

/** Service to expose IModelDownloaderService. */
public final class ModelDownloaderService extends Service {
  private static final String TAG = "ModelDownloaderService";

  private IBinder iBinder;

  @Override
  public void onCreate() {
    super.onCreate();
    this.iBinder =
        new ModelDownloaderServiceImpl(
            /* bgExecutorService= */ TextClassifierServiceExecutors.getDownloaderExecutor(),
            /* transportExecutorService= */ TextClassifierServiceExecutors.getNetworkIOExecutor());
  }

  @Override
  public IBinder onBind(Intent intent) {
    TcLog.d(TAG, "Binding to ModelDownloadService");
    return iBinder;
  }
}
