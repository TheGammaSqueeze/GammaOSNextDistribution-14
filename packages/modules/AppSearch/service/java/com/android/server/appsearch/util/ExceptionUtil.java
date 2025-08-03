/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.appsearch.util;

/**
 * Utilities for handling exceptions.
 *
 * @hide
 */
public final class ExceptionUtil {

  /**
   * {@link RuntimeException} will be rethrown if {@link #isItOkayToRethrowException()}
   * returns true.
   */
  public static final void handleException(Exception e) {
    if (isItOkayToRethrowException() && e instanceof RuntimeException) {
      rethrowRuntimeException((RuntimeException) e);
    }
  }

  /** Returns whether it is OK to rethrow exceptions from this entrypoint. */
  private static final boolean isItOkayToRethrowException() {
    return false;
  }

  /**
   * A helper method to rethrow {@link RuntimeException}.
   *
   * <p>We use this to enforce exception type and assure the compiler/linter that the exception is
   * indeed {@link RuntimeException} and can be rethrown safely.
   */
  private static final void rethrowRuntimeException(RuntimeException e) {
    throw e;
  }

  private ExceptionUtil() {}
}
