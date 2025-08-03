package com.android.wallpaper.util.wallpaperconnection

import android.app.WallpaperColors
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.service.wallpaper.IWallpaperConnection
import android.service.wallpaper.IWallpaperEngine
import android.service.wallpaper.IWallpaperService
import android.util.Log
import android.view.SurfaceView
import android.view.WindowManager
import com.android.wallpaper.util.WallpaperConnection
import com.android.wallpaper.util.WallpaperConnection.WhichPreview
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

class WallpaperEngineConnection(
    private val displayMetrics: Point,
    private val whichPreview: WhichPreview,
) : IWallpaperConnection.Stub() {

    var engine: IWallpaperEngine? = null
    private var engineContinuation: CancellableContinuation<IWallpaperEngine>? = null
    private var listener: WallpaperEngineConnectionListener? = null

    suspend fun getEngine(
        wallpaperService: IWallpaperService,
        destinationFlag: Int,
        surfaceView: SurfaceView,
    ): IWallpaperEngine {
        return engine
            ?: suspendCancellableCoroutine { k: CancellableContinuation<IWallpaperEngine> ->
                engineContinuation = k
                attachEngineConnection(
                    wallpaperEngineConnection = this,
                    wallpaperService = wallpaperService,
                    destinationFlag = destinationFlag,
                    surfaceView = surfaceView,
                )
            }
    }

    override fun attachEngine(engine: IWallpaperEngine?, displayId: Int) {
        // Note that the engine in attachEngine callback is different from the one in engineShown
        // callback, which is called after this attachEngine callback. We use the engine reference
        // passed in attachEngine callback for WallpaperEngineConnection.
        this.engine = engine
        engine?.apply {
            setVisibility(true)
            resizePreview(Rect(0, 0, displayMetrics.x, displayMetrics.y))
            requestWallpaperColors()
        }
    }

    override fun engineShown(engine: IWallpaperEngine?) {
        if (engine != null) {
            dispatchWallpaperCommand(engine)
            // Note that the engines from the attachEngine and engineShown callbacks are different
            // and we only use the reference of engine from the attachEngine callback.
            this.engine?.let { engineContinuation?.resumeWith(Result.success(it)) }
        }
    }

    private fun dispatchWallpaperCommand(engine: IWallpaperEngine) {
        val bundle = Bundle()
        bundle.putInt(WHICH_PREVIEW, whichPreview.value)
        try {
            engine.dispatchWallpaperCommand(COMMAND_PREVIEW_INFO, 0, 0, 0, bundle)
        } catch (e: RemoteException) {
            Log.e(TAG, "Error dispatching wallpaper command: $whichPreview")
        }
    }

    override fun onLocalWallpaperColorsChanged(
        area: RectF?,
        colors: WallpaperColors?,
        displayId: Int
    ) {
        // Do nothing intended.
    }

    override fun onWallpaperColorsChanged(colors: WallpaperColors?, displayId: Int) {
        listener?.onWallpaperColorsChanged(colors, displayId)
    }

    override fun setWallpaper(name: String?): ParcelFileDescriptor {
        TODO("Not yet implemented")
    }

    fun setListener(listener: WallpaperEngineConnectionListener) {
        this.listener = listener
    }

    fun removeListener() {
        this.listener = null
    }

    companion object {
        const val TAG = "WallpaperEngineConnection"

        const val COMMAND_PREVIEW_INFO = "android.wallpaper.previewinfo"
        const val WHICH_PREVIEW = "which_preview"

        /**
         * Before Android U, [IWallpaperService.attach] has no input of destinationFlag. We do
         * method reflection to probe if the service from the external app is using a pre-U API;
         * otherwise, we use the new one.
         */
        private fun attachEngineConnection(
            wallpaperEngineConnection: WallpaperEngineConnection,
            wallpaperService: IWallpaperService,
            destinationFlag: Int,
            surfaceView: SurfaceView,
        ) {
            try {
                val preUMethod: Method =
                    wallpaperService.javaClass.getMethod(
                        "attach",
                        IWallpaperConnection::class.java,
                        IBinder::class.java,
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Rect::class.java,
                        Int::class.javaPrimitiveType
                    )
                preUMethod.invoke(
                    wallpaperService,
                    wallpaperEngineConnection,
                    surfaceView.windowToken,
                    WindowManager.LayoutParams.TYPE_APPLICATION_MEDIA,
                    true,
                    surfaceView.width,
                    surfaceView.height,
                    Rect(0, 0, 0, 0),
                    surfaceView.display.displayId
                )
            } catch (e: Exception) {
                when (e) {
                    is NoSuchMethodException,
                    is InvocationTargetException,
                    is IllegalAccessException ->
                        wallpaperService.attach(
                            wallpaperEngineConnection,
                            surfaceView.windowToken,
                            WindowManager.LayoutParams.TYPE_APPLICATION_MEDIA,
                            true,
                            surfaceView.width,
                            surfaceView.height,
                            Rect(0, 0, 0, 0),
                            surfaceView.display.displayId,
                            destinationFlag,
                            null,
                        )
                    else -> throw e
                }
            }
        }
    }

    /** Interface to be notified of connect/disconnect events from [WallpaperConnection] */
    interface WallpaperEngineConnectionListener {
        /** Called after the wallpaper color is available or updated. */
        fun onWallpaperColorsChanged(colors: WallpaperColors?, displayId: Int) {}
    }
}
