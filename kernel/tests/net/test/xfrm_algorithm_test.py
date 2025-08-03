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

# pylint: disable=g-bad-todo,g-bad-file-header,wildcard-import
from errno import *  # pylint: disable=wildcard-import
import os
import itertools
from scapy import all as scapy
from socket import *  # pylint: disable=wildcard-import
import threading
import unittest

import multinetwork_base
import net_test
from tun_twister import TapTwister
import util
import xfrm
import xfrm_base
import xfrm_test

ANY_KVER = net_test.LINUX_ANY_VERSION

# List of encryption algorithms for use in ParamTests.
CRYPT_ALGOS = [
    (xfrm.XfrmAlgo((xfrm.XFRM_EALG_CBC_AES, 128)), ANY_KVER),
    (xfrm.XfrmAlgo((xfrm.XFRM_EALG_CBC_AES, 192)), ANY_KVER),
    (xfrm.XfrmAlgo((xfrm.XFRM_EALG_CBC_AES, 256)), ANY_KVER),
    # RFC 3686 specifies that key length must be 128, 192 or 256 bits, with
    # an additional 4 bytes (32 bits) of nonce. A fresh nonce value MUST be
    # assigned for each SA.
    # CTR-AES is enforced since kernel version 5.8
    (xfrm.XfrmAlgo((xfrm.XFRM_EALG_CTR_AES, 128+32)), (5, 8)),
    (xfrm.XfrmAlgo((xfrm.XFRM_EALG_CTR_AES, 192+32)), (5, 8)),
    (xfrm.XfrmAlgo((xfrm.XFRM_EALG_CTR_AES, 256+32)), (5, 8)),
]

# List of auth algorithms for use in ParamTests.
AUTH_ALGOS = [
    # RFC 4868 specifies that the only supported truncation length is half the
    # hash size.
    (xfrm.XfrmAlgoAuth((xfrm.XFRM_AALG_HMAC_MD5, 128, 96)), ANY_KVER),
    (xfrm.XfrmAlgoAuth((xfrm.XFRM_AALG_HMAC_SHA1, 160, 96)), ANY_KVER),
    (xfrm.XfrmAlgoAuth((xfrm.XFRM_AALG_HMAC_SHA256, 256, 128)), ANY_KVER),
    (xfrm.XfrmAlgoAuth((xfrm.XFRM_AALG_HMAC_SHA384, 384, 192)), ANY_KVER),
    (xfrm.XfrmAlgoAuth((xfrm.XFRM_AALG_HMAC_SHA512, 512, 256)), ANY_KVER),
    # Test larger truncation lengths for good measure.
    (xfrm.XfrmAlgoAuth((xfrm.XFRM_AALG_HMAC_MD5, 128, 128)), ANY_KVER),
    (xfrm.XfrmAlgoAuth((xfrm.XFRM_AALG_HMAC_SHA1, 160, 160)), ANY_KVER),
    (xfrm.XfrmAlgoAuth((xfrm.XFRM_AALG_HMAC_SHA256, 256, 256)), ANY_KVER),
    (xfrm.XfrmAlgoAuth((xfrm.XFRM_AALG_HMAC_SHA384, 384, 384)), ANY_KVER),
    (xfrm.XfrmAlgoAuth((xfrm.XFRM_AALG_HMAC_SHA512, 512, 512)), ANY_KVER),
    # RFC 3566 specifies that the only supported truncation length
    # is 96 bits.
    # XCBC-AES is enforced since kernel version 5.8
    (xfrm.XfrmAlgoAuth((xfrm.XFRM_AALG_AUTH_XCBC_AES, 128, 96)), (5, 8)),
]

