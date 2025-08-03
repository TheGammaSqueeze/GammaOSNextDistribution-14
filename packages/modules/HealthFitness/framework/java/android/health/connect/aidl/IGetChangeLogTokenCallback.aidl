package android.health.connect.aidl;

import android.health.connect.aidl.HealthConnectExceptionParcel;
import android.health.connect.changelog.ChangeLogTokenResponse;

/**
 * Callback for {@link IHealthConnectService#getChangeLogToken}
 *
 * {@hide}
 */
interface IGetChangeLogTokenCallback {
    // Called on a successful operation
    oneway void onResult(in ChangeLogTokenResponse parcel);
    // Called when an error is hit
    oneway void onError(in HealthConnectExceptionParcel exception);
}
