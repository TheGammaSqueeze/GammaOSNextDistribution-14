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

package android.telephony.satellite.wrapper;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import com.android.internal.telephony.flags.Flags;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Encapsulates the non-terrestrial network signal strength related information. */
@FlaggedApi(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
public final class NtnSignalStrengthWrapper {

  /** Non-terrestrial network signal strength is not available. */
  @FlaggedApi(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
  public static final int NTN_SIGNAL_STRENGTH_NONE = 0;
  /** Non-terrestrial network signal strength is poor. */
  @FlaggedApi(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
  public static final int NTN_SIGNAL_STRENGTH_POOR = 1;
  /** Non-terrestrial network signal strength is moderate. */
  @FlaggedApi(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
  public static final int NTN_SIGNAL_STRENGTH_MODERATE = 2;
  /** Non-terrestrial network signal strength is good. */
  @FlaggedApi(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
  public static final int NTN_SIGNAL_STRENGTH_GOOD = 3;
  /** Non-terrestrial network signal strength is great. */
  @FlaggedApi(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
  public static final int NTN_SIGNAL_STRENGTH_GREAT = 4;
  @NtnSignalStrengthLevel private final int mLevel;

  /** @hide */
  @IntDef(prefix = "NTN_SIGNAL_STRENGTH_", value = {
      NTN_SIGNAL_STRENGTH_NONE,
      NTN_SIGNAL_STRENGTH_POOR,
      NTN_SIGNAL_STRENGTH_MODERATE,
      NTN_SIGNAL_STRENGTH_GOOD,
      NTN_SIGNAL_STRENGTH_GREAT
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface NtnSignalStrengthLevel {}

  @FlaggedApi(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
  public NtnSignalStrengthWrapper(@NonNull @NtnSignalStrengthLevel int level) {
    this.mLevel = level;
  }

  @FlaggedApi(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
  @NtnSignalStrengthLevel public int getLevel() {
    return mLevel;
  }
}
