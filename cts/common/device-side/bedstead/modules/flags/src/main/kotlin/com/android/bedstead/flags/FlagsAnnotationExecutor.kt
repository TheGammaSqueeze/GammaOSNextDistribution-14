package com.android.bedstead.flags

import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.android.bedstead.flags.annotations.RequireFlagsDisabled
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.AnnotationExecutor
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue

class FlagsAnnotationExecutor : AnnotationExecutor {

    val flagValueProvider = DeviceFlagsValueProvider()

    override fun applyAnnotation(annotation: Annotation?) {
        if (annotation is RequireFlagsEnabled) {
            requireFlagsEnabled(annotation.value)
        } else if (annotation is RequireFlagsDisabled) {
            requireFlagsDisabled(annotation.value)
        }
    }

    private fun requireFlagsEnabled(flags: Array<out String>) {
        for (flag in flags) {
            assumeTrue(String.format("Flag %s required to be enabled, but is disabled", flag),
                flagValueProvider.getBoolean(flag))
        }
    }

    private fun requireFlagsDisabled(flags: Array<out String>) {
        for (flag in flags) {
            assumeFalse(String.format("Flag %s required to be disabled, but is enabled", flag),
                flagValueProvider.getBoolean(flag))
        }
    }

    override fun teardownShareableState() {
    }

    override fun teardownNonShareableState() {
    }
}