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
interface IDnsResolver {
  boolean isAlive();
  void registerEventListener(android.net.metrics.INetdEventListener listener);
  void setResolverConfiguration(in android.net.ResolverParamsParcel resolverParams);
  void getResolverInfo(int netId, out @utf8InCpp String[] servers, out @utf8InCpp String[] domains, out @utf8InCpp String[] tlsServers, out int[] params, out int[] stats, out int[] wait_for_pending_req_timeout_count);
  void startPrefix64Discovery(int netId);
  void stopPrefix64Discovery(int netId);
  @utf8InCpp String getPrefix64(int netId);
  void createNetworkCache(int netId);
  void destroyNetworkCache(int netId);
  void setLogSeverity(int logSeverity);
  void flushNetworkCache(int netId);
  void setPrefix64(int netId, @utf8InCpp String prefix);
  void registerUnsolicitedEventListener(android.net.resolv.aidl.IDnsResolverUnsolicitedEventListener listener);
  void setResolverOptions(int netId, in android.net.ResolverOptionsParcel optionParams);
  const int RESOLVER_PARAMS_SAMPLE_VALIDITY = 0;
  const int RESOLVER_PARAMS_SUCCESS_THRESHOLD = 1;
  const int RESOLVER_PARAMS_MIN_SAMPLES = 2;
  const int RESOLVER_PARAMS_MAX_SAMPLES = 3;
  const int RESOLVER_PARAMS_BASE_TIMEOUT_MSEC = 4;
  const int RESOLVER_PARAMS_RETRY_COUNT = 5;
  const int RESOLVER_PARAMS_COUNT = 6;
  const int RESOLVER_STATS_SUCCESSES = 0;
  const int RESOLVER_STATS_ERRORS = 1;
  const int RESOLVER_STATS_TIMEOUTS = 2;
  const int RESOLVER_STATS_INTERNAL_ERRORS = 3;
  const int RESOLVER_STATS_RTT_AVG = 4;
  const int RESOLVER_STATS_LAST_SAMPLE_TIME = 5;
  const int RESOLVER_STATS_USABLE = 6;
  const int RESOLVER_STATS_COUNT = 7;
  const int DNS_RESOLVER_LOG_VERBOSE = 0;
  const int DNS_RESOLVER_LOG_DEBUG = 1;
  const int DNS_RESOLVER_LOG_INFO = 2;
  const int DNS_RESOLVER_LOG_WARNING = 3;
  const int DNS_RESOLVER_LOG_ERROR = 4;
  const int TC_MODE_DEFAULT = 0;
  const int TC_MODE_UDP_TCP = 1;
  const int TRANSPORT_UNKNOWN = (-1) /* -1 */;
  const int TRANSPORT_CELLULAR = 0;
  const int TRANSPORT_WIFI = 1;
  const int TRANSPORT_BLUETOOTH = 2;
  const int TRANSPORT_ETHERNET = 3;
  const int TRANSPORT_VPN = 4;
  const int TRANSPORT_WIFI_AWARE = 5;
  const int TRANSPORT_LOWPAN = 6;
  const int TRANSPORT_TEST = 7;
  const int TRANSPORT_USB = 8;
  const int TRANSPORT_THREAD = 9;
  const int TRANSPORT_SATELLITE = 10;
}
