# Copyright 2022 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
r"""Mkcert entry point.

Mkcert will handle the SSL certificates process to secure WEB browser of
a local or remote instance of an Android Virtual Device.
"""

import filecmp
import logging
import os
import platform
import shutil
import stat

from acloud.internal import constants
from acloud.internal.lib import utils

logger = logging.getLogger(__name__)

_CA_NAME = constants.SSL_CA_NAME
_CERT_DIR = os.path.join(os.path.expanduser("~"), constants.SSL_DIR)
_CA_KEY_PATH = os.path.join(_CERT_DIR, f"{_CA_NAME}.key")
_CA_CRT_PATH = os.path.join(_CERT_DIR, f"{_CA_NAME}.pem")
_CERT_KEY_PATH = os.path.join(_CERT_DIR, "server.key")
_CERT_CSR_PATH = os.path.join(_CERT_DIR, "server.csr")
_CERT_CRT_PATH = os.path.join(_CERT_DIR, "server.crt")
_CA_EXT = "keyUsage=critical,keyCertSign"
_CA_SUBJ="/OU=acloud/O=acloud development CA/CN=localhost"
_CERT_SUBJ = "/OU=%s/O=acloud development CA" % platform.node()
_TRUST_CA_PATH = os.path.join(constants.SSL_TRUST_CA_DIR,
                              f"{_CA_NAME}.crt")
_CERT_CRT_EXT = ";".join(f"echo \"{ext}\"" for ext in [
    "keyUsage = critical, digitalSignature, keyEncipherment",
    "extendedKeyUsage = serverAuth",
    "subjectAltName = DNS.1:localhost, IP.1:0.0.0.0, IP.2:::1"])

# Generate a Root SSL Certificate.
_CA_CMD = (f"openssl req -new -x509 -days 9999 -newkey rsa:2048 "
           f"-sha256 -nodes -keyout \"{_CA_KEY_PATH}\" "
           f"-out \"{_CA_CRT_PATH}\" -extensions v3_ca "
           f"-subj \"{_CA_SUBJ}\" -addext \"{_CA_EXT}\"")

# Trust the Root SSL Certificate.
_TRUST_CA_COPY_CMD = f"sudo cp -p {_CA_CRT_PATH} {_TRUST_CA_PATH}"
_UPDATE_TRUST_CA_CMD = "sudo update-ca-certificates"
_TRUST_CHROME_CMD = (
    "certutil -d sql:$HOME/.pki/nssdb -A -t TC "
    f"-n \"{_CA_NAME}\" -i \"{_TRUST_CA_PATH}\"")

# Generate an SSL SAN Certificate with the Root Certificate.
_CERT_KEY_CMD = f"openssl genrsa -out \"{_CERT_KEY_PATH}\" 2048"
_CERT_CSR_CMD = (f"openssl req -new -key \"{_CERT_KEY_PATH}\" "
                 f"-out \"{_CERT_CSR_PATH}\" -subj \"{_CERT_SUBJ}\"")
_CERT_CRT_CMD = (
    f"openssl x509 -req -days 9999 -in \"{_CERT_CSR_PATH}\" "
    f"-CA \"{_CA_CRT_PATH}\" -CAkey \"{_CA_KEY_PATH}\" "
    f"-CAcreateserial -out \"{_CERT_CRT_PATH}\" "
    f"-extfile <({_CERT_CRT_EXT};)")

# UnInstall the Root SSL Certificate.
_UNDO_TRUST_CA_CMD = f"sudo rm {_TRUST_CA_PATH}"
_UNDO_TRUST_CHROME_CMD = f"certutil -D -d sql:$HOME/.pki/nssdb -n \"{_CA_NAME}\""


def Install():
    """Install Root SSL Certificates by the openssl tool.

    Generates a Root SSL Certificates and setup the host environment
    to build a secure browser for WebRTC AVD.

    Returns:
        True when the Root SSL Certificates are generated and setup.
    """
    if os.path.isdir(_CERT_DIR):
        shutil.rmtree(_CERT_DIR)
    os.mkdir(_CERT_DIR)

    if os.path.exists(_TRUST_CA_PATH):
        UnInstall()

    utils.Popen(_CA_CMD, shell=True)
    # The rootCA.pem file should grant READ permission to others.
    if not os.stat(_CA_CRT_PATH).st_mode & stat.S_IROTH:
        os.chmod(_CA_CRT_PATH, stat.S_IRUSR | stat.S_IWUSR | stat.S_IRGRP | stat.S_IROTH)
    utils.Popen(_TRUST_CA_COPY_CMD, shell=True)
    utils.Popen(_UPDATE_TRUST_CA_CMD, shell=True)
    utils.Popen(_TRUST_CHROME_CMD, shell=True)

    return IsRootCAReady()


def AllocateLocalHostCert():
    """Allocate localhost certificate by the openssl tool.

    Generate an SSL SAN Certificate with the Root Certificate.

    Returns:
        True if the certificates is exist.
    """
    if not IsRootCAReady():
        logger.debug("Can't load CA files.")
        return False

    if not os.path.exists(_CERT_KEY_PATH):
        utils.Popen(_CERT_KEY_CMD, shell=True)
    if not os.path.exists(_CERT_CSR_PATH):
        utils.Popen(_CERT_CSR_CMD, shell=True)
    if not os.path.exists(_CERT_CRT_PATH):
        utils.Popen(_CERT_CRT_CMD, shell=True)

    return IsCertificateReady()


def IsRootCAReady():
    """Check if the Root SSL Certificates are all ready.

    Returns:
        True if the Root SSL Certificates are exist.
    """
    for cert_file_name in [_CA_KEY_PATH, _CA_CRT_PATH, _TRUST_CA_PATH]:
        if not os.path.exists(cert_file_name):
            logger.debug("Root SSL Certificate: %s, does not exist",
                         cert_file_name)
            return False
    # TODO: this check can be delete when the mkcert mechanism is stable.
    if not os.stat(_TRUST_CA_PATH).st_mode & stat.S_IROTH:
        return False

    if not filecmp.cmp(_CA_CRT_PATH, _TRUST_CA_PATH):
        logger.debug("The trusted CA %s file is not the same with %s ",
                     _TRUST_CA_PATH, _CA_CRT_PATH)
        return False
    return True


def IsCertificateReady():
    """Check if the SSL SAN Certificates files are all ready.

    Returns:
        True if the SSL SAN Certificates files existed.
    """
    for cert_file_name in [_CERT_KEY_PATH, _CERT_CRT_PATH]:
        if not os.path.exists(cert_file_name):
            logger.debug("SSL SAN Certificate: %s, does not exist",
                         cert_file_name)
            return False
    return True


def UnInstall():
    """Uninstall a Root SSL Certificate.

    Undo the Root SSL Certificate host setup.
    """
    utils.Popen(_UNDO_TRUST_CA_CMD, shell=True)
    utils.Popen(_UPDATE_TRUST_CA_CMD, shell=True)
    utils.Popen(_UNDO_TRUST_CHROME_CMD, shell=True)
