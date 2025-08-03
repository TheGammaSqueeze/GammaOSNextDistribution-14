package com.android.healthconnect.testapps.toolbox.utils

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.android.healthconnect.testapps.toolbox.R

class LoadingDialog(context: Context) {

    private val dialog: Dialog

    init {
        val inflater = LayoutInflater.from(context)
        dialog =
            AlertDialog.Builder(context)
                .setTitle("Loading")
                .setView(inflater.inflate(R.layout.loading, null))
                .create()
        dialog.setCancelable(false)
    }

    fun showDialog() {
        dialog.show()
    }

    fun dismissDialog() {
        dialog.dismiss()
    }
}