# List of aead algorithms for use in ParamTests.
AEAD_ALGOS = [
    # RFC 4106 specifies that key length must be 128, 192 or 256 bits,
    #   with an additional 4 bytes (32 bits) of salt. The salt must be unique
    #   for each new SA using the same key.
    # RFC 4106 specifies that ICV length must be 8, 12, or 16 bytes
    (xfrm.XfrmAlgoAead((xfrm.XFRM_AEAD_GCM_AES, 128+32, 8*8)), ANY_KVER),
    (xfrm.XfrmAlgoAead((xfrm.XFRM_AEAD_GCM_AES, 128+32, 12*8)), ANY_KVER),
    (xfrm.XfrmAlgoAead((xfrm.XFRM_AEAD_GCM_AES, 128+32, 16*8)), ANY_KVER),
    (xfrm.XfrmAlgoAead((xfrm.XFRM_AEAD_GCM_AES, 192+32, 8*8)), ANY_KVER),
    (xfrm.XfrmAlgoAead((xfrm.XFRM_AEAD_GCM_AES, 192+32, 12*8)), ANY_KVER),
    (xfrm.XfrmAlgoAead((xfrm.XFRM_AEAD_GCM_AES, 192+32, 16*8)), ANY_KVER),
    (xfrm.XfrmAlgoAead((xfrm.XFRM_AEAD_GCM_AES, 256+32, 8*8)), ANY_KVER),
    (xfrm.XfrmAlgoAead((xfrm.XFRM_AEAD_GCM_AES, 256+32, 12*8)), ANY_KVER),
    (xfrm.XfrmAlgoAead((xfrm.XFRM_AEAD_GCM_AES, 256+32, 16*8)), ANY_KVER),
    # RFC 7634 specifies that key length must be 256 bits, with an additional
    # 4 bytes (32 bits) of nonce. A fresh nonce value MUST be assigned for
    # each SA. RFC 7634 also specifies that ICV length must be 16 bytes.
    # ChaCha20-Poly1305 is enforced since kernel version 5.8
    (xfrm.XfrmAlgoAead((xfrm.XFRM_AEAD_CHACHA20_POLY1305, 256+32, 16*8)), (5, 8)),
]

