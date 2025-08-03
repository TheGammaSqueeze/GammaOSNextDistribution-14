#!/usr/bin/python3
#
# Copyright 2017 The Android Open Source Project
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

import unittest

from errno import *
from socket import *
from scapy import all as scapy

import multinetwork_base
import net_test
import os
import packets
import tcp_metrics


TCPOPT_FASTOPEN = 34
TCP_FASTOPEN_CONNECT = 30
BH_TIMEOUT_SYSCTL = "/proc/sys/net/ipv4/tcp_fastopen_blackhole_timeout_sec"


class TcpFastOpenTest(multinetwork_base.MultiNetworkBaseTest):

  @classmethod
  def setUpClass(cls):
    super(TcpFastOpenTest, cls).setUpClass()
    cls.tcp_metrics = tcp_metrics.TcpMetrics()

  def TFOClientSocket(self, version, netid):
    s = net_test.TCPSocket(net_test.GetAddressFamily(version))
    net_test.DisableFinWait(s)
    self.SelectInterface(s, netid, "mark")
    s.setsockopt(IPPROTO_TCP, TCP_FASTOPEN_CONNECT, 1)
    return s

  def assertSocketNotConnected(self, sock):
    self.assertRaisesErrno(ENOTCONN, sock.getpeername)

  def assertSocketConnected(self, sock):
    sock.getpeername()  # No errors? Socket is alive and connected.

  def clearTcpMetrics(self, version, netid):
    saddr = self.MyAddress(version, netid)
    daddr = self.GetRemoteAddress(version)
    self.tcp_metrics.DelMetrics(saddr, daddr)
    with self.assertRaisesErrno(ESRCH):
      print(self.tcp_metrics.GetMetrics(saddr, daddr))

  def assertNoTcpMetrics(self, version, netid):
    saddr = self.MyAddress(version, netid)
    daddr = self.GetRemoteAddress(version)
    with self.assertRaisesErrno(ENOENT):
      self.tcp_metrics.GetMetrics(saddr, daddr)

  def clearBlackhole(self):
    timeout = self.GetSysctl(BH_TIMEOUT_SYSCTL)

    # Write to timeout to clear any pre-existing blackhole condition
    self.SetSysctl(BH_TIMEOUT_SYSCTL, timeout)

  def CheckConnectOption(self, version):
    ip_layer = {4: scapy.IP, 6: scapy.IPv6}[version]
    netid = self.RandomNetid()
    s = self.TFOClientSocket(version, netid)

    self.clearTcpMetrics(version, netid)
    self.clearBlackhole()

    # Connect the first time.
    remoteaddr = self.GetRemoteAddress(version)
    with self.assertRaisesErrno(EINPROGRESS):
      s.connect((remoteaddr, 53))
    self.assertSocketNotConnected(s)

    # Expect a SYN handshake with an empty TFO option.
    myaddr = self.MyAddress(version, netid)
    port = s.getsockname()[1]
    self.assertNotEqual(0, port)
    desc, syn = packets.SYN(53, version, myaddr, remoteaddr, port, seq=None)
    syn.getlayer("TCP").options = [(TCPOPT_FASTOPEN, "")]
    msg = "Fastopen connect: expected %s" % desc
    syn = self.ExpectPacketOn(netid, msg, syn)
    syn = ip_layer(bytes(syn))

    # Receive a SYN+ACK with a TFO cookie and expect the connection to proceed
    # as normal.
    desc, synack = packets.SYNACK(version, remoteaddr, myaddr, syn)
    synack.getlayer("TCP").options = [
        (TCPOPT_FASTOPEN, "helloT"), ("NOP", None), ("NOP", None)]
    self.ReceivePacketOn(netid, synack)
    synack = ip_layer(bytes(synack))
    desc, ack = packets.ACK(version, myaddr, remoteaddr, synack)
    msg = "First connect: got SYN+ACK, expected %s" % desc
    self.ExpectPacketOn(netid, msg, ack)
    self.assertSocketConnected(s)
    s.close()
    desc, rst = packets.RST(version, myaddr, remoteaddr, synack)
    msg = "Closing client socket, expecting %s" % desc
    self.ExpectPacketOn(netid, msg, rst)

    # Connect to the same destination again. Expect the connect to succeed
    # without sending a SYN packet.
    s = self.TFOClientSocket(version, netid)
    s.connect((remoteaddr, 53))
    self.assertSocketNotConnected(s)
    self.ExpectNoPacketsOn(netid, "Second TFO connect, expected no packets")

    # Issue a write and expect a SYN with data.
    port = s.getsockname()[1]
    s.send(net_test.UDP_PAYLOAD)
    desc, syn = packets.SYN(53, version, myaddr, remoteaddr, port, seq=None)
    t = syn.getlayer(scapy.TCP)
    t.options = [ (TCPOPT_FASTOPEN, "helloT"), ("NOP", None), ("NOP", None)]
    t.payload = scapy.Raw(net_test.UDP_PAYLOAD)
    msg = "TFO write, expected %s" % desc
    self.ExpectPacketOn(netid, msg, syn)
    s.close()

  def testConnectOptionIPv4(self):
    self.CheckConnectOption(4)

  def testConnectOptionIPv6(self):
    self.CheckConnectOption(6)


if __name__ == "__main__":
  unittest.main()
