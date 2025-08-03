/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package android.platform.uiautomator_helpers

import android.os.Build
import java.time.Duration

private const val CUTTLEFISH = "cutf_cvm"
private const val CUTTLEFISH_FACTOR = 5L

/** Platform-dependent duration utils (specifically targeting Cuttlefish)
 *  For physical (non-emulator) devices, the timeout is unchanged,
 *  the if the Build.HARDWARE is Cuttlefish, we increase the factor by 5.
 */
object DurationUtils {

    /**
     * For non-cuttlefish platforms, leave the timeout unchanged, otherwise
     * increase the delay to compensate for slower performance.
     */
    fun Duration.platformAdjust() =
        if (Build.HARDWARE == CUTTLEFISH)
	    this.multipliedBy(CUTTLEFISH_FACTOR)
        else
	    this
}
