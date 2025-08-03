package com.android.healthconnect.controller.dataentries

import com.android.healthconnect.controller.shared.DataType
import java.time.Instant

/** OnDeleteListener for Data entries. */
interface OnDeleteEntryListener {
    fun onDeleteEntry(
        id: String,
        dataType: DataType,
        index: Int,
        startTime: Instant? = null,
        endTime: Instant? = null
    )
}
