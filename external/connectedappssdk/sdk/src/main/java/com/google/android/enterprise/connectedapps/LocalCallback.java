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
package com.google.android.enterprise.connectedapps;

import android.os.Bundle;

/**
 * Interface used by callbacks used when calling {@link CrossProfileSender#callAsync(long, int,
 * Bundle, LocalCallback, Object, long)}.
 */
public interface LocalCallback {

  /**
   * Pass a result into the callback.
   *
   * @param methodIdentifier The method being responded to.
   * @param params A Bundle containing the result under the key "result".
   */
  void onResult(int methodIdentifier, Bundle params);

  /**
   * Pass an exception into the callback.
   *
   * @param exception A Bundle containing the exception under the key "throwable"
   */
  void onException(Bundle exception);
}
