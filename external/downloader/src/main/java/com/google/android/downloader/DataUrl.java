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

import com.google.common.base.Splitter;
import com.google.common.io.BaseEncoding;
import java.util.List;

/**
 * Utility for handling data URLs. See the MDN for documentation of the format:
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/Data_URIs
 *
 * <p>See http://www.ietf.org/rfc/rfc2397.txt for the formal specification of the data URL scheme.
 */
class DataUrl {
  private static final BaseEncoding BASE64_URL = BaseEncoding.base64Url();
  private static final BaseEncoding BASE64 = BaseEncoding.base64();
  private static final Splitter SEMICOLON_SPLITTER = Splitter.on(';');

  private final byte[] data;
  private final String mimeType;

  /** Thrown to indicate that the input URL could not be parsed as an RFC2397 data URL. */
  static class DataUrlException extends IllegalArgumentException {
    DataUrlException(String message) {
      super(message);
    }

    DataUrlException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private DataUrl(byte[] data, String mimeType) {
    this.data = data;
    this.mimeType = mimeType;
  }

  byte[] data() {
    return data;
  }

  String mimeType() {
    return mimeType;
  }

  /**
   * Decodes a data url, returning the payload as a {@link DataUrl} instance.
   *
   * <p>The syntax of a valid data URL is as follows: data:[&lt;mediatype&gt;][;base64],&lt;data&gt;
   * This method will assert that the provided URL is conformant, and throws an {@link
   * DataUrlException} if the URL is invalid or can't be parsed.
   *
   * <p>Note that this method requires the data URL to be encoded in base64, and assumes the data is
   * binary. Data URLs may be text/plain data, but that is not supported by this method.
   *
   * @param url the data url to decode
   */
  static DataUrl parseFromString(String url) throws DataUrlException {
    if (!url.startsWith("data:")) {
      throw new DataUrlException("URL doesn't have the data scheme");
    }

    int commaPos = url.indexOf(',');
    if (commaPos == -1) {
      throw new DataUrlException("Comma not found in data URL");
    }
    String data = url.substring(commaPos + 1);
    List<String> options = SEMICOLON_SPLITTER.splitToList(url.substring(5, commaPos));
    if (options.size() < 2) {
      throw new DataUrlException("Insufficient number of options for data URL");
    }
    String mimeType = options.get(0);
    String encoding = options.get(1);
    if (!encoding.equals("base64")) {
      throw new DataUrlException("Invalid encoding: " + encoding + ", only base64 is supported");
    }

    try {
      return new DataUrl(BASE64_URL.decode(data), mimeType);
    } catch (IllegalArgumentException unused) {
      // If the URL-safe base64 encoding doesn't work, try the vanilla base64 encoding. Ideally
      // users would only use web-safe data URIs, but in many environments regular base64 payloads
      // can be used without issue.
      try {
        return new DataUrl(BASE64.decode(data), mimeType);
      } catch (IllegalArgumentException e) {
        throw new DataUrlException("Invalid base64 payload in data URL", e);
      }
    }
  }
}
