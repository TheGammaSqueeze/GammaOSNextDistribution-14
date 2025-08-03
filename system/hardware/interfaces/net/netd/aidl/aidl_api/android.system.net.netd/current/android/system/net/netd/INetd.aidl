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

package android.system.net.netd;
@VintfStability
interface INetd {
  void addInterfaceToOemNetwork(in long networkHandle, in String ifname);
  void addRouteToOemNetwork(in long networkHandle, in String ifname, in String destination, in String nexthop);
  android.system.net.netd.INetd.OemNetwork createOemNetwork();
  void destroyOemNetwork(in long networkHandle);
  void removeInterfaceFromOemNetwork(in long networkHandle, in String ifname);
  void removeRouteFromOemNetwork(in long networkHandle, in String ifname, in String destination, in String nexthop);
  void setForwardingBetweenInterfaces(in String inputIfName, in String outputIfName, in boolean enable);
  void setIpForwardEnable(in boolean enable);
  const int STATUS_INVALID_ARGUMENTS = 1;
  const int STATUS_NO_NETWORK = 2;
  const int STATUS_ALREADY_EXISTS = 3;
  const int STATUS_PERMISSION_DENIED = 4;
  const int STATUS_UNKNOWN_ERROR = 5;
  parcelable OemNetwork {
    long networkHandle;
    int packetMark;
  }
}
