/*
 * Copyright (C) 2022-2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package vendor.lineage.health;

@Backing(type="int") @VintfStability
enum ChargingControlSupportedMode {
    /**
     * The device supports control charging through an on/off toggle.
     */
    TOGGLE = 1 << 0,

    /**
     * The device allows battery bypass when charging stops.
     */
    BYPASS = 1 << 1,

    /**
     * The device supports control charging by specifying the deadline
     */
    DEADLINE = 1 << 2,
}
