package android.health.connect.aidl;

import android.health.connect.HealthConnectDataState;
import android.health.connect.aidl.HealthConnectExceptionParcel;

/**
 * Callback for {@link IHealthConnectService#getHealthConnectDataState}.
 * @hide
 */
interface IGetHealthConnectDataStateCallback {
    oneway void onResult(in HealthConnectDataState healthConnectDataState);
    oneway void onError(in HealthConnectExceptionParcel exception);
}