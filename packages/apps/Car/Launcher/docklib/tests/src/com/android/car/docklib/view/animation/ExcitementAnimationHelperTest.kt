package com.android.car.docklib.view.animation

import androidx.core.animation.Animator
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class ExcitementAnimationHelperTest {
    private val animatorMock = mock<Animator>()
    private val successCallbackMock = mock<Runnable>()
    private val failureCallbackMock = mock<Runnable>()

    @Test
    fun getAnimatorListener_onAnimationEnd_onlySuccessCallbackCalled() {
        val animatorListener =
            ExcitementAnimationHelper.getAnimatorListener(
                successCallbackMock,
                failureCallbackMock
            )

        animatorListener.onAnimationEnd(animatorMock)

        verify(successCallbackMock).run()
        verify(failureCallbackMock, never()).run()
    }

    @Test
    fun getAnimatorListener_onAnimationCancel_onlyFailureCallbackCalled() {
        val animatorListener =
            ExcitementAnimationHelper.getAnimatorListener(
                successCallbackMock,
                failureCallbackMock
            )

        animatorListener.onAnimationCancel(animatorMock)

        verify(failureCallbackMock).run()
        verify(successCallbackMock, never()).run()
    }

    @Test
    fun getAnimatorListener_onAnimationCancelAndonAnimationEnd_onlyFailureCallbackCalled() {
        val animatorListener =
            ExcitementAnimationHelper.getAnimatorListener(
                successCallbackMock,
                failureCallbackMock
            )

        animatorListener.onAnimationCancel(animatorMock)
        animatorListener.onAnimationEnd(animatorMock)

        verify(failureCallbackMock).run()
        verify(successCallbackMock, never()).run()
    }
}
