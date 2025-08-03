package com.android.car.cartelemetryapp;

import android.os.PersistableBundle;
import com.android.car.cartelemetryapp.IConfigData;

interface IResultListener {
    void onResult(
            in String metricsConfigName,
            in IConfigData configData,
            in PersistableBundle report,
            in String telemetryError);
}