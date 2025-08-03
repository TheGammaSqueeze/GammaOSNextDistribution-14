package com.android.managedprovisioning.annotations

@RequiresOptIn(
    message =
    "Marks API meant only to facilitate legacy code integration. " +
            "Should be avoided whenever possible.",
    level = RequiresOptIn.Level.ERROR,
)
annotation class LegacyApi