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

import com.google.android.enterprise.connectedapps.FutureWrapper;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;

/**
 * An implementation of {@link FutureResultWriter} which suppresses any {@link
 * UnavailableProfileException} instances, instead returning the specified {@code defaultValue}.
 *
 * <p>All other exceptions will be passed to the future as normal.
 */
public final class IfAvailableFutureResultWriter<E> implements FutureResultWriter<E> {

  private final FutureWrapper<E> futureWrapper;
  private final E defaultValue;

  public IfAvailableFutureResultWriter(FutureWrapper<E> futureWrapper, E defaultValue) {
    if (futureWrapper == null) {
      throw new NullPointerException();
    }
    this.futureWrapper = futureWrapper;
    this.defaultValue = defaultValue;
  }

  @Override
  public void onSuccess(E result) {
    this.futureWrapper.onResult(result);
  }

  @Override
  public void onFailure(Throwable throwable) {
    if (throwable instanceof UnavailableProfileException) {
      this.futureWrapper.onResult(defaultValue);
    } else {
      this.futureWrapper.onException(throwable);
    }
  }
}
