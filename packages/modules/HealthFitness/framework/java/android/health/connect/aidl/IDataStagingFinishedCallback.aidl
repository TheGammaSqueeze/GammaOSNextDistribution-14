package android.health.connect.aidl;

import android.health.connect.restore.StageRemoteDataException;

/**
 * Callback for {@link IHealthConnectService#stageAllHealthConnectRemoteData}.
 * @hide
 */
interface IDataStagingFinishedCallback {
    oneway void onResult();
    oneway void onError(in StageRemoteDataException exception);
}