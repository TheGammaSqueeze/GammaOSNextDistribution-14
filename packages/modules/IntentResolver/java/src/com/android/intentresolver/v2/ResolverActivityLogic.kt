package com.android.intentresolver.v2

import androidx.activity.ComponentActivity
import androidx.annotation.OpenForTesting

/** Activity logic for [ResolverActivity]. */
@OpenForTesting
open class ResolverActivityLogic(
    tag: String,
    activity: ComponentActivity,
    onWorkProfileStatusUpdated: () -> Unit,
) :
    ActivityLogic,
    CommonActivityLogic by CommonActivityLogicImpl(
        tag,
        activity,
        onWorkProfileStatusUpdated,
    )
