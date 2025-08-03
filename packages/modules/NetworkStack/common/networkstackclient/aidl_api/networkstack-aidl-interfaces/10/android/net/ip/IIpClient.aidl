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

package android.net.ip;
/* @hide */
interface IIpClient {
  oneway void completedPreDhcpAction();
  oneway void confirmConfiguration();
  oneway void readPacketFilterComplete(in byte[] data);
  oneway void shutdown();
  oneway void startProvisioning(in android.net.ProvisioningConfigurationParcelable req);
  oneway void stop();
  oneway void setTcpBufferSizes(in String tcpBufferSizes);
  oneway void setHttpProxy(in android.net.ProxyInfo proxyInfo);
  oneway void setMulticastFilter(boolean enabled);
  oneway void addKeepalivePacketFilter(int slot, in android.net.TcpKeepalivePacketDataParcelable pkt);
  oneway void removeKeepalivePacketFilter(int slot);
  oneway void setL2KeyAndGroupHint(in String l2Key, in String cluster);
  oneway void addNattKeepalivePacketFilter(int slot, in android.net.NattKeepalivePacketDataParcelable pkt);
  oneway void notifyPreconnectionComplete(boolean success);
  oneway void updateLayer2Information(in android.net.Layer2InformationParcelable info);
}
