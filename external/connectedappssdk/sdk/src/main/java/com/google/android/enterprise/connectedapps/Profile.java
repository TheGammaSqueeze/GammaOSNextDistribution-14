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


/** A profile which can be interacted with using the profile-aware SDK. */
public final class Profile {

  private static final int CURRENT_PROFILE_LEGACY_IDENTIFIER = 0;
  private static final int OTHER_PROFILE_LEGACY_IDENTIFIER = 1;

  /** Recreate a {@link Profile} previously serialised using {@link #asInt()}. */
  public static Profile fromInt(int legacyProfileIdentifier) {
    return new Profile(legacyProfileIdentifier);
  }

  // 0 for "current profile", 1 for "other profile"
  // TODO(142042055): Refactor ProfileId so it is stable across profiles, so it can be
  //  stored, and so it can represent profile types as well as specific profiles
  private final int legacyProfileIdentifier;

  private Profile(int legacyProfileIdentifier) {
    this.legacyProfileIdentifier = legacyProfileIdentifier;
  }

  /** Returns true if this {@link Profile} refers to the current profile. */
  public boolean isCurrent() {
    return legacyProfileIdentifier == CURRENT_PROFILE_LEGACY_IDENTIFIER;
  }

  /** Returns true if this {@link Profile} refers to the other profile. */
  public boolean isOther() {
    return legacyProfileIdentifier == OTHER_PROFILE_LEGACY_IDENTIFIER;
  }

  public int asInt() {
    return legacyProfileIdentifier;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Profile that = (Profile) o;
    return legacyProfileIdentifier == that.legacyProfileIdentifier;
  }

  @Override
  public int hashCode() {
    return legacyProfileIdentifier;
  }
}
