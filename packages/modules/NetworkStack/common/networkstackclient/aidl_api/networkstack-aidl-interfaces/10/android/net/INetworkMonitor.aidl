///////////////////////////////////////////////////////////////////////////////
// THIS FILE IS IMMUTABLE. DO NOT EDIT IN ANY CASE.                          //
///////////////////////////////////////////////////////////////////////////////

// This file is a snapshot of an AIDL file. Do not edit it manually. There are
// two cases:
// 1). this is a frozen version file - do not edit this in any case.
// 2). this is a 'current' file. If you make a backwards compatible change to
//     the interface (from the latest frozen version), the build system will
//     prompt you to update this file with `m <name>-update-api`.
//
// You must not make a backward incompatible change to any AIDL file built
// with the aidl_interface module type with versions property set. The module
// type is used to build AIDL files in a way that they can be used across
// independently updatable components of the system. If a device is shipped
// with such a backward incompatible change, it has a high risk of breaking
// later when a module using the interface is updated, e.g., Mainline modules.

package android.net;
/* @hide */
interface INetworkMonitor {
  oneway void start();
  oneway void launchCaptivePortalApp();
  oneway void notifyCaptivePortalAppFinished(int response);
  oneway void setAcceptPartialConnectivity();
  oneway void forceReevaluation(int uid);
  oneway void notifyPrivateDnsChanged(in android.net.PrivateDnsConfigParcel config);
  oneway void notifyDnsResponse(int returnCode);
  oneway void notifyNetworkConnected(in android.net.LinkProperties lp, in android.net.NetworkCapabilities nc);
  oneway void notifyNetworkDisconnected();
  oneway void notifyLinkPropertiesChanged(in android.net.LinkProperties lp);
  oneway void notifyNetworkCapabilitiesChanged(in android.net.NetworkCapabilities nc);
  const int NETWORK_TEST_RESULT_VALID = 0;
  const int NETWORK_TEST_RESULT_INVALID = 1;
  const int NETWORK_TEST_RESULT_PARTIAL_CONNECTIVITY = 2;
  const int NETWORK_VALIDATION_RESULT_VALID = 1;
  const int NETWORK_VALIDATION_RESULT_PARTIAL = 2;
  const int NETWORK_VALIDATION_PROBE_DNS = 4;
  const int NETWORK_VALIDATION_PROBE_HTTP = 8;
  const int NETWORK_VALIDATION_PROBE_HTTPS = 16;
  const int NETWORK_VALIDATION_PROBE_FALLBACK = 32;
  const int NETWORK_VALIDATION_PROBE_PRIVDNS = 64;
}
