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

import java.util.Set;

/** Abstraction of a request engine, which is a high-level interface into a network stack. */
public interface UrlEngine {
  /**
   * Creates a request for the given URL. The request is returned as a {@link UrlRequest.Builder},
   * so that callers may further customize the request. Callers must call {@link UrlRequest#send} to
   * execute the request.
   *
   * @param url the URL to connect to. Internal code will assume this is a valid, well-formed URL,
   *     so client code must have already verified the URL.
   */
  UrlRequest.Builder createRequest(String url);

  /**
   * Returns the set of URL schemes supported by this url request factory instance. Network stacks
   * that connect over HTTP will likely return the set {"http", "https"}, but there may be many
   * other types of schemes available (e.g. "file" or "data").
   */
  Set<String> supportedSchemes();
}
