package com.android.car.docklib.view

import android.car.media.CarMediaManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.os.UserHandle
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.docklib.TestUtils
import com.android.car.docklib.data.DockAppItem
import com.android.car.ui.shortcutspopup.CarUiShortcutsPopup
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class DockItemLongClickListenerTest {
    private val resourcesMock = mock<Resources>()
    private val userHandleMock = mock<UserHandle>()
    private val contextMock = mock<Context> {
        on { resources } doReturn resourcesMock
        on { user } doReturn userHandleMock
    }
    private val viewMock = mock<View> { on { context } doReturn contextMock }
    private val runnableMock1 = mock<Runnable>()
    private val runnableMock2 = mock<Runnable>()
    private val carUiShortcutsPopupMock = mock<CarUiShortcutsPopup>()
    private val carUiShortcutsPopupBuilderMock = mock<CarUiShortcutsPopup.Builder> {
        on { addShortcut(any<CarUiShortcutsPopup.ShortcutItem>()) } doReturn it
        on { build(any<Context>(), any<View>()) } doReturn carUiShortcutsPopupMock
    }
    private val carMediaManagerMock = mock<CarMediaManager>()

    private lateinit var dockItemLongClickListener: DockItemLongClickListener

    @Before
    fun setup() {
        dockItemLongClickListener = createDockItemLongClickListener()
    }

    @Test
    fun onLongClick_shortcutShown() {
        dockItemLongClickListener.onLongClick(viewMock)

        verify(carUiShortcutsPopupMock).show()
    }

    @Test
    fun onLongClick_typeStatic_pinShortcutItem_parameterIsItemPinnedIsTrue() {
        dockItemLongClickListener = createDockItemLongClickListener(
                TestUtils.createAppItem(type = DockAppItem.Type.STATIC)
        )

        dockItemLongClickListener.onLongClick(viewMock)

        verify(dockItemLongClickListener).createPinShortcutItem(
                any<Resources>(),
                eq(true),
                any<Runnable>(),
                any<Runnable>()
        )
    }

    @Test
    fun onLongClick_typeDynamic_pinShortcutItem_parameterIsItemPinnedIsFalse() {
        dockItemLongClickListener = createDockItemLongClickListener(
                TestUtils.createAppItem(type = DockAppItem.Type.DYNAMIC)
        )

        dockItemLongClickListener.onLongClick(viewMock)

        verify(dockItemLongClickListener).createPinShortcutItem(
                any<Resources>(),
                eq(false),
                any<Runnable>(),
                any<Runnable>()
        )
    }

    private fun createDockItemLongClickListener(
        dockAppItem: DockAppItem = TestUtils.createAppItem()
    ): DockItemLongClickListener {
        return spy(object : DockItemLongClickListener(
            dockAppItem,
            runnableMock1,
            runnableMock2,
            ComponentName("testPkg", "testClass"),
            contextMock,
            carMediaManagerMock,
            setOf()

        ) {
            override fun createCarUiShortcutsPopupBuilder(): CarUiShortcutsPopup.Builder =
                carUiShortcutsPopupBuilderMock
        })
    }
}
