/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.enterprise.connectedapps.internal;

import com.google.android.enterprise.connectedapps.ExceptionCallback;
import com.google.android.enterprise.connectedapps.Profile;

/**
 * An {@link ExceptionCallback} which marks a {@link CrossProfileCallbackMultiMerger} result missing
 * when an exception is received.
 */
public class CallbackMergerExceptionCallback<R> implements ExceptionCallback {

  private final Profile profileId;
  private final CrossProfileCallbackMultiMerger<R> merger;

  public CallbackMergerExceptionCallback(
      Profile profileId, CrossProfileCallbackMultiMerger<R> merger) {
    if (profileId == null || merger == null) {
      throw new NullPointerException();
    }
    this.profileId = profileId;
    this.merger = merger;
  }

  @Override
  public void onException(Throwable throwable) {
    merger.missingResult(profileId);
  }
}
