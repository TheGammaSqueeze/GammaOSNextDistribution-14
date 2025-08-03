#!/usr/bin/python3
#
# Copyright 2014 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import contextlib
import fcntl
import os
import random
import re
from socket import *  # pylint: disable=wildcard-import
import struct
import sys
import unittest

from scapy import all as scapy

import binascii
import csocket

# TODO: Move these to csocket.py.
SOL_IPV6 = 41
IP_RECVERR = 11
IPV6_RECVERR = 25
IP_TRANSPARENT = 19
IPV6_TRANSPARENT = 75
IPV6_TCLASS = 67
IPV6_FLOWLABEL_MGR = 32
IPV6_FLOWINFO_SEND = 33

SO_BINDTODEVICE = 25
SO_MARK = 36
SO_PROTOCOL = 38
SO_DOMAIN = 39
SO_COOKIE = 57

ETH_P_IP = 0x0800
ETH_P_IPV6 = 0x86dd

IPPROTO_GRE = 47

SIOCSIFHWADDR = 0x8924

IPV6_FL_A_GET = 0
IPV6_FL_A_PUT = 1
IPV6_FL_A_RENEW = 1

IPV6_FL_F_CREATE = 1
IPV6_FL_F_EXCL = 2

IPV6_FL_S_NONE = 0
IPV6_FL_S_EXCL = 1
IPV6_FL_S_ANY = 255

IFNAMSIZ = 16

IPV4_PING = b"\x08\x00\x00\x00\x0a\xce\x00\x03"
IPV6_PING = b"\x80\x00\x00\x00\x0a\xce\x00\x03"

IPV4_ADDR = "8.8.8.8"
IPV4_ADDR2 = "8.8.4.4"
IPV6_ADDR = "2001:4860:4860::8888"
IPV6_ADDR2 = "2001:4860:4860::8844"

IPV6_SEQ_DGRAM_HEADER = ("  sl  "
                         "local_address                         "
                         "remote_address                        "
                         "st tx_queue rx_queue tr tm->when retrnsmt"
                         "   uid  timeout inode ref pointer drops\n")

UDP_HDR_LEN = 8

# Arbitrary packet payload.
UDP_PAYLOAD = bytes(scapy.DNS(rd=1,
                              id=random.randint(0, 65535),
                              qd=scapy.DNSQR(qname="wWW.GoOGle.CoM",
                                             qtype="AAAA")))

# Unix group to use if we want to open sockets as non-root.
AID_INET = 3003

# Kernel log verbosity levels.
KERN_INFO = 6

LINUX_VERSION = csocket.LinuxVersion()
LINUX_ANY_VERSION = (0, 0)

def KernelAtLeast(versions):
  """Checks the kernel version matches the specified versions.

  Args:
    versions: a list of versions expressed as tuples,
    e.g., [(5, 10, 108), (5, 15, 31)]. The kernel version matches if it's
    between each specified version and the next minor version with last digit
    set to 0. In this example, the kernel version must match either:
      >= 5.10.108 and < 5.15.0
      >= 5.15.31
    While this is less flexible than matching exact tuples, it allows the caller
    to pass in fewer arguments, because Android only supports certain minor
    versions (4.19, 5.4, 5.10, ...)

  Returns:
    True if the kernel version matches, False otherwise
  """
  maxversion = (1000, 255, 65535)
  for version in sorted(versions, reverse=True):
    if version[:2] == maxversion[:2]:
      raise ValueError("Duplicate minor version: %s %s", (version, maxversion))
    if LINUX_VERSION >= version and LINUX_VERSION < maxversion:
      return True
    maxversion = (version[0], version[1], 0)
  return False

def ByteToHex(b):
  return "%02x" % (ord(b) if isinstance(b, str) else b)

def GetWildcardAddress(version):
  return {4: "0.0.0.0", 6: "::"}[version]

def GetIpHdrLength(version):
  return {4: 20, 6: 40}[version]

def GetAddressFamily(version):
  return {4: AF_INET, 5: AF_INET6, 6: AF_INET6}[version]


def AddressLengthBits(version):
  return {4: 32, 6: 128}[version]

def GetAddressVersion(address):
  if ":" not in address:
    return 4
  if address.startswith("::ffff"):
    return 5
  return 6

def SetSocketTos(s, tos):
  level = {AF_INET: SOL_IP, AF_INET6: SOL_IPV6}[s.family]
  option = {AF_INET: IP_TOS, AF_INET6: IPV6_TCLASS}[s.family]
  s.setsockopt(level, option, tos)


