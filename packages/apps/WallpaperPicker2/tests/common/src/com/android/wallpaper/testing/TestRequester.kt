package com.android.wallpaper.testing

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import com.android.volley.Request
import com.android.wallpaper.network.Requester
import com.bumptech.glide.request.target.Target
import java.io.File

/** Test implementation of [Requester] */
class TestRequester : Requester {
    override fun <T : Any?> addToRequestQueue(request: Request<T>?) {
        // Do nothing intended
    }

    override fun loadImageFile(imageUrl: Uri?): File {
        return File("test_file.txt")
    }

    override fun loadImageFileWithActivity(
        activity: Activity?,
        imageUrl: Uri?,
        target: Target<File>?
    ) {
        // Do nothing intended
    }

    override fun loadImageBitmap(imageUrl: Uri?, target: Target<Bitmap>?) {
        // Do nothing intended
    }
}
