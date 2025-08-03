/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.cujcommon.cts;

import com.google.auto.value.AutoValue;

import java.util.List;

/**
 * An AutoValue class to  create cuj test params.
 */
@AutoValue
public abstract class CujTestParam {


  /**
   * Returns a builder for {@link CujTestParam}.
   */
  public static Builder builder() {
    return new AutoValue_CujTestParam.Builder();
  }

  /**
   * Return test mediaUrls.
   */
  public abstract List<String> mediaUrls();

  /**
   * Return test timeoutMilliSeconds.
   */
  public abstract long timeoutMilliSeconds();

  /**
   * Return test playerListener.
   */
  public abstract PlayerListener playerListener();

  /**
   * A builder for {@link CujTestParam}.
   */
  @AutoValue.Builder
  public abstract static class Builder {

    /**
     * Sets test mediaUrls.
     */
    public abstract Builder setMediaUrls(List<String> mediaUrls);

    /**
     * Sets test timeoutMilliSeconds.
     */
    public abstract Builder setTimeoutMilliSeconds(long timeoutMilliSeconds);

    /**
     * Sets test playerListener.
     */
    public abstract Builder setPlayerListener(PlayerListener playerListener);

    /**
     * Returns a newly-constructed {@link CujTestParam}.
     */
    public abstract CujTestParam build();
  }
}