def SetNonBlocking(fd):
  flags = fcntl.fcntl(fd, fcntl.F_GETFL, 0)
  fcntl.fcntl(fd, fcntl.F_SETFL, flags | os.O_NONBLOCK)


# Convenience functions to create sockets.
def Socket(family, sock_type, protocol):
  s = socket(family, sock_type, protocol)
  csocket.SetSocketTimeout(s, 5000)
  return s


def PingSocket(family):
  proto = {AF_INET: IPPROTO_ICMP, AF_INET6: IPPROTO_ICMPV6}[family]
  return Socket(family, SOCK_DGRAM, proto)


def IPv4PingSocket():
  return PingSocket(AF_INET)


def IPv6PingSocket():
  return PingSocket(AF_INET6)


def TCPSocket(family):
  s = Socket(family, SOCK_STREAM, IPPROTO_TCP)
  SetNonBlocking(s.fileno())
  return s


def IPv4TCPSocket():
  return TCPSocket(AF_INET)


def IPv6TCPSocket():
  return TCPSocket(AF_INET6)


def UDPSocket(family):
  return Socket(family, SOCK_DGRAM, IPPROTO_UDP)


def RawGRESocket(family):
  s = Socket(family, SOCK_RAW, IPPROTO_GRE)
  return s


def BindRandomPort(version, sock):
  addr = {4: "0.0.0.0", 5: "::", 6: "::"}[version]
  sock.setsockopt(SOL_SOCKET, SO_REUSEADDR, 1)
  sock.bind((addr, 0))
  if sock.getsockopt(SOL_SOCKET, SO_PROTOCOL) == IPPROTO_TCP:
    sock.listen(100)
  port = sock.getsockname()[1]
  return port


def EnableFinWait(sock):
  # Disabling SO_LINGER causes sockets to go into FIN_WAIT on close().
  sock.setsockopt(SOL_SOCKET, SO_LINGER, struct.pack("ii", 0, 0))


def DisableFinWait(sock):
  # Enabling SO_LINGER with a timeout of zero causes close() to send RST.
  sock.setsockopt(SOL_SOCKET, SO_LINGER, struct.pack("ii", 1, 0))


def CreateSocketPair(family, socktype, addr):
  clientsock = socket(family, socktype, 0)
  listensock = socket(family, socktype, 0)
  listensock.bind((addr, 0))
  addr = listensock.getsockname()
  if socktype == SOCK_STREAM:
    listensock.listen(1)
  clientsock.connect(listensock.getsockname())
  if socktype == SOCK_STREAM:
    acceptedsock, _ = listensock.accept()
    DisableFinWait(clientsock)
    DisableFinWait(acceptedsock)
    listensock.close()
  else:
    listensock.connect(clientsock.getsockname())
    acceptedsock = listensock
  return clientsock, acceptedsock


def GetInterfaceIndex(ifname):
  with UDPSocket(AF_INET) as s:
    ifr = struct.pack("%dsi" % IFNAMSIZ, ifname.encode(), 0)
    ifr = fcntl.ioctl(s, scapy.SIOCGIFINDEX, ifr)
    return struct.unpack("%dsi" % IFNAMSIZ, ifr)[1]


def SetInterfaceHWAddr(ifname, hwaddr):
  with UDPSocket(AF_INET) as s:
    hwaddr = hwaddr.replace(":", "")
    hwaddr = binascii.unhexlify(hwaddr)
    if len(hwaddr) != 6:
      raise ValueError("Unknown hardware address length %d" % len(hwaddr))
    ifr = struct.pack("%dsH6s" % IFNAMSIZ, ifname.encode(), scapy.ARPHDR_ETHER,
                      hwaddr)
    fcntl.ioctl(s, SIOCSIFHWADDR, ifr)


def SetInterfaceState(ifname, up):
  ifname_bytes = ifname.encode()
  with UDPSocket(AF_INET) as s:
    ifr = struct.pack("%dsH" % IFNAMSIZ, ifname_bytes, 0)
    ifr = fcntl.ioctl(s, scapy.SIOCGIFFLAGS, ifr)
    _, flags = struct.unpack("%dsH" % IFNAMSIZ, ifr)
    if up:
      flags |= scapy.IFF_UP
    else:
      flags &= ~scapy.IFF_UP
    ifr = struct.pack("%dsH" % IFNAMSIZ, ifname_bytes, flags)
    ifr = fcntl.ioctl(s, scapy.SIOCSIFFLAGS, ifr)


def SetInterfaceUp(ifname):
  return SetInterfaceState(ifname, True)


def SetInterfaceDown(ifname):
  return SetInterfaceState(ifname, False)


