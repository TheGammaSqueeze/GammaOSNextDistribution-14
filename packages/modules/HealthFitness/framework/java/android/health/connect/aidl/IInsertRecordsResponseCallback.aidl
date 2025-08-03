package android.health.connect.aidl;

import android.health.connect.aidl.InsertRecordsResponseParcel;
import android.health.connect.aidl.HealthConnectExceptionParcel;

/**
 * Callback for {@link IHealthConnectService#insertRecords}.
 *
 * {@hide}
 */
interface IInsertRecordsResponseCallback {
    // Called on a successful operation
    oneway void onResult(in InsertRecordsResponseParcel parcel);
    // Called when an error is hit
    oneway void onError(in HealthConnectExceptionParcel exception);
}
