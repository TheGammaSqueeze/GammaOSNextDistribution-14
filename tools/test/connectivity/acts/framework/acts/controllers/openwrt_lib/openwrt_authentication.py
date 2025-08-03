"""Module for OpenWrt SSH authentication.

This module provides a class, OpenWrtAuth, for managing SSH authentication for
OpenWrt devices. It allows you to generate RSA key pairs, save them in a
specified directory, and upload the public key to a remote host.

Usage:
  1. Create an instance of OpenWrtAuth with the required parameters.
  2. Call generate_rsa_key() to generate RSA key pairs and save them.
  3. Call send_public_key_to_remote_host() to upload the public key
     to the remote host.
"""

import logging
import os
import paramiko
import scp


_REMOTE_PATH = "/etc/dropbear/authorized_keys"


class OpenWrtAuth:
  """Class for managing SSH authentication for OpenWrt devices."""

  def __init__(self, hostname, username="root", password="root", port=22):
    """Initializes a new instance of the OpenWrtAuth class.

    Args:
      hostname (str): The hostname or IP address of the remote device.
      username (str): The username for authentication.
      password (str): The password for authentication.
      port (int): The port number for SSH.

    Attributes:
      public_key (str): The generated public key.
      public_key_file (str): The path to the generated public key file.
      private_key_file (str): The path to the generated private key file.
    """
    self.hostname = hostname
    self.username = username
    self.password = password
    self.port = port
    self.public_key = None
    self.key_dir = "/tmp/openwrt/"
    self.public_key_file = f"{self.key_dir}id_rsa_{self.hostname}.pub"
    self.private_key_file = f"{self.key_dir}id_rsa_{self.hostname}"

  def generate_rsa_key(self):
    """Generates an RSA key pair and saves it to the specified directory.

    Raises:
      ValueError:
        If an error occurs while generating the RSA key pair.
      paramiko.SSHException:
        If an error occurs while generating the RSA key pair.
      FileNotFoundError:
        If the directory for saving the private or public key does not exist.
      PermissionError:
        If there is a permission error while creating the directory
        for saving the keys.
      Exception:
        If an unexpected error occurs while generating the RSA key pair.
    """
    # Checks if the private and public key files already exist.
    if os.path.exists(self.private_key_file) and os.path.exists(
        self.public_key_file
    ):
      logging.warning("RSA key pair already exists, skipping key generation.")
      return

    try:
      # Generates an RSA key pair in /tmp/openwrt/ directory.
      logging.info("Generating RSA key pair...")
      key = paramiko.RSAKey.generate(bits=2048)
      self.public_key = f"ssh-rsa {key.get_base64()}"
      logging.debug("Public key: %s", self.public_key)

      # Create /tmp/openwrt/ directory if it doesn't exist.
      logging.info("Creating %s directory...", self.key_dir)
      os.makedirs(self.key_dir, exist_ok=True)

      # Saves the private key to a file.
      key.write_private_key_file(self.private_key_file)
      logging.debug("Saved private key to file: %s", self.private_key_file)

      # Saves the public key to a file.
      with open(self.public_key_file, "w") as f:
        f.write(self.public_key)
      logging.debug("Saved public key to file: %s", self.public_key_file)
    except (ValueError, paramiko.SSHException, PermissionError) as e:
      logging.error("An error occurred while generating "
                    "the RSA key pair: %s", e)
    except Exception as e:
      logging.error("An unexpected error occurred while generating "
                    "the RSA key pair: %s", e)

  def send_public_key_to_remote_host(self):
    """Uploads the public key to the remote host.

    Raises:
      paramiko.AuthenticationException:
        If authentication to the remote host fails.
      paramiko.SSHException:
        If an SSH-related error occurs during the connection.
      FileNotFoundError:
        If the public key file or the private key file does not exist.
      Exception: If an unexpected error occurs while sending the public key.
    """
    try:
      # Connects to the remote host and uploads the public key.
      logging.info("Uploading public key to remote host %s...", self.hostname)
      with paramiko.SSHClient() as ssh:
        ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        ssh.connect(hostname=self.hostname,
                    port=self.port,
                    username=self.username,
                    password=self.password)
        scp_client = scp.SCPClient(ssh.get_transport())
        scp_client.put(self.public_key_file, _REMOTE_PATH)
      logging.info("Public key uploaded successfully.")
    except (paramiko.AuthenticationException,
            paramiko.SSHException,
            FileNotFoundError) as e:
      logging.error("An error occurred while sending the public key: %s", e)
    except Exception as e:
      logging.error("An unexpected error occurred while "
                    "sending the public key: %s", e)
