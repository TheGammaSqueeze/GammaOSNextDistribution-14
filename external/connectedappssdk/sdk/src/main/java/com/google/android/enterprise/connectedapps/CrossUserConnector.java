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

import android.content.Context;
import com.google.android.enterprise.connectedapps.annotations.AvailabilityRestrictions;
import com.google.android.enterprise.connectedapps.annotations.CrossUser;
import com.google.android.enterprise.connectedapps.annotations.CustomUserConnector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** The default {@link UserConnector} used if none is specified in a {@link CrossUser} type. */
@CustomUserConnector
public interface CrossUserConnector extends UserConnector {
  /** Builder for {@link CrossUserConnector} instances. */
  final class Builder {

    private Builder(Context context) {
      implBuilder.setContext(context);
    }

    private final AbstractUserConnector.Builder implBuilder =
        new AbstractUserConnector.Builder()
            .setServiceClassName(
                "com.google.android.enterprise.connectedapps.CrossUserConnector_Service")
            .setAvailabilityRestrictions(AvailabilityRestrictions.DEFAULT);

    /**
     * Use an alternative {@link ScheduledExecutorService}.
     *
     * <p>Defaults to {@link Executors#newSingleThreadScheduledExecutor()}.
     *
     * <p>This {@link ScheduledExecutorService} must be single threaded or sequential. Failure to do
     * so will result in undefined behavior when using the SDK.
     */
    public Builder setScheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
      implBuilder.setScheduledExecutorService(scheduledExecutorService);
      return this;
    }

    /**
     * Specify which set of restrictions should be applied to checking availability.
     *
     * <p>Defaults to {@link AvailabilityRestrictions#DEFAULT}, which requires that a user be
     * running, unlocked, and not in quiet mode
     */
    public Builder setAvailabilityRestrictions(AvailabilityRestrictions availabilityRestrictions) {
      implBuilder.setAvailabilityRestrictions(availabilityRestrictions);
      return this;
    }

    /** Instantiate the {@link CrossProfileConnector} for the given settings. */
    public CrossUserConnector build() {
      return new CrossUserConnectorImpl(implBuilder);
    }
  }

  static Builder builder(Context context) {
    return new Builder(context);
  }
}
