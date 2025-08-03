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

package com.android.adservices.data.adselection.datahandlers;

import android.annotation.NonNull;
import android.net.Uri;

import com.google.auto.value.AutoValue;

/** Data class representing the result of an ad selection run . */
@AutoValue
public abstract class AdSelectionResultBidAndUri {
    /** Ad selection id */
    public abstract long getAdSelectionId();

    /** Winning ad bid in this ad selection run. */
    public abstract double getWinningAdBid();

    /** Winning ad render url for this ad selection run. */
    @NonNull
    public abstract Uri getWinningAdRenderUri();

    /**
     * @return generic builder
     */
    @NonNull
    public static Builder builder() {
        return new AutoValue_AdSelectionResultBidAndUri.Builder();
    }

    /**
     * Creates a {@link AdSelectionResultBidAndUri} object using the builder.
     *
     * <p>Required for Room SQLite integration.
     */
    @NonNull
    public static AdSelectionResultBidAndUri create(
            long adSelectionId, double winningAdBid, @NonNull Uri winningAdRenderUri) {
        return builder()
                .setAdSelectionId(adSelectionId)
                .setWinningAdBid(winningAdBid)
                .setWinningAdRenderUri(winningAdRenderUri)
                .build();
    }

    /** Builder for AdSelectionResult. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets ad selection id */
        public abstract Builder setAdSelectionId(long adSelectionId);

        /** Sets the winning ad bid in this ad selection run. */
        public abstract Builder setWinningAdBid(double winningAdBid);

        /** Sets the winning ad render url for this ad selection run. */
        public abstract Builder setWinningAdRenderUri(@NonNull Uri winningAdRenderUri);

        /** Builds a {@link AdSelectionResultBidAndUri} object. */
        @NonNull
        public abstract AdSelectionResultBidAndUri build();
    }
}
