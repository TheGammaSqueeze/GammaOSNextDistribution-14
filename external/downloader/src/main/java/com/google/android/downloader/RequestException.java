// Copyright 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.downloader;

import java.io.IOException;

/**
 * An exception that occurred during creation or execution of a request; contains {@link
 * ErrorDetails} for more detail on the error.
 */
public final class RequestException extends IOException {
  private final ErrorDetails errorDetails;

  public RequestException(ErrorDetails errorDetails) {
    this.errorDetails = errorDetails;
  }

  public RequestException(ErrorDetails errorDetails, Throwable cause) {
    super(cause);
    this.errorDetails = errorDetails;
  }

  public RequestException(Throwable cause) {
    super(cause);
    this.errorDetails = ErrorDetails.create(cause.getMessage());
  }

  public RequestException(String message) {
    super(message);
    this.errorDetails = ErrorDetails.create(message);
  }

  public ErrorDetails getErrorDetails() {
    return errorDetails;
  }

  @Override
  public String getMessage() {
    return super.getMessage() + "; " + errorDetails;
  }
}
