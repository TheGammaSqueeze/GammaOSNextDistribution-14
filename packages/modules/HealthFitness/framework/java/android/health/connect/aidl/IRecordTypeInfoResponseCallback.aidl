package android.health.connect.aidl;

import android.health.connect.aidl.RecordTypeInfoResponseParcel;
import android.health.connect.aidl.HealthConnectExceptionParcel;

/**
 * Callback for {@link IHealthConnectService#queryAllRecordTypesInfo}.
 *
 * {@hide}
 */
interface IRecordTypeInfoResponseCallback {
    // Called on a successful operation
    oneway void onResult(in RecordTypeInfoResponseParcel parcel);
    // Called when an error is hit
    oneway void onError(in HealthConnectExceptionParcel exception);
}
