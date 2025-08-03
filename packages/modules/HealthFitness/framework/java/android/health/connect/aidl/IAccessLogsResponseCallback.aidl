package android.health.connect.aidl;

import android.health.connect.accesslog.AccessLogsResponseParcel;
import android.health.connect.aidl.HealthConnectExceptionParcel;

/**
 * Callback for {@link HealthConnectManager#queryAccessLogs}
 * {@hide}
 */
interface IAccessLogsResponseCallback {
    // Called on a successful operation
    oneway void onResult(in AccessLogsResponseParcel parcel);
    // Called when an error is hit
    oneway void onError(in HealthConnectExceptionParcel exception);
}