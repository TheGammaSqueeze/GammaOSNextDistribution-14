package com.android.car.cartelemetryapp;

import android.os.PersistableBundle;
import com.android.car.cartelemetryapp.IConfigData;
import com.android.car.cartelemetryapp.IConfigStateListener;
import com.android.car.cartelemetryapp.IResultListener;

import java.util.List;

interface ICarMetricsCollectorService {
    List<IConfigData> getConfigData();
    void addConfig(in String configName);
    void removeConfig(in String configName);
    void setConfigStateListener(in IConfigStateListener listener);
    void setResultListener(in IResultListener listener);
    List<PersistableBundle> getBundleHistory(in String configName);
    List<String> getErrorHistory(in String configName);
    void clearHistory(in String configName);
    String getLog();
}