def CanonicalizeIPv6Address(addr):
  return inet_ntop(AF_INET6, inet_pton(AF_INET6, addr))


def FormatProcAddress(unformatted):
  groups = []
  for i in range(0, len(unformatted), 4):
    groups.append(unformatted[i:i+4])
  formatted = ":".join(groups)
  # Compress the address.
  address = CanonicalizeIPv6Address(formatted)
  return address


def FormatSockStatAddress(address):
  if ":" in address:
    family = AF_INET6
  else:
    family = AF_INET
  binary = inet_pton(family, address)
  out = ""
  for i in range(0, len(binary), 4):
    out += "%08X" % struct.unpack("=L", binary[i:i+4])
  return out


def GetLinkAddress(ifname, linklocal):
  with open("/proc/net/if_inet6") as if_inet6:
    addresses = if_inet6.readlines()
  for address in addresses:
    address = [s for s in address.strip().split(" ") if s]
    if address[5] == ifname:
      if (linklocal and address[0].startswith("fe80")
          or not linklocal and not address[0].startswith("fe80")):
        # Convert the address from raw hex to something with colons in it.
        return FormatProcAddress(address[0])
  return None


def GetDefaultRoute(version=6):
  if version == 6:
    with open("/proc/net/ipv6_route") as ipv6_route:
      routes = ipv6_route.readlines()
    for route in routes:
      route = [s for s in route.strip().split(" ") if s]
      if (route[0] == "00000000000000000000000000000000" and route[1] == "00"
          # Routes in non-default tables end up in /proc/net/ipv6_route!!!
          and route[9] != "lo" and not route[9].startswith("nettest")):
        return FormatProcAddress(route[4]), route[9]
    raise ValueError("No IPv6 default route found")
  elif version == 4:
    with open("/proc/net/route") as ipv4_route:
      routes = ipv4_route.readlines()
    for route in routes:
      route = [s for s in route.strip().split("\t") if s]
      if route[1] == "00000000" and route[7] == "00000000":
        gw, iface = route[2], route[0]
        gw = inet_ntop(AF_INET, binascii.unhexlify(gw)[::-1])
        return gw, iface
    raise ValueError("No IPv4 default route found")
  else:
    raise ValueError("Don't know about IPv%s" % version)


def GetDefaultRouteInterface():
  unused_gw, iface = GetDefaultRoute()
  return iface


def MakeFlowLabelOption(addr, label):
  # struct in6_flowlabel_req {
  #         struct in6_addr flr_dst;
  #         __be32  flr_label;
  #         __u8    flr_action;
  #         __u8    flr_share;
  #         __u16   flr_flags;
  #         __u16   flr_expires;
  #         __u16   flr_linger;
  #         __u32   __flr_pad;
  #         /* Options in format of IPV6_PKTOPTIONS */
  # };
  fmt = "16sIBBHHH4s"
  assert struct.calcsize(fmt) == 32
  addr = inet_pton(AF_INET6, addr)
  assert len(addr) == 16
  label = htonl(label & 0xfffff)
  action = IPV6_FL_A_GET
  share = IPV6_FL_S_ANY
  flags = IPV6_FL_F_CREATE
  pad = b"\x00" * 4
  return struct.pack(fmt, addr, label, action, share, flags, 0, 0, pad)


def SetFlowLabel(s, addr, label):
  opt = MakeFlowLabelOption(addr, label)
  s.setsockopt(SOL_IPV6, IPV6_FLOWLABEL_MGR, opt)
  # Caller also needs to do s.setsockopt(SOL_IPV6, IPV6_FLOWINFO_SEND, 1).


def GetIptablesBinaryPath(version):
  if version == 4:
    paths = (
        "/sbin/iptables-legacy",
        "/sbin/iptables",
        "/system/bin/iptables-legacy",
        "/system/bin/iptables",
    )
  elif version == 6:
    paths = (
        "/sbin/ip6tables-legacy",
        "/sbin/ip6tables",
        "/system/bin/ip6tables-legacy",
        "/system/bin/ip6tables",
    )
  for iptables_path in paths:
    if os.access(iptables_path, os.X_OK):
      return iptables_path
  raise FileNotFoundError(
      "iptables binary for IPv{} not found".format(version) +
      ", checked: {}".format(", ".join(paths)))


def RunIptablesCommand(version, args):
  iptables_path = GetIptablesBinaryPath(version)
  return os.spawnvp(
      os.P_WAIT, iptables_path,
      [iptables_path, "-w"] + args.split(" "))

# Determine network configuration.
try:
  GetDefaultRoute(version=4)
  HAVE_IPV4 = True
except ValueError:
  HAVE_IPV4 = False

