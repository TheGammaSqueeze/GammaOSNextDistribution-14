package android.federatedcompute.aidl;


/**
  * Callback from a schedule/cancel federated computation request.
  * @hide
  */
oneway interface IFederatedComputeCallback {
  /** Sends back a void indicating success. */
  void onSuccess();
  /** Sends back a status code indicating failure. */
  void onFailure(int errorCode);
}