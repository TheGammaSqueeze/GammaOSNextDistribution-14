package com.android.wallpaper.util.wallpaperconnection

import android.app.WallpaperInfo
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Matrix
import android.graphics.Point
import android.os.RemoteException
import android.service.wallpaper.IWallpaperEngine
import android.service.wallpaper.IWallpaperService
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Display
import android.view.SurfaceControl
import android.view.SurfaceView
import android.view.View
import com.android.wallpaper.picker.data.WallpaperModel.LiveWallpaperModel
import com.android.wallpaper.picker.di.modules.MainDispatcher
import com.android.wallpaper.util.ScreenSizeCalculator
import com.android.wallpaper.util.WallpaperConnection
import com.android.wallpaper.util.WallpaperConnection.WhichPreview
import com.android.wallpaper.util.wallpaperconnection.WallpaperConnectionUtils.getKey
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WallpaperConnectionUtils {

    const val TAG = "WallpaperConnectionUtils"

    // engineMap and surfaceControlMap are used for disconnecting wallpaper services.
    private val engineMap =
        mutableMapOf<String, Deferred<Pair<ServiceConnection, WallpaperEngineConnection>>>()
    // Note that when one wallpaper engine's render is mirrored to a new surface view, we call
    // engine.mirrorSurfaceControl() and will have a new surface control instance.
    private val surfaceControlMap = mutableMapOf<String, MutableList<SurfaceControl>>()

    private val mutex = Mutex()

    /** Only call this function when the surface view is attached. */
    suspend fun connect(
        context: Context,
        @MainDispatcher mainScope: CoroutineScope,
        wallpaperModel: LiveWallpaperModel,
        whichPreview: WhichPreview,
        destinationFlag: Int,
        surfaceView: SurfaceView,
        listener: WallpaperEngineConnection.WallpaperEngineConnectionListener? = null,
    ) {
        val wallpaperInfo = wallpaperModel.liveWallpaperData.systemWallpaperInfo
        val engineKey = wallpaperInfo.getKey()
        val displayMetrics = getDisplayMetrics(surfaceView)

        // Update the creative wallpaper uri before starting the service.
        wallpaperModel.creativeWallpaperData?.configPreviewUri?.let {
            context.contentResolver.update(it, ContentValues(), null)
        }

        if (!engineMap.containsKey(engineKey)) {
            mutex.withLock {
                if (!engineMap.containsKey(engineKey)) {
                    engineMap[engineKey] =
                        mainScope.async {
                            initEngine(
                                context,
                                wallpaperModel.getWallpaperServiceIntent(),
                                displayMetrics,
                                destinationFlag,
                                whichPreview,
                                surfaceView,
                                listener,
                            )
                        }
                }
            }
        }

        engineMap[engineKey]?.await()?.let { (_, engineConnection) ->
            engineConnection.engine?.let {
                mirrorAndReparent(engineKey, it, surfaceView, displayMetrics)
            }
        }
    }

    private fun LiveWallpaperModel.getWallpaperServiceIntent(): Intent {
        return liveWallpaperData.systemWallpaperInfo.let {
            Intent(WallpaperService.SERVICE_INTERFACE).setClassName(it.packageName, it.serviceName)
        }
    }

    suspend fun disconnect(
        context: Context,
        wallpaperModel: LiveWallpaperModel,
    ) {
        val engineKey = wallpaperModel.liveWallpaperData.systemWallpaperInfo.getKey()
        if (engineMap.containsKey(engineKey)) {
            mutex.withLock {
                engineMap.remove(engineKey)?.await()?.let { (serviceConnection, engineConnection) ->
                    engineConnection.engine?.destroy()
                    engineConnection.removeListener()
                    context.unbindService(serviceConnection)
                }
            }
        }

        if (surfaceControlMap.containsKey(engineKey)) {
            mutex.withLock {
                surfaceControlMap.remove(engineKey)?.let { surfaceControls ->
                    surfaceControls.forEach { it.release() }
                    surfaceControls.clear()
                }
            }
        }
    }

    /**
     * Disconnect all live wallpaper services without releasing and clear surface controls. This
     * function is called before binding static wallpapers. We have cases that user switch between
     * live wan static wallpapers. When switching from live to static wallpapers, we need to
     * disconnect the live wallpaper services to have the static wallpapers show up. But we can not
     * clear the surface controls yet, because we will need them to render the live wallpapers again
     * when switching from static to live wallpapers again.
     */
    suspend fun disconnectAllServices(context: Context) {
        engineMap.keys.map { key ->
            mutex.withLock {
                engineMap.remove(key)?.await()?.let { (serviceConnection, engineConnection) ->
                    engineConnection.engine?.destroy()
                    engineConnection.removeListener()
                    context.unbindService(serviceConnection)
                }
            }
        }
    }

    private suspend fun initEngine(
        context: Context,
        wallpaperIntent: Intent,
        displayMetrics: Point,
        destinationFlag: Int,
        whichPreview: WhichPreview,
        surfaceView: SurfaceView,
        listener: WallpaperEngineConnection.WallpaperEngineConnectionListener?,
    ): Pair<ServiceConnection, WallpaperEngineConnection> {
        // Bind service and get service connection and wallpaper service
        val (serviceConnection, wallpaperService) = bindWallpaperService(context, wallpaperIntent)
        val engineConnection = WallpaperEngineConnection(displayMetrics, whichPreview)
        listener?.let { engineConnection.setListener(it) }
        // Attach wallpaper connection to service and get wallpaper engine
        engineConnection.getEngine(wallpaperService, destinationFlag, surfaceView)
        return Pair(serviceConnection, engineConnection)
    }

    private fun WallpaperInfo.getKey(): String {
        return this.packageName.plus(":").plus(this.serviceName)
    }

    private suspend fun bindWallpaperService(
        context: Context,
        intent: Intent
    ): Pair<ServiceConnection, IWallpaperService> =
        suspendCancellableCoroutine {
            k: CancellableContinuation<Pair<ServiceConnection, IWallpaperService>> ->
            val serviceConnection =
                WallpaperServiceConnection(
                    object : WallpaperServiceConnection.WallpaperServiceConnectionListener {
                        override fun onWallpaperServiceConnected(
                            serviceConnection: ServiceConnection,
                            wallpaperService: IWallpaperService
                        ) {
                            k.resumeWith(Result.success(Pair(serviceConnection, wallpaperService)))
                        }
                    }
                )
            val success =
                context.bindService(
                    intent,
                    serviceConnection,
                    Context.BIND_AUTO_CREATE or
                        Context.BIND_IMPORTANT or
                        Context.BIND_ALLOW_ACTIVITY_STARTS
                )
            if (!success) {
                k.resumeWith(Result.failure(Exception("Fail to bind the live wallpaper service.")))
            }
        }

    private suspend fun mirrorAndReparent(
        engineKey: String,
        engine: IWallpaperEngine,
        parentSurface: SurfaceView,
        displayMetrics: Point
    ) {
        fun logError(e: Exception) {
            Log.e(WallpaperConnection::class.simpleName, "Fail to reparent wallpaper surface", e)
        }

        try {
            val parentSurfaceControl = parentSurface.surfaceControl
            val wallpaperSurfaceControl = engine.mirrorSurfaceControl() ?: return
            // Add surface control reference for later release when disconnected
            addSurfaceControlReference(engineKey, wallpaperSurfaceControl)

            val values = getScale(parentSurface, displayMetrics)
            SurfaceControl.Transaction().use { t ->
                t.setMatrix(
                    wallpaperSurfaceControl,
                    values[Matrix.MSCALE_X],
                    values[Matrix.MSKEW_Y],
                    values[Matrix.MSKEW_X],
                    values[Matrix.MSCALE_Y]
                )
                t.reparent(wallpaperSurfaceControl, parentSurfaceControl)
                t.show(wallpaperSurfaceControl)
                t.apply()
            }
        } catch (e: RemoteException) {
            logError(e)
        } catch (e: NullPointerException) {
            logError(e)
        }
    }

    private suspend fun addSurfaceControlReference(
        engineKey: String,
        wallpaperSurfaceControl: SurfaceControl,
    ) {
        val surfaceControls = surfaceControlMap[engineKey]
        if (surfaceControls == null) {
            mutex.withLock {
                surfaceControlMap[engineKey] =
                    (surfaceControlMap[engineKey] ?: mutableListOf()).apply {
                        add(wallpaperSurfaceControl)
                    }
            }
        } else {
            surfaceControls.add(wallpaperSurfaceControl)
        }
    }

    private fun getScale(parentSurface: SurfaceView, displayMetrics: Point): FloatArray {
        val metrics = Matrix()
        val values = FloatArray(9)
        val surfacePosition = parentSurface.holder.surfaceFrame
        metrics.postScale(
            surfacePosition.width().toFloat() / displayMetrics.x,
            surfacePosition.height().toFloat() / displayMetrics.y
        )
        metrics.getValues(values)
        return values
    }

    private fun getDisplayMetrics(view: View): Point {
        val screenSizeCalculator = ScreenSizeCalculator.getInstance()
        val display: Display = view.display
        return screenSizeCalculator.getScreenSize(display)
    }
}
