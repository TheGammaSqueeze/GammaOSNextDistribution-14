package com.android.car.docklib.view

import android.content.ClipData
import android.content.ClipDescription
import android.content.ComponentName
import android.content.res.Resources
import android.graphics.Point
import android.view.DragEvent
import android.view.DragEvent.ACTION_DRAG_ENTERED
import android.view.DragEvent.ACTION_DRAG_EXITED
import android.view.DragEvent.ACTION_DRAG_STARTED
import android.view.DragEvent.ACTION_DROP
import android.view.SurfaceControl
import android.view.View
import androidx.core.animation.ValueAnimator
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.docklib.R
import com.android.car.docklib.view.DockDragListener.Companion.APP_ITEM_DRAG_TAG
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.isNull
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DockDragListenerTest {
    private val resourcesMock = mock<Resources> {
        on { getInteger(eq(R.integer.drop_animation_scale_down_duration_ms)) } doReturn 0
        on { getInteger(eq(R.integer.drop_animation_scale_up_duration_ms)) } doReturn 0
    }
    private val viewMock = mock<View> {}
    private val dragEventMock = mock<DragEvent> {}
    private val clipDescriptionMock = mock<ClipDescription> {}
    private val clipDataMock = mock<ClipData> {}
    private val clipDataItemMock = mock<ClipData.Item> {}
    private val surfaceControlMock = mock<SurfaceControl> {}
    private val surfaceControlTransactionMock = mock<SurfaceControl.Transaction> {}
    private val callbackMock = mock<DockDragListener.Callback> {}
    private val valueAnimatorMock = mock<ValueAnimator> {}
    private val booleanConsumerMock = mock<Consumer<Boolean>> {}
    private val componentNameCaptor = argumentCaptor<ComponentName>()
    private var dockDragListener = object : DockDragListener(resourcesMock, callbackMock) {
        override fun getAnimator(
            surfaceControl: SurfaceControl,
            fromX: Float,
            fromY: Float,
            toX: Float,
            toY: Float,
            fromScaleX: Float,
            fromScaleY: Float,
            toScaleX: Float,
            toScaleY: Float,
            animationDuration: Long
        ): ValueAnimator {
            return valueAnimatorMock
        }
    }

    companion object {
        private val VALID_COMPONENT_NAME = ComponentName(
            "com.android.car.docklib.view",
            DockDragListenerTest::javaClass.name
        ).flattenToString()
    }

    @Test
    fun onDrag_ACTION_DRAG_STARTED_clipDescriptionNameInvalid_returnFalse() {
        val invalidDragTag = "$APP_ITEM_DRAG_TAG-gibberish"
        whenever(dragEventMock.action) doReturn ACTION_DRAG_STARTED
        whenever(clipDescriptionMock.label) doReturn invalidDragTag
        whenever(dragEventMock.clipDescription) doReturn clipDescriptionMock

        val ret = dockDragListener.onDrag(viewMock, dragEventMock)

        assertThat(ret).isFalse()
    }

    @Test
    fun onDrag_ACTION_DRAG_STARTED_clipDescriptionNameValid_returnTrue() {
        val validDragTag = APP_ITEM_DRAG_TAG
        whenever(dragEventMock.action) doReturn ACTION_DRAG_STARTED
        whenever(clipDescriptionMock.label) doReturn validDragTag
        whenever(dragEventMock.clipDescription) doReturn clipDescriptionMock

        val ret = dockDragListener.onDrag(viewMock, dragEventMock)

        assertThat(ret).isTrue()
    }

    @Test
    fun onDrag_ACTION_DRAG_ENTERED_exciteViewCallbackTriggered() {
        whenever(dragEventMock.action) doReturn ACTION_DRAG_ENTERED

        dockDragListener.onDrag(viewMock, dragEventMock)

        verify(callbackMock).exciteView()
    }

    @Test
    fun onDrag_ACTION_DRAG_EXITED_resetViewCallbackTriggered() {
        whenever(dragEventMock.action) doReturn ACTION_DRAG_EXITED

        dockDragListener.onDrag(viewMock, dragEventMock)

        verify(callbackMock).resetView()
    }

    @Test
    fun onDrag_ACTION_DROP_noClipData_returnFalse() {
        whenever(dragEventMock.action) doReturn ACTION_DROP
        whenever(dragEventMock.clipData) doReturn clipDataMock
        whenever(clipDataMock.getItemAt(any())) doReturn null

        val ret = dockDragListener.onDrag(viewMock, dragEventMock)

        assertThat(ret).isFalse()
    }

    @Test
    fun onDrag_ACTION_DROP_noClipData_resetViewCallbackTriggered() {
        whenever(dragEventMock.action) doReturn ACTION_DROP
        whenever(dragEventMock.clipData) doReturn clipDataMock
        whenever(clipDataMock.getItemAt(any())) doReturn null

        dockDragListener.onDrag(viewMock, dragEventMock)

        verify(callbackMock).resetView()
    }

    @Test
    fun onDrag_ACTION_DROP_clipDataWithNoText_returnFalse() {
        whenever(dragEventMock.action) doReturn ACTION_DROP
        whenever(clipDataItemMock.text) doReturn null
        whenever(clipDataMock.getItemAt(eq(0))) doReturn clipDataItemMock
        whenever(dragEventMock.clipData) doReturn clipDataMock

        val ret = dockDragListener.onDrag(viewMock, dragEventMock)

        assertThat(ret).isFalse()
    }

    @Test
    fun onDrag_ACTION_DROP_clipDataWithNoText_resetViewCallbackTriggered() {
        whenever(dragEventMock.action) doReturn ACTION_DROP
        whenever(clipDataItemMock.text) doReturn null
        whenever(clipDataMock.getItemAt(eq(0))) doReturn clipDataItemMock
        whenever(dragEventMock.clipData) doReturn clipDataMock

        dockDragListener.onDrag(viewMock, dragEventMock)

        verify(callbackMock).resetView()
    }

    @Test
    fun onDrag_ACTION_DROP_invalidComponentName_returnFalse() {
        val invalidComponentName = "invalidComponentName"
        whenever(dragEventMock.action) doReturn ACTION_DROP
        whenever(clipDataItemMock.text) doReturn invalidComponentName
        whenever(clipDataMock.getItemAt(eq(0))) doReturn clipDataItemMock
        whenever(dragEventMock.clipData) doReturn clipDataMock

        val ret = dockDragListener.onDrag(viewMock, dragEventMock)

        assertThat(ret).isFalse()
    }

    @Test
    fun onDrag_ACTION_DROP_invalidComponentName_resetViewCallbackTriggered() {
        val invalidComponentName = "invalidComponentName"
        whenever(dragEventMock.action) doReturn ACTION_DROP
        whenever(clipDataItemMock.text) doReturn invalidComponentName
        whenever(clipDataMock.getItemAt(eq(0))) doReturn clipDataItemMock
        whenever(dragEventMock.clipData) doReturn clipDataMock

        dockDragListener.onDrag(viewMock, dragEventMock)

        verify(callbackMock).resetView()
    }

    @Test
    fun onDrag_ACTION_DROP_noDragSurface_returnFalse() {
        whenever(dragEventMock.action) doReturn ACTION_DROP
        whenever(clipDataItemMock.text) doReturn VALID_COMPONENT_NAME
        whenever(clipDataMock.getItemAt(eq(0))) doReturn clipDataItemMock
        whenever(dragEventMock.clipData) doReturn clipDataMock
        whenever(dragEventMock.dragSurface) doReturn null

        val ret = dockDragListener.onDrag(viewMock, dragEventMock)

        assertThat(ret).isFalse()
    }

    @Test
    fun onDrag_ACTION_DROP_noDragSurface_dragAcceptedCallbackTriggered() {
        whenever(dragEventMock.action) doReturn ACTION_DROP
        whenever(clipDataItemMock.text) doReturn VALID_COMPONENT_NAME
        whenever(clipDataMock.getItemAt(eq(0))) doReturn clipDataItemMock
        whenever(dragEventMock.clipData) doReturn clipDataMock
        whenever(dragEventMock.dragSurface) doReturn null

        dockDragListener.onDrag(viewMock, dragEventMock)

        verify(callbackMock).dropSuccessful(componentNameCaptor.capture(), isNull())
        assertThat(componentNameCaptor.firstValue).isNotNull()
        assertThat(componentNameCaptor.firstValue.flattenToString()).isEqualTo(VALID_COMPONENT_NAME)
    }

    @Test
    fun onDrag_ACTION_DROP_validPositionComponentNameDragSurface_returnTrue() {
        whenever(dragEventMock.action) doReturn ACTION_DROP
        whenever(clipDataItemMock.text) doReturn VALID_COMPONENT_NAME
        whenever(clipDataMock.getItemAt(eq(0))) doReturn clipDataItemMock
        whenever(dragEventMock.clipData) doReturn clipDataMock
        whenever(dragEventMock.dragSurface) doReturn surfaceControlMock
        whenever(callbackMock.getDropContainerLocation()).thenReturn(Point(0, 0))
        whenever(callbackMock.getDropLocation()).thenReturn(Point(0, 0))
        whenever(callbackMock.getDropHeight()).thenReturn(10f)
        whenever(callbackMock.getDropWidth()).thenReturn(10f)

        val ret = dockDragListener.onDrag(viewMock, dragEventMock)

        assertThat(ret).isTrue()
    }

    @Test
    fun onDrag_ACTION_DROP_validPositionComponentNameDragSurface_animationStarted() {
        whenever(dragEventMock.action) doReturn ACTION_DROP
        whenever(clipDataItemMock.text) doReturn VALID_COMPONENT_NAME
        whenever(clipDataMock.getItemAt(eq(0))) doReturn clipDataItemMock
        whenever(dragEventMock.clipData) doReturn clipDataMock
        whenever(dragEventMock.dragSurface) doReturn surfaceControlMock
        whenever(callbackMock.getDropContainerLocation()).thenReturn(Point(0, 0))
        whenever(callbackMock.getDropLocation()).thenReturn(Point(0, 0))
        whenever(callbackMock.getDropHeight()).thenReturn(10f)
        whenever(callbackMock.getDropWidth()).thenReturn(10f)

        dockDragListener.onDrag(viewMock, dragEventMock)

        verify(valueAnimatorMock).start()
    }

    @Test
    fun getAnimator_onAnimationUpdate_surfaceControlSetToNewPosition() {
        val updatedX = 10f
        val updatedY = 20f
        val updatedScaleX = 0.9f
        val updatedScaleY = 0.9f
        whenever(
            valueAnimatorMock.getAnimatedValue(DockDragListener.PVH_POSITION_X)
        ) doReturn updatedX
        whenever(
            valueAnimatorMock.getAnimatedValue(DockDragListener.PVH_POSITION_Y)
        ) doReturn updatedY
        whenever(
            valueAnimatorMock.getAnimatedValue(DockDragListener.PVH_SCALE_X)
        ) doReturn updatedScaleX
        whenever(
            valueAnimatorMock.getAnimatedValue(DockDragListener.PVH_SCALE_Y)
        ) doReturn updatedScaleY
        whenever(
            surfaceControlTransactionMock.setPosition(any<SurfaceControl>(), anyFloat(), anyFloat())
        ) doReturn surfaceControlTransactionMock
        whenever(
            surfaceControlTransactionMock.setScale(any<SurfaceControl>(), anyFloat(), anyFloat())
        ) doReturn surfaceControlTransactionMock

        val updateListener = dockDragListener.getAnimatorUpdateListener(
            surfaceControlMock,
            surfaceControlTransactionMock
        )
        updateListener.onAnimationUpdate(valueAnimatorMock)

        verify(surfaceControlTransactionMock)
            .setPosition(eq(surfaceControlMock), eq(updatedX), eq(updatedY))
        verify(surfaceControlTransactionMock).apply()
    }

    @Test
    fun getAnimator_onAnimationUpdate_surfaceControlSetToNewScale() {
        val updatedX = 10f
        val updatedY = 20f
        val updatedScaleX = 0.9f
        val updatedScaleY = 0.9f
        whenever(
            valueAnimatorMock.getAnimatedValue(DockDragListener.PVH_POSITION_X)
        ) doReturn updatedX
        whenever(
            valueAnimatorMock.getAnimatedValue(DockDragListener.PVH_POSITION_Y)
        ) doReturn updatedY
        whenever(
            valueAnimatorMock.getAnimatedValue(DockDragListener.PVH_SCALE_X)
        ) doReturn updatedScaleX
        whenever(
            valueAnimatorMock.getAnimatedValue(DockDragListener.PVH_SCALE_Y)
        ) doReturn updatedScaleY
        whenever(
            surfaceControlTransactionMock.setPosition(any<SurfaceControl>(), anyFloat(), anyFloat())
        ) doReturn surfaceControlTransactionMock
        whenever(
            surfaceControlTransactionMock.setScale(any<SurfaceControl>(), anyFloat(), anyFloat())
        ) doReturn surfaceControlTransactionMock

        val updateListener = dockDragListener.getAnimatorUpdateListener(
            surfaceControlMock,
            surfaceControlTransactionMock
        )
        updateListener.onAnimationUpdate(valueAnimatorMock)

        verify(surfaceControlTransactionMock)
            .setScale(eq(surfaceControlMock), eq(updatedScaleX), eq(updatedScaleY))
        verify(surfaceControlTransactionMock).apply()
    }

    @Test
    fun getAnimatorListener_onAnimationEnd_callbackTriggered() {
        whenever(surfaceControlMock.isValid) doReturn true

        val listener = dockDragListener.getAnimatorListener(booleanConsumerMock)
        listener.onAnimationEnd(valueAnimatorMock)

        verify(booleanConsumerMock).accept(
            eq(false) // isCancelled
        )
    }

    @Test
    fun getAnimatorListener_onAnimationCancel_callbackTriggered() {
        whenever(surfaceControlMock.isValid) doReturn true

        val listener = dockDragListener.getAnimatorListener(booleanConsumerMock)
        listener.onAnimationCancel(valueAnimatorMock)

        verify(booleanConsumerMock).accept(
            eq(true) // isCancelled
        )
    }

    @Test
    fun getAnimatorListener_onAnimationCancelAndonAnimationEnd_callbackTriggeredOnce() {
        whenever(surfaceControlMock.isValid) doReturn true

        val listener = dockDragListener.getAnimatorListener(booleanConsumerMock)
        listener.onAnimationCancel(valueAnimatorMock)
        listener.onAnimationEnd(valueAnimatorMock)

        verify(booleanConsumerMock).accept(
            eq(true) // isCancelled
        )
    }
}
