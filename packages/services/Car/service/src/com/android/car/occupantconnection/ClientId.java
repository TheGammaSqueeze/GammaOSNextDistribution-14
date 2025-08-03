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
package com.android.car.occupantconnection;

import android.annotation.NonNull;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.text.TextUtils;

import java.util.Objects;

/** A class used to identify a client. */
final class ClientId {

    /** The occupant zone that the client runs in. */
    public final OccupantZoneInfo occupantZone;
    /** The user ID of the client. */
    public final int userId;
    /** The package name of the client. */
    public final String packageName;

    // TODO(b/275370184): use factory method pattern.
    public ClientId(@NonNull OccupantZoneInfo occupantZone, int userId,
            @NonNull String packageName) {
        this.occupantZone = Objects.requireNonNull(occupantZone, "occupantZone cannot be null");
        this.userId = userId;
        this.packageName = Objects.requireNonNull(packageName, "packageName cannot be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClientId)) {
            return false;
        }
        ClientId other = (ClientId) o;
        return occupantZone.equals(other.occupantZone) && userId == other.userId
                && TextUtils.equals(packageName, other.packageName);

    }

    @Override
    public int hashCode() {
        return Objects.hash(occupantZone, userId, packageName);
    }

    @Override
    public String toString() {
        return "ClientId[occupantZone=" + occupantZone + ", userId=" + userId
                + ", packageName=" + packageName + "]";
    }
}
