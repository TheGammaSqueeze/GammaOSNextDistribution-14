package com.android.wallpaper.util.wallpaperconnection

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.service.wallpaper.IWallpaperService

class WallpaperServiceConnection(val listener: WallpaperServiceConnectionListener) :
    ServiceConnection {

    override fun onServiceConnected(componentName: ComponentName?, service: IBinder?) {
        listener.onWallpaperServiceConnected(this, IWallpaperService.Stub.asInterface(service))
    }

    override fun onServiceDisconnected(componentName: ComponentName?) {}

    interface WallpaperServiceConnectionListener {
        fun onWallpaperServiceConnected(
            serviceConnection: ServiceConnection,
            wallpaperService: IWallpaperService,
        )
    }
}