def GenerateKey(key_len):
  if key_len % 8 != 0:
    raise ValueError("Invalid key length in bits: " + str(key_len))
  return os.urandom(key_len // 8)

# Does the kernel support this algorithm?
def HaveAlgo(crypt_algo, auth_algo, aead_algo):
  try:
    test_xfrm = xfrm.Xfrm()
    test_xfrm.FlushSaInfo()
    test_xfrm.FlushPolicyInfo()

    test_xfrm.AddSaInfo(
        src=xfrm_test.TEST_ADDR1,
        dst=xfrm_test.TEST_ADDR2,
        spi=xfrm_test.TEST_SPI,
        mode=xfrm.XFRM_MODE_TRANSPORT,
        reqid=100,
        encryption=(crypt_algo,
                    GenerateKey(crypt_algo.key_len)) if crypt_algo else None,
        auth_trunc=(auth_algo,
                    GenerateKey(auth_algo.key_len)) if auth_algo else None,
        aead=(aead_algo, GenerateKey(aead_algo.key_len)) if aead_algo else None,
        encap=None,
        mark=None,
        output_mark=None)

    test_xfrm.FlushSaInfo()
    test_xfrm.FlushPolicyInfo()

    return True
  except IOError as err:
    if err.errno == ENOSYS:
      return False
    else:
      print("Unexpected error:", err.errno)
      return True

# Dictionary to record the algorithm state. Mark the state True if this
# algorithm is enforced or enabled on this kernel. Otherwise, mark it
# False.
algoState = {}

def AlgoEnforcedOrEnabled(crypt, auth, aead, target_algo, target_kernel):
  if algoState.get(target_algo) is None:
    algoState[target_algo] = net_test.LINUX_VERSION >= target_kernel or HaveAlgo(
        crypt, auth, aead)
  return algoState.get(target_algo)

# Return true if this algorithm should be enforced or is enabled on this kernel
def AuthEnforcedOrEnabled(authCase):
  auth = authCase[0]
  crypt = xfrm.XfrmAlgo((b"ecb(cipher_null)", 0))
  return AlgoEnforcedOrEnabled(crypt, auth, None, auth.name, authCase[1])

# Return true if this algorithm should be enforced or is enabled on this kernel
def CryptEnforcedOrEnabled(cryptCase):
  crypt = cryptCase[0]
  auth = xfrm.XfrmAlgoAuth((b"digest_null", 0, 0))
  return AlgoEnforcedOrEnabled(crypt, auth, None, crypt.name, cryptCase[1])

# Return true if this algorithm should be enforced or is enabled on this kernel
def AeadEnforcedOrEnabled(aeadCase):
  aead = aeadCase[0]
  return AlgoEnforcedOrEnabled(None, None, aead, aead.name, aeadCase[1])

def InjectTests():
  XfrmAlgorithmTest.InjectTests()


class XfrmAlgorithmTest(xfrm_base.XfrmLazyTest):
  @classmethod
  def InjectTests(cls):
    VERSIONS = (4, 6)
    TYPES = (SOCK_DGRAM, SOCK_STREAM)

    # Tests all combinations of auth & crypt. Mutually exclusive with aead.
    param_list = itertools.product(VERSIONS, TYPES, AUTH_ALGOS, CRYPT_ALGOS,
                                   [None])
    util.InjectParameterizedTest(cls, param_list, cls.TestNameGenerator)

    # Tests all combinations of aead. Mutually exclusive with auth/crypt.
    param_list = itertools.product(VERSIONS, TYPES, [None], [None], AEAD_ALGOS)
    util.InjectParameterizedTest(cls, param_list, cls.TestNameGenerator)

  @staticmethod
  def TestNameGenerator(version, proto, authCase, cryptCase, aeadCase):
    # Produce a unique and readable name for each test. e.g.
    #     testSocketPolicySimple_cbc-aes_256_hmac-sha512_512_256_IPv6_UDP
    param_string = ""
    if cryptCase is not None:
      crypt = cryptCase[0]
      param_string += "%s_%d_" % (crypt.name.decode(), crypt.key_len)

    if authCase is not None:
      auth = authCase[0]
      param_string += "%s_%d_%d_" % (auth.name.decode(), auth.key_len,
          auth.trunc_len)

    if aeadCase is not None:
      aead = aeadCase[0]
      param_string += "%s_%d_%d_" % (aead.name.decode(), aead.key_len,
          aead.icv_len)

    param_string += "%s_%s" % ("IPv4" if version == 4 else "IPv6",
        "UDP" if proto == SOCK_DGRAM else "TCP")
    return param_string

  def ParamTestSocketPolicySimple(self, version, proto, authCase, cryptCase, aeadCase):
    """Test two-way traffic using transport mode and socket policies."""

    # Bypass the test if any algorithm going to be tested is not enforced
    # or enabled on this kernel
    if authCase is not None and not AuthEnforcedOrEnabled(authCase):
      return
    if cryptCase is not None and not CryptEnforcedOrEnabled(cryptCase):
      return
    if aeadCase is not None and not AeadEnforcedOrEnabled(aeadCase):
      return

    auth = authCase[0] if authCase else None
    crypt = cryptCase[0] if cryptCase else None
    aead = aeadCase[0] if aeadCase else None

    def AssertEncrypted(packet):
      # This gives a free pass to ICMP and ICMPv6 packets, which show up
      # nondeterministically in tests.
      self.assertEqual(None,
                        packet.getlayer(scapy.UDP),
                        "UDP packet sent in the clear")
      self.assertEqual(None,
                        packet.getlayer(scapy.TCP),
                        "TCP packet sent in the clear")

    # We create a pair of sockets, "left" and "right", that will talk to each
    # other using transport mode ESP. Because of TapTwister, both sockets
    # perceive each other as owning "remote_addr".
    netid = self.RandomNetid()
    family = net_test.GetAddressFamily(version)
    local_addr = self.MyAddress(version, netid)
    remote_addr = self.GetRemoteSocketAddress(version)
    auth_left = (xfrm.XfrmAlgoAuth((auth.name, auth.key_len, auth.trunc_len)),
                 os.urandom(auth.key_len // 8)) if auth else None
    auth_right = (xfrm.XfrmAlgoAuth((auth.name, auth.key_len, auth.trunc_len)),
                  os.urandom(auth.key_len // 8)) if auth else None
    crypt_left = (xfrm.XfrmAlgo((crypt.name, crypt.key_len)),
                  os.urandom(crypt.key_len // 8)) if crypt else None
    crypt_right = (xfrm.XfrmAlgo((crypt.name, crypt.key_len)),
                   os.urandom(crypt.key_len // 8)) if crypt else None
    aead_left = (xfrm.XfrmAlgoAead((aead.name, aead.key_len, aead.icv_len)),
                 os.urandom(aead.key_len // 8)) if aead else None
    aead_right = (xfrm.XfrmAlgoAead((aead.name, aead.key_len, aead.icv_len)),
                  os.urandom(aead.key_len // 8)) if aead else None
    spi_left = 0xbeefface
    spi_right = 0xcafed00d
    req_ids = [100, 200, 300, 400]  # Used to match templates and SAs.

    # Left outbound SA
    self.xfrm.AddSaInfo(
        src=local_addr,
        dst=remote_addr,
        spi=spi_right,
        mode=xfrm.XFRM_MODE_TRANSPORT,
        reqid=req_ids[0],
        encryption=crypt_right,
        auth_trunc=auth_right,
        aead=aead_right,
        encap=None,
        mark=None,
        output_mark=None)
    # Right inbound SA
    self.xfrm.AddSaInfo(
        src=remote_addr,
        dst=local_addr,
        spi=spi_right,
        mode=xfrm.XFRM_MODE_TRANSPORT,
        reqid=req_ids[1],
        encryption=crypt_right,
        auth_trunc=auth_right,
        aead=aead_right,
        encap=None,
        mark=None,
        output_mark=None)
    # Right outbound SA
    self.xfrm.AddSaInfo(
        src=local_addr,
        dst=remote_addr,
        spi=spi_left,
        mode=xfrm.XFRM_MODE_TRANSPORT,
        reqid=req_ids[2],
        encryption=crypt_left,
        auth_trunc=auth_left,
        aead=aead_left,
        encap=None,
        mark=None,
        output_mark=None)
    # Left inbound SA
    self.xfrm.AddSaInfo(
        src=remote_addr,
        dst=local_addr,
        spi=spi_left,
        mode=xfrm.XFRM_MODE_TRANSPORT,
        reqid=req_ids[3],
        encryption=crypt_left,
        auth_trunc=auth_left,
        aead=aead_left,
        encap=None,
        mark=None,
        output_mark=None)

    # Make two sockets.
    sock_left = socket(family, proto, 0)
    sock_left.settimeout(2.0)
    sock_left.setsockopt(SOL_SOCKET, SO_REUSEADDR, 1)
    self.SelectInterface(sock_left, netid, "mark")
    sock_right = socket(family, proto, 0)
    sock_right.settimeout(2.0)
    sock_right.setsockopt(SOL_SOCKET, SO_REUSEADDR, 1)
    self.SelectInterface(sock_right, netid, "mark")

    # For UDP, set SO_LINGER to 0, to prevent TCP sockets from hanging around
    # in a TIME_WAIT state.
    if proto == SOCK_STREAM:
      net_test.DisableFinWait(sock_left)
      net_test.DisableFinWait(sock_right)

    # Apply the left outbound socket policy.
    xfrm_base.ApplySocketPolicy(sock_left, family, xfrm.XFRM_POLICY_OUT,
                                spi_right, req_ids[0], None)
    # Apply right inbound socket policy.
    xfrm_base.ApplySocketPolicy(sock_right, family, xfrm.XFRM_POLICY_IN,
                                spi_right, req_ids[1], None)
    # Apply right outbound socket policy.
    xfrm_base.ApplySocketPolicy(sock_right, family, xfrm.XFRM_POLICY_OUT,
                                spi_left, req_ids[2], None)
    # Apply left inbound socket policy.
    xfrm_base.ApplySocketPolicy(sock_left, family, xfrm.XFRM_POLICY_IN,
                                spi_left, req_ids[3], None)

    server_ready = threading.Event()
    server_error = None  # Save exceptions thrown by the server.

    def TcpServer(sock, client_port):
      try:
        sock.listen(1)
        server_ready.set()
        accepted, peer = sock.accept()
        self.assertEqual(remote_addr, peer[0])
        self.assertEqual(client_port, peer[1])
        data = accepted.recv(2048)
        self.assertEqual(b"hello request", data)
        accepted.send(b"hello response")
      except Exception as e:
        server_error = e
      finally:
        sock.close()

    def UdpServer(sock, client_port):
      try:
        server_ready.set()
        data, peer = sock.recvfrom(2048)
        self.assertEqual(remote_addr, peer[0])
        self.assertEqual(client_port, peer[1])
        self.assertEqual(b"hello request", data)
        sock.sendto(b"hello response", peer)
      except Exception as e:
        server_error = e
      finally:
        sock.close()

    # Server and client need to know each other's port numbers in advance.
    wildcard_addr = net_test.GetWildcardAddress(version)
    sock_left.bind((wildcard_addr, 0))
    sock_right.bind((wildcard_addr, 0))
    left_port = sock_left.getsockname()[1]
    right_port = sock_right.getsockname()[1]

    # Start the appropriate server type on sock_right.
    target = TcpServer if proto == SOCK_STREAM else UdpServer
    server = threading.Thread(
        target=target,
        args=(sock_right, left_port),
        name="SocketServer")
    server.start()
    # Wait for server to be ready before attempting to connect. TCP retries
    # hide this problem, but UDP will fail outright if the server socket has
    # not bound when we send.
    self.assertTrue(server_ready.wait(2.0), "Timed out waiting for server thread")

    with TapTwister(fd=self.tuns[netid].fileno(), validator=AssertEncrypted):
      sock_left.connect((remote_addr, right_port))
      sock_left.send(b"hello request")
      data = sock_left.recv(2048)
      self.assertEqual(b"hello response", data)
      sock_left.close()
      server.join(timeout=2.0)
      self.assertFalse(server.is_alive(), "Timed out waiting for server exit")
    if server_error:
      raise server_error


if __name__ == "__main__":
  XfrmAlgorithmTest.InjectTests()
  unittest.main()