try:
  GetDefaultRoute(version=6)
  HAVE_IPV6 = True
except ValueError:
  HAVE_IPV6 = False

class RunAsUidGid(object):
  """Context guard to run a code block as a given UID."""

  def __init__(self, uid, gid):
    self.uid = uid
    self.gid = gid

  def __enter__(self):
    if self.gid:
      self.saved_gid = os.getgid()
      os.setgid(self.gid)
    if self.uid:
      self.saved_uids = os.getresuid()
      self.saved_groups = os.getgroups()
      os.setgroups(self.saved_groups + [AID_INET])
      os.setresuid(self.uid, self.uid, self.saved_uids[0])

  def __exit__(self, unused_type, unused_value, unused_traceback):
    if self.uid:
      os.setresuid(*self.saved_uids)
      os.setgroups(self.saved_groups)
    if self.gid:
      os.setgid(self.saved_gid)

class RunAsUid(RunAsUidGid):
  """Context guard to run a code block as a given GID and UID."""

  def __init__(self, uid):
    RunAsUidGid.__init__(self, uid, 0)

class NetworkTest(unittest.TestCase):

  @contextlib.contextmanager
  def _errnoCheck(self, err_num):
    with self.assertRaises(EnvironmentError) as context:
      yield context
    self.assertEqual(context.exception.errno, err_num)

  def assertRaisesErrno(self, err_num, f=None, *args):
    """Test that the system returns an errno error.

    This works similarly to unittest.TestCase.assertRaises. You can call it as
    an assertion, or use it as a context manager.
    e.g.
        self.assertRaisesErrno(errno.ENOENT, do_things, arg1, arg2)
    or
        with self.assertRaisesErrno(errno.ENOENT):
          do_things(arg1, arg2)

    Args:
      err_num: an errno constant
      f: (optional) A callable that should result in error
      *args: arguments passed to f
    """
    if f is None:
      return self._errnoCheck(err_num)
    else:
      with self._errnoCheck(err_num):
        f(*args)

  def ReadProcNetSocket(self, protocol):
    # Read file.
    filename = "/proc/net/%s" % protocol
    with open(filename) as f:
      lines = f.readlines()

    # Possibly check, and strip, header.
    if protocol in ["icmp6", "raw6", "udp6"]:
      self.assertEqual(IPV6_SEQ_DGRAM_HEADER, lines[0])
    lines = lines[1:]

    # Check contents.
    if protocol.endswith("6"):
      addrlen = 32
    else:
      addrlen = 8

    if protocol.startswith("tcp"):
      # Real sockets have 5 extra numbers, timewait sockets have none.
      end_regexp = "(| +[0-9]+ [0-9]+ [0-9]+ [0-9]+ -?[0-9]+)$"
    elif re.match("icmp|udp|raw", protocol):
      # Drops.
      end_regexp = " +([0-9]+) *$"
    else:
      raise ValueError("Don't know how to parse %s" % filename)

    regexp = re.compile(r" *(\d+): "                    # bucket
                        "([0-9A-F]{%d}:[0-9A-F]{4}) "   # srcaddr, port
                        "([0-9A-F]{%d}:[0-9A-F]{4}) "   # dstaddr, port
                        "([0-9A-F][0-9A-F]) "           # state
                        "([0-9A-F]{8}:[0-9A-F]{8}) "    # mem
                        "([0-9A-F]{2}:[0-9A-F]{8}) "    # ?
                        "([0-9A-F]{8}) +"               # ?
                        "([0-9]+) +"                    # uid
                        "([0-9]+) +"                    # timeout
                        "([0-9]+) +"                    # inode
                        "([0-9]+) +"                    # refcnt
                        "([0-9a-f]+)"                   # sp
                        "%s"                            # icmp has spaces
                        % (addrlen, addrlen, end_regexp))
    # Return a list of lists with only source / dest addresses for now.
    # TODO: consider returning a dict or namedtuple instead.
    out = []
    for line in lines:
      m = regexp.match(line)
      if m is None:
        raise ValueError("Failed match on [%s]" % line)
      (_, src, dst, state, mem,
       _, _, uid, _, _, refcnt, _, extra) = m.groups()
      out.append([src, dst, state, mem, uid, refcnt, extra])
    return out

  @staticmethod
  def GetConsoleLogLevel():
    with open("/proc/sys/kernel/printk") as printk:
      return int(printk.readline().split()[0])

  @staticmethod
  def SetConsoleLogLevel(level):
    with open("/proc/sys/kernel/printk", "w") as printk:
      return printk.write("%s\n" % level)


if __name__ == "__main__":
  unittest.main()
