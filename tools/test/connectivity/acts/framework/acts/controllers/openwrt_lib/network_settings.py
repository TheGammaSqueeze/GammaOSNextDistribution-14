#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import re
import time

from acts import signals
from acts import utils
from acts.controllers.openwrt_lib import network_const


SERVICE_DNSMASQ = "dnsmasq"
SERVICE_STUNNEL = "stunnel"
SERVICE_NETWORK = "network"
SERVICE_PPTPD = "pptpd"
SERVICE_FIREWALL = "firewall"
SERVICE_IPSEC = "ipsec"
SERVICE_XL2TPD = "xl2tpd"
SERVICE_ODHCPD = "odhcpd"
SERVICE_OPENNDS = "opennds"
SERVICE_UHTTPD = "uhttpd"
PPTP_PACKAGE = "pptpd kmod-nf-nathelper-extra"
L2TP_PACKAGE = "strongswan-full openssl-util xl2tpd"
NAT6_PACKAGE = "ip6tables kmod-ipt-nat6"
CAPTIVE_PORTAL_PACKAGE = "opennds php7-cli php7-mod-openssl php7-cgi php7"
MDNS_PACKAGE = "avahi-utils avahi-daemon-service-http avahi-daemon-service-ssh libavahi-client avahi-dbus-daemon"
STUNNEL_CONFIG_PATH = "/etc/stunnel/DoTServer.conf"
HISTORY_CONFIG_PATH = "/etc/dirty_configs"
PPTPD_OPTION_PATH = "/etc/ppp/options.pptpd"
XL2TPD_CONFIG_PATH = "/etc/xl2tpd/xl2tpd.conf"
XL2TPD_OPTION_CONFIG_PATH = "/etc/ppp/options.xl2tpd"
FIREWALL_CUSTOM_OPTION_PATH = "/etc/firewall.user"
PPP_CHAP_SECRET_PATH = "/etc/ppp/chap-secrets"
IKEV2_VPN_CERT_KEYS_PATH = "/var/ikev2_cert.sh"
TCPDUMP_DIR = "/tmp/tcpdump/"
LOCALHOST = "192.168.1.1"
DEFAULT_PACKAGE_INSTALL_TIMEOUT = 200


class NetworkSettings(object):
    """Class for network settings.

    Attributes:
        ssh: ssh connection object.
        ssh_settings: ssh settings for AccessPoint.
        service_manager: Object manage service configuration.
        user: username for ssh.
        ip: ip address for AccessPoint.
        log: Logging object for AccessPoint.
        config: A list to store changes on network settings.
        firewall_rules_list: A list of firewall rule name list.
        cleanup_map: A dict for compare oppo functions.
        l2tp: profile for vpn l2tp server.
    """

    def __init__(self, ssh, ssh_settings, logger):
        """Initialize wireless settings.

        Args:
            ssh: ssh connection object.
            ssh_settings: ssh settings for AccessPoint.
            logger: Logging object for AccessPoint.
        """
        self.ssh = ssh
        self.service_manager = ServiceManager(ssh)
        self.ssh_settings = ssh_settings
        self.user = self.ssh_settings.username
        self.ip = self.ssh_settings.hostname
        self.log = logger
        self.config = set()
        self.firewall_rules_list = []
        self.cleanup_map = {
            "setup_dns_server": self.remove_dns_server,
            "setup_vpn_pptp_server": self.remove_vpn_pptp_server,
            "setup_vpn_l2tp_server": self.remove_vpn_l2tp_server,
            "disable_ipv6": self.enable_ipv6,
            "setup_ipv6_bridge": self.remove_ipv6_bridge,
            "default_dns": self.del_default_dns,
            "default_v6_dns": self.del_default_v6_dns,
            "ipv6_prefer_option": self.remove_ipv6_prefer_option,
            "block_dns_response": self.unblock_dns_response,
            "setup_mdns": self.remove_mdns,
            "add_dhcp_rapid_commit": self.remove_dhcp_rapid_commit,
            "setup_captive_portal": self.remove_cpative_portal
        }
        # This map contains cleanup functions to restore the configuration to
        # its default state. We write these keys to HISTORY_CONFIG_PATH prior to
        # making any changes to that subsystem.
        # This makes it easier to recover after an aborted test.
        self.update_firewall_rules_list()
        self.cleanup_network_settings()
        self.clear_tcpdump()

    def cleanup_network_settings(self):
        """Reset all changes on Access point."""

        # Detect if any changes that is not clean up.
        if self.file_exists(HISTORY_CONFIG_PATH):
            out = self.ssh.run("cat %s" % HISTORY_CONFIG_PATH).stdout
            if out:
                self.config = set(out.split("\n"))

        if self.config:
            temp = self.config.copy()
            for change in temp:
                change_list = change.split()
                if len(change_list) > 1:
                    self.cleanup_map[change_list[0]](*change_list[1:])
                else:
                    self.cleanup_map[change]()
            self.config = set()

        if self.file_exists(HISTORY_CONFIG_PATH):
            out = self.ssh.run("cat %s" % HISTORY_CONFIG_PATH).stdout
            if not out:
                self.ssh.run("rm %s" % HISTORY_CONFIG_PATH)

    def commit_changes(self):
        """Apply changes on Access point."""
        self.ssh.run("uci commit")
        self.service_manager.restart_services()
        self.create_config_file("\n".join(self.config),
                                HISTORY_CONFIG_PATH)

    def package_install(self, package_list):
        """Install packages on OpenWrtAP via opkg If not installed.

        Args:
            package_list: package list to install.
                          e.g. "pptpd kmod-mppe kmod-nf-nathelper-extra"
        """
        self.ssh.run("opkg update")
        for package_name in package_list.split(" "):
            if not self._package_installed(package_name):
                self.ssh.run("opkg install %s" % package_name,
                             timeout=DEFAULT_PACKAGE_INSTALL_TIMEOUT)
                self.log.info("Package: %s installed." % package_name)
            else:
                self.log.info("Package: %s skipped (already installed)." % package_name)

    def package_remove(self, package_list):
        """Remove packages on OpenWrtAP via opkg If existed.

        Args:
            package_list: package list to remove.
        """
        for package_name in package_list.split(" "):
            if self._package_installed(package_name):
                self.ssh.run("opkg remove %s" % package_name)
                self.log.info("Package: %s removed." % package_name)
            else:
                self.log.info("No exist package %s found." % package_name)

    def _package_installed(self, package_name):
        """Check if target package installed on OpenWrtAP.

        Args:
            package_name: package name want to check.

        Returns:
            True if installed.
        """
        if self.ssh.run("opkg list-installed %s" % package_name).stdout:
            return True
        return False

    def file_exists(self, abs_file_path):
        """Check if target file exist on specific path on OpenWrt.

        Args:
            abs_file_path: Absolute path for the file.

        Returns:
            True if Existed.
        """
        path, file_name = abs_file_path.rsplit("/", 1)
        if self.ssh.run("ls %s | grep %s" % (path, file_name),
                        ignore_status=True).stdout:
            return True
        return False

    def path_exists(self, abs_path):
        """Check if dir exist on OpenWrt.

        Args:
            abs_path: absolutely path for create folder.
        """
        try:
            self.ssh.run("ls %s" % abs_path)
        except:
            return False
        return True

    def create_folder(self, abs_path):
        """If dir not exist, create it.

        Args:
            abs_path: absolutely path for create folder.
        """
        if not self.path_exists(abs_path):
            self.ssh.run("mkdir %s" % abs_path)
        else:
            self.log.info("%s already existed." %abs_path)

    def count(self, config, key):
        """Count in uci config.

        Args:
            config: config or section to research
            key: keywords to  e.g. rule, domain
        Returns:
            Numbers of the count.
        """
        count = self.ssh.run("uci show %s | grep =%s" % (config, key),
                             ignore_status=True).stdout
        return len(count.split("\n"))

    def create_config_file(self, config, file_path):
        """Create config file. Overwrite if file already exist.

        Args:
            config: A string of content of config.
            file_path: Config's abs_path.
        """
        self.ssh.run("echo -e \"%s\" > %s" % (config, file_path))

    def replace_config_option(self, old_option, new_option, file_path):
        """Replace config option if pattern match.

        If find match pattern with old_option, then replace it with new_option.
        Else add new_option to the file.

        Args:
            old_option: the regexp pattern to replace.
            new_option: the option to add.
            file_path: Config's abs_path.
        """
        config = self.ssh.run("cat %s" % file_path).stdout
        config, count = re.subn(old_option, new_option, config)
        if not count:
            config = "\n".join([config, new_option])
        self.create_config_file(config, file_path)

    def remove_config_option(self, option, file_path):
        """Remove option from config file.

        Args:
            option: Option to remove. Support regular expression.
            file_path: Config's abs_path.
        Returns:
            Boolean for find option to remove.
        """
        config = self.ssh.run("cat %s" % file_path).stdout.split("\n")
        for line in config:
            count = re.subn(option, "", line)[1]
            if count > 0:
                config.remove(line)
                self.create_config_file("\n".join(config), file_path)
                return True
        self.log.warning("No match option to remove.")
        return False

    def setup_dns_server(self, domain_name):
        """Setup DNS server on OpenWrtAP.

        Args:
            domain_name: Local dns domain name.
        """
        self.config.add("setup_dns_server")
        self.log.info("Setup DNS server with domain name %s" % domain_name)
        self.ssh.run("uci set dhcp.@dnsmasq[0].local='/%s/'" % domain_name)
        self.ssh.run("uci set dhcp.@dnsmasq[0].domain='%s'" % domain_name)
        self.add_resource_record(domain_name, self.ip)
        self.service_manager.need_restart(SERVICE_DNSMASQ)
        self.commit_changes()

        # Check stunnel package is installed
        self.package_install("stunnel")
        self.service_manager.stop(SERVICE_STUNNEL)
        self.service_manager.disable(SERVICE_STUNNEL)

        # Enable stunnel
        self.create_stunnel_config()
        self.ssh.run("stunnel /etc/stunnel/DoTServer.conf")

    def remove_dns_server(self):
        """Remove DNS server on OpenWrtAP."""
        if self.file_exists("/var/run/stunnel.pid"):
            self.ssh.run("kill $(cat /var/run/stunnel.pid)")
        self.ssh.run("uci set dhcp.@dnsmasq[0].local='/lan/'")
        self.ssh.run("uci set dhcp.@dnsmasq[0].domain='lan'")
        self.clear_resource_record()
        self.service_manager.need_restart(SERVICE_DNSMASQ)
        self.config.discard("setup_dns_server")
        self.commit_changes()

    def add_resource_record(self, domain_name, domain_ip):
        """Add resource record.

        Args:
            domain_name: A string for domain name.
            domain_ip: A string for domain ip.
        """
        self.ssh.run("uci add dhcp domain")
        self.ssh.run("uci set dhcp.@domain[-1].name='%s'" % domain_name)
        self.ssh.run("uci set dhcp.@domain[-1].ip='%s'" % domain_ip)
        self.service_manager.need_restart(SERVICE_DNSMASQ)

    def del_resource_record(self):
        """Delete the last resource record."""
        self.ssh.run("uci delete dhcp.@domain[-1]")
        self.service_manager.need_restart(SERVICE_DNSMASQ)

    def clear_resource_record(self):
        """Delete the all resource record."""
        rr = self.ssh.run("uci show dhcp | grep =domain",
                          ignore_status=True).stdout
        if rr:
            for _ in rr.split("\n"):
                self.del_resource_record()
        self.service_manager.need_restart(SERVICE_DNSMASQ)

    def create_stunnel_config(self):
        """Create config for stunnel service."""
        stunnel_config = [
            "pid = /var/run/stunnel.pid",
            "[dns]",
            "accept = 853",
            "connect = 127.0.0.1:53",
            "cert = /etc/stunnel/fullchain.pem",
            "key = /etc/stunnel/privkey.pem",
        ]
        config_string = "\n".join(stunnel_config)
        self.create_config_file(config_string, STUNNEL_CONFIG_PATH)

    def setup_vpn_pptp_server(self, local_ip, user, password):
        """Setup pptp vpn server on OpenWrt.

        Args:
            local_ip: local pptp server ip address.
            user: username for pptp user.
            password: password for pptp user.
        """
        #  Install pptp service
        self.package_install(PPTP_PACKAGE)

        self.config.add("setup_vpn_pptp_server")
        # Edit /etc/config/pptpd & /etc/ppp/options.pptpd
        self.setup_pptpd(local_ip, user, password)
        # Edit /etc/config/firewall & /etc/firewall.user
        self.setup_firewall_rules_for_pptp()
        # Enable service
        self.service_manager.enable(SERVICE_PPTPD)
        self.service_manager.need_restart(SERVICE_PPTPD)
        self.service_manager.need_restart(SERVICE_FIREWALL)
        self.commit_changes()

    def remove_vpn_pptp_server(self):
        """Remove pptp vpn server on OpenWrt."""
        # Edit /etc/config/pptpd
        self.restore_pptpd()
        # Edit /etc/config/firewall & /etc/firewall.user
        self.restore_firewall_rules_for_pptp()
        # Disable service
        self.service_manager.disable(SERVICE_PPTPD)
        self.service_manager.need_restart(SERVICE_PPTPD)
        self.service_manager.need_restart(SERVICE_FIREWALL)
        self.config.discard("setup_vpn_pptp_server")
        self.commit_changes()

        self.package_remove(PPTP_PACKAGE)
        self.ssh.run("rm /etc/ppp/options.pptpd")
        self.ssh.run("rm /etc/config/pptpd")

    def setup_pptpd(self, local_ip, username, password, ms_dns="8.8.8.8"):
        """Setup pptpd config for ip addr and account.

        Args:
            local_ip: vpn server address
            username: pptp vpn username
            password: pptp vpn password
            ms_dns: DNS server
        """
        # Calculate remote ip address
        # e.g. local_ip = 10.10.10.9
        # remote_ip = 10.10.10.10 -250
        remote_ip = local_ip.split(".")
        remote_ip.append(str(int(remote_ip.pop(-1)) + 1))
        remote_ip = ".".join(remote_ip)
        # Enable pptp service and set ip addr
        self.ssh.run("uci set pptpd.pptpd.enabled=1")
        self.ssh.run("uci set pptpd.pptpd.localip='%s'" % local_ip)
        self.ssh.run("uci set pptpd.pptpd.remoteip='%s-250'" % remote_ip)

        # Setup pptp service account
        self.ssh.run("uci set pptpd.@login[0].username='%s'" % username)
        self.ssh.run("uci set pptpd.@login[0].password='%s'" % password)
        self.service_manager.need_restart(SERVICE_PPTPD)

        self.replace_config_option(r"#*ms-dns \d+.\d+.\d+.\d+",
                                   "ms-dns %s" % ms_dns, PPTPD_OPTION_PATH)
        self.replace_config_option("(#no)*proxyarp",
                                   "proxyarp", PPTPD_OPTION_PATH)

    def restore_pptpd(self):
        """Disable pptpd."""
        self.ssh.run("uci set pptpd.pptpd.enabled=0")
        self.remove_config_option(r"\S+ pptp-server \S+ \*",
                                  PPP_CHAP_SECRET_PATH)
        self.service_manager.need_restart(SERVICE_PPTPD)

    def setup_vpn_l2tp_server(self,
                              vpn_server_hostname,
                              vpn_server_address,
                              vpn_username,
                              vpn_password,
                              psk_secret,
                              server_name,
                              country,
                              org):
        """Setup l2tp vpn server on OpenWrt.

        Args:
            vpn_server_hostname: vpn server domain name
            vpn_server_address: vpn server addr
            vpn_username: vpn account
            vpn_password: vpn password
            psk_secret: psk for ipsec
            server_name: vpn server name for register in OpenWrt
            country: country code for generate cert keys.
            org: Organization name for generate cert keys.
        """
        self.l2tp = network_const.VpnL2tp(vpn_server_hostname,
                                          vpn_server_address,
                                          vpn_username,
                                          vpn_password,
                                          psk_secret,
                                          server_name)

        self.package_install(L2TP_PACKAGE)
        self.config.add("setup_vpn_l2tp_server")

        # /etc/strongswan.conf: Strongswan configuration file
        self.setup_strongswan()
        # /etc/ipsec.conf /etc/ipsec.secrets
        self.setup_ipsec()
        # /etc/xl2tpd/xl2tpd.conf & /etc/ppp/options.xl2tpd
        self.setup_xl2tpd()
        # /etc/ppp/chap-secrets
        self.setup_ppp_secret()
        # /etc/config/firewall & /etc/firewall.user
        self.setup_firewall_rules_for_l2tp()
        # setup vpn server local ip
        self.setup_vpn_local_ip()
        # generate cert and key for rsa
        if self.l2tp.name == "ikev2-server":
            self.generate_ikev2_vpn_cert_keys(country, org)
            self.add_resource_record(self.l2tp.hostname, LOCALHOST)
        else:
            self.generate_vpn_cert_keys(country, org)
        # restart service
        self.service_manager.need_restart(SERVICE_IPSEC)
        self.service_manager.need_restart(SERVICE_XL2TPD)
        self.service_manager.need_restart(SERVICE_FIREWALL)
        self.commit_changes()

    def remove_vpn_l2tp_server(self):
        """Remove l2tp vpn server on OpenWrt."""
        self.config.discard("setup_vpn_l2tp_server")
        self.restore_firewall_rules_for_l2tp()
        self.remove_vpn_local_ip()
        if self.l2tp.name == "ikev2-server":
            self.clear_resource_record()
        self.service_manager.need_restart(SERVICE_IPSEC)
        self.service_manager.need_restart(SERVICE_XL2TPD)
        self.service_manager.need_restart(SERVICE_FIREWALL)
        self.commit_changes()
        self.package_remove(L2TP_PACKAGE)
        if hasattr(self, "l2tp"):
            delattr(self, "l2tp")

    def setup_strongswan(self, dns="8.8.8.8"):
        """Setup strongswan config."""
        config = [
            "charon {",
            "   load_modular = yes",
            "   plugins {",
            "       include strongswan.d/charon/*.conf",
            "   }",
            "   dns1=%s" % dns,
            "}"
        ]
        self.create_config_file("\n".join(config), "/etc/strongswan.conf")

    def setup_ipsec(self):
        """Setup ipsec config."""
        def load_ipsec_config(data, rightsourceip=False):
            for i in data.keys():
                config.append(i)
                for j in data[i].keys():
                    config.append("\t %s=%s" % (j, data[i][j]))
                if rightsourceip:
                    config.append("\t rightsourceip=%s.16/26" % self.l2tp.address.rsplit(".", 1)[0])
                config.append("")

        config = []
        load_ipsec_config(network_const.IPSEC_IKEV2_MSCHAPV2, True)
        load_ipsec_config(network_const.IPSEC_IKEV2_PSK, True)
        load_ipsec_config(network_const.IPSEC_IKEV2_RSA, True)
        load_ipsec_config(network_const.IPSEC_IKEV2_MSCHAPV2_HOSTNAME, True)
        load_ipsec_config(network_const.IPSEC_IKEV2_PSK_HOSTNAME, True)
        load_ipsec_config(network_const.IPSEC_IKEV2_RSA_HOSTNAME, True)
        load_ipsec_config(network_const.IPSEC_CONF)
        load_ipsec_config(network_const.IPSEC_L2TP_PSK)
        load_ipsec_config(network_const.IPSEC_L2TP_RSA)
        load_ipsec_config(network_const.IPSEC_HYBRID_RSA, True)
        load_ipsec_config(network_const.IPSEC_XAUTH_PSK, True)
        load_ipsec_config(network_const.IPSEC_XAUTH_RSA, True)
        self.create_config_file("\n".join(config), "/etc/ipsec.conf")

        ipsec_secret = []
        ipsec_secret.append(r": PSK \"%s\"" % self.l2tp.psk_secret)
        ipsec_secret.append(r": RSA \"%s\"" % "serverKey.der")
        ipsec_secret.append(r"%s : XAUTH \"%s\"" % (self.l2tp.username,
                                                    self.l2tp.password))
        self.create_config_file("\n".join(ipsec_secret), "/etc/ipsec.secrets")

    def setup_xl2tpd(self, ip_range=20):
        """Setup xl2tpd config."""
        net_id, host_id = self.l2tp.address.rsplit(".", 1)
        xl2tpd_conf = list(network_const.XL2TPD_CONF_GLOBAL)
        xl2tpd_conf.append("auth file = %s" % PPP_CHAP_SECRET_PATH)
        xl2tpd_conf.extend(network_const.XL2TPD_CONF_INS)
        xl2tpd_conf.append("ip range = %s.%s-%s.%s" %
                           (net_id, host_id, net_id,
                            str(int(host_id)+ip_range)))
        xl2tpd_conf.append("local ip = %s" % self.l2tp.address)
        xl2tpd_conf.append("name = %s" % self.l2tp.name)
        xl2tpd_conf.append("pppoptfile = %s" % XL2TPD_OPTION_CONFIG_PATH)

        self.create_config_file("\n".join(xl2tpd_conf), XL2TPD_CONFIG_PATH)
        xl2tpd_option = list(network_const.XL2TPD_OPTION)
        xl2tpd_option.append("name %s" % self.l2tp.name)
        self.create_config_file("\n".join(xl2tpd_option),
                                XL2TPD_OPTION_CONFIG_PATH)

    def setup_ppp_secret(self):
        self.replace_config_option(
            r"\S+ %s \S+ \*" % self.l2tp.name,
            "%s %s %s *" % (self.l2tp.username,
                            self.l2tp.name,
                            self.l2tp.password),
            PPP_CHAP_SECRET_PATH)

    def generate_vpn_cert_keys(self, country, org):
        """Generate cert and keys for vpn server."""
        rsa = "--type rsa"
        lifetime = "--lifetime 365"
        size = "--size 4096"

        self.ssh.run("ipsec pki --gen %s %s --outform der > caKey.der" %
                     (rsa, size))
        self.ssh.run("ipsec pki --self --ca %s --in caKey.der %s --dn "
                     "\"C=%s, O=%s, CN=%s\" --outform der > caCert.der" %
                     (lifetime, rsa, country, org, self.l2tp.hostname))
        self.ssh.run("ipsec pki --gen %s %s --outform der > serverKey.der" %
                     (size, rsa))
        self.ssh.run("ipsec pki --pub --in serverKey.der %s | ipsec pki "
                     "--issue %s --cacert caCert.der --cakey caKey.der "
                     "--dn \"C=%s, O=%s, CN=%s\" --san %s --flag serverAuth"
                     " --flag ikeIntermediate --outform der > serverCert.der" %
                     (rsa, lifetime, country, org, self.l2tp.hostname, LOCALHOST))
        self.ssh.run("ipsec pki --gen %s %s --outform der > clientKey.der" %
                     (size, rsa))
        self.ssh.run("ipsec pki --pub --in clientKey.der %s | ipsec pki "
                     "--issue %s --cacert caCert.der --cakey caKey.der "
                     "--dn \"C=%s, O=%s, CN=%s@%s\" --outform der > "
                     "clientCert.der" % (rsa, lifetime, country, org,
                                         self.l2tp.username, self.l2tp.hostname))

        self.ssh.run(
            "openssl rsa -inform DER -in clientKey.der"
            " -out clientKey.pem -outform PEM"
        )
        self.ssh.run(
            "openssl x509 -inform DER -in clientCert.der"
            " -out clientCert.pem -outform PEM"
        )
        self.ssh.run(
            "openssl x509 -inform DER -in caCert.der"
            " -out caCert.pem -outform PEM"
        )
        self.ssh.run(
            "openssl pkcs12 -in clientCert.pem -inkey  clientKey.pem"
            " -certfile caCert.pem -export -out clientPkcs.p12 -passout pass:"
        )

        self.ssh.run("mv caCert.pem /etc/ipsec.d/cacerts/")
        self.ssh.run("mv *Cert* /etc/ipsec.d/certs/")
        self.ssh.run("mv *Key* /etc/ipsec.d/private/")
        if not self.path_exists("/www/downloads/"):
            self.ssh.run("mkdir /www/downloads/")
        self.ssh.run("mv clientPkcs.p12 /www/downloads/")
        self.ssh.run("chmod 664 /www/downloads/clientPkcs.p12")

    def generate_ikev2_vpn_cert_keys(self, country, org):
        rsa = "--type rsa"
        lifetime = "--lifetime 365"
        size = "--size 4096"

        if not self.path_exists("/www/downloads/"):
            self.ssh.run("mkdir /www/downloads/")

        ikev2_vpn_cert_keys = [
            "ipsec pki --gen %s %s --outform der > caKey.der" % (rsa, size),
            "ipsec pki --self --ca %s --in caKey.der %s --dn "
            "\"C=%s, O=%s, CN=%s\" --outform der > caCert.der" %
            (lifetime, rsa, country, org, self.l2tp.hostname),
            "ipsec pki --gen %s %s --outform der > serverKey.der" % (size, rsa),
            "ipsec pki --pub --in serverKey.der %s | ipsec pki --issue %s "
            r"--cacert caCert.der --cakey caKey.der --dn \"C=%s, O=%s, CN=%s\" "
            "--san %s --san %s --flag serverAuth --flag ikeIntermediate "
            "--outform der > serverCert.der" % (rsa, lifetime, country, org,
                                                self.l2tp.hostname, LOCALHOST,
                                                self.l2tp.hostname),
            "ipsec pki --gen %s %s --outform der > clientKey.der" % (size, rsa),
            "ipsec pki --pub --in clientKey.der %s | ipsec pki --issue %s "
            r"--cacert caCert.der --cakey caKey.der --dn \"C=%s, O=%s, CN=%s@%s\" "
            r"--san \"%s\" --san \"%s@%s\" --san \"%s@%s\" --outform der "
            "> clientCert.der" % (rsa, lifetime, country, org, self.l2tp.username,
                                  self.l2tp.hostname, self.l2tp.username,
                                  self.l2tp.username, LOCALHOST,
                                  self.l2tp.username, self.l2tp.hostname),
            "openssl rsa -inform DER -in clientKey.der "
            "-out clientKey.pem -outform PEM",
            "openssl x509 -inform DER -in clientCert.der "
            "-out clientCert.pem -outform PEM",
            "openssl x509 -inform DER -in caCert.der "
            "-out caCert.pem -outform PEM",
            "openssl pkcs12 -in clientCert.pem -inkey  clientKey.pem "
            "-certfile caCert.pem -export -out clientPkcs.p12 -passout pass:",
            "mv caCert.pem /etc/ipsec.d/cacerts/",
            "mv *Cert* /etc/ipsec.d/certs/",
            "mv *Key* /etc/ipsec.d/private/",
            "mv clientPkcs.p12 /www/downloads/",
            "chmod 664 /www/downloads/clientPkcs.p12",
        ]
        file_string = "\n".join(ikev2_vpn_cert_keys)
        self.create_config_file(file_string, IKEV2_VPN_CERT_KEYS_PATH)

        self.ssh.run("chmod +x %s" % IKEV2_VPN_CERT_KEYS_PATH)
        self.ssh.run("%s" % IKEV2_VPN_CERT_KEYS_PATH)

    def update_firewall_rules_list(self):
        """Update rule list in /etc/config/firewall."""
        new_rules_list = []
        for i in range(self.count("firewall", "rule")):
            rule = self.ssh.run("uci get firewall.@rule[%s].name" % i).stdout
            new_rules_list.append(rule)
        self.firewall_rules_list = new_rules_list

    def setup_firewall_rules_for_pptp(self):
        """Setup firewall for vpn pptp server."""
        self.update_firewall_rules_list()
        if "pptpd" not in self.firewall_rules_list:
            self.ssh.run("uci add firewall rule")
            self.ssh.run("uci set firewall.@rule[-1].name='pptpd'")
            self.ssh.run("uci set firewall.@rule[-1].target='ACCEPT'")
            self.ssh.run("uci set firewall.@rule[-1].proto='tcp'")
            self.ssh.run("uci set firewall.@rule[-1].dest_port='1723'")
            self.ssh.run("uci set firewall.@rule[-1].family='ipv4'")
            self.ssh.run("uci set firewall.@rule[-1].src='wan'")

        if "GRP" not in self.firewall_rules_list:
            self.ssh.run("uci add firewall rule")
            self.ssh.run("uci set firewall.@rule[-1].name='GRP'")
            self.ssh.run("uci set firewall.@rule[-1].target='ACCEPT'")
            self.ssh.run("uci set firewall.@rule[-1].src='wan'")
            self.ssh.run("uci set firewall.@rule[-1].proto='47'")

        iptable_rules = list(network_const.FIREWALL_RULES_FOR_PPTP)
        self.add_custom_firewall_rules(iptable_rules)
        self.service_manager.need_restart(SERVICE_FIREWALL)

    def restore_firewall_rules_for_pptp(self):
        """Restore firewall for vpn pptp server."""
        self.update_firewall_rules_list()
        if "pptpd" in self.firewall_rules_list:
            self.ssh.run("uci del firewall.@rule[%s]"
                         % self.firewall_rules_list.index("pptpd"))
        self.update_firewall_rules_list()
        if "GRP" in self.firewall_rules_list:
            self.ssh.run("uci del firewall.@rule[%s]"
                         % self.firewall_rules_list.index("GRP"))
        self.remove_custom_firewall_rules()
        self.service_manager.need_restart(SERVICE_FIREWALL)

    def setup_firewall_rules_for_l2tp(self):
        """Setup firewall for vpn l2tp server."""
        self.update_firewall_rules_list()
        if "ipsec esp" not in self.firewall_rules_list:
            self.ssh.run("uci add firewall rule")
            self.ssh.run("uci set firewall.@rule[-1].name='ipsec esp'")
            self.ssh.run("uci set firewall.@rule[-1].target='ACCEPT'")
            self.ssh.run("uci set firewall.@rule[-1].proto='esp'")
            self.ssh.run("uci set firewall.@rule[-1].src='wan'")

        if "ipsec nat-t" not in self.firewall_rules_list:
            self.ssh.run("uci add firewall rule")
            self.ssh.run("uci set firewall.@rule[-1].name='ipsec nat-t'")
            self.ssh.run("uci set firewall.@rule[-1].target='ACCEPT'")
            self.ssh.run("uci set firewall.@rule[-1].src='wan'")
            self.ssh.run("uci set firewall.@rule[-1].proto='udp'")
            self.ssh.run("uci set firewall.@rule[-1].dest_port='4500'")

        if "auth header" not in self.firewall_rules_list:
            self.ssh.run("uci add firewall rule")
            self.ssh.run("uci set firewall.@rule[-1].name='auth header'")
            self.ssh.run("uci set firewall.@rule[-1].target='ACCEPT'")
            self.ssh.run("uci set firewall.@rule[-1].src='wan'")
            self.ssh.run("uci set firewall.@rule[-1].proto='ah'")

        net_id = self.l2tp.address.rsplit(".", 1)[0]
        iptable_rules = list(network_const.FIREWALL_RULES_FOR_L2TP)
        iptable_rules.append("iptables -A FORWARD -s %s.0/24"
                             "  -j ACCEPT" % net_id)
        iptable_rules.append("iptables -t nat -A POSTROUTING"
                             " -s %s.0/24 -o eth0.2 -j MASQUERADE" % net_id)

        self.add_custom_firewall_rules(iptable_rules)
        self.service_manager.need_restart(SERVICE_FIREWALL)

    def restore_firewall_rules_for_l2tp(self):
        """Restore firewall for vpn l2tp server."""
        self.update_firewall_rules_list()
        if "ipsec esp" in self.firewall_rules_list:
            self.ssh.run("uci del firewall.@rule[%s]"
                         % self.firewall_rules_list.index("ipsec esp"))
        self.update_firewall_rules_list()
        if "ipsec nat-t" in self.firewall_rules_list:
            self.ssh.run("uci del firewall.@rule[%s]"
                         % self.firewall_rules_list.index("ipsec nat-t"))
        self.update_firewall_rules_list()
        if "auth header" in self.firewall_rules_list:
            self.ssh.run("uci del firewall.@rule[%s]"
                         % self.firewall_rules_list.index("auth header"))
        self.remove_custom_firewall_rules()
        self.service_manager.need_restart(SERVICE_FIREWALL)

    def add_custom_firewall_rules(self, rules):
        """Backup current custom rules and replace with arguments.

        Args:
            rules: A list of iptable rules to apply.
        """
        backup_file_path = FIREWALL_CUSTOM_OPTION_PATH+".backup"
        if not self.file_exists(backup_file_path):
            self.ssh.run("mv %s %s" % (FIREWALL_CUSTOM_OPTION_PATH,
                                       backup_file_path))
        for rule in rules:
            self.ssh.run("echo %s >> %s" % (rule, FIREWALL_CUSTOM_OPTION_PATH))

    def remove_custom_firewall_rules(self):
        """Clean up and recover custom firewall rules."""
        backup_file_path = FIREWALL_CUSTOM_OPTION_PATH+".backup"
        if self.file_exists(backup_file_path):
            self.ssh.run("mv %s %s" % (backup_file_path,
                                       FIREWALL_CUSTOM_OPTION_PATH))
        else:
            self.log.debug("Did not find %s" % backup_file_path)
            self.ssh.run("echo "" > %s" % FIREWALL_CUSTOM_OPTION_PATH)

    def disable_pptp_service(self):
        """Disable pptp service."""
        self.package_remove(PPTP_PACKAGE)

    def setup_vpn_local_ip(self):
        """Setup VPN Server local ip on OpenWrt for client ping verify."""
        self.ssh.run("uci set network.lan2=interface")
        self.ssh.run("uci set network.lan2.type=bridge")
        self.ssh.run("uci set network.lan2.ifname=eth1.2")
        self.ssh.run("uci set network.lan2.proto=static")
        self.ssh.run("uci set network.lan2.ipaddr=\"%s\"" % self.l2tp.address)
        self.ssh.run("uci set network.lan2.netmask=255.255.255.0")
        self.ssh.run("uci set network.lan2=interface")
        self.service_manager.reload(SERVICE_NETWORK)
        self.commit_changes()

    def remove_vpn_local_ip(self):
        """Discard vpn local ip on OpenWrt."""
        self.ssh.run("uci delete network.lan2")
        self.service_manager.reload(SERVICE_NETWORK)
        self.commit_changes()

    def enable_ipv6(self):
        """Enable ipv6 on OpenWrt."""
        self.ssh.run("uci set network.lan.ipv6=1")
        self.ssh.run("uci set network.wan.ipv6=1")
        self.service_manager.enable("odhcpd")
        self.service_manager.reload(SERVICE_NETWORK)
        self.config.discard("disable_ipv6")
        self.commit_changes()

    def disable_ipv6(self):
        """Disable ipv6 on OpenWrt."""
        self.config.add("disable_ipv6")
        self.ssh.run("uci set network.lan.ipv6=0")
        self.ssh.run("uci set network.wan.ipv6=0")
        self.service_manager.disable("odhcpd")
        self.service_manager.reload(SERVICE_NETWORK)
        self.commit_changes()

    def setup_ipv6_bridge(self):
        """Setup ipv6 bridge for client have ability to access network."""
        self.config.add("setup_ipv6_bridge")

        self.ssh.run("uci set dhcp.lan.dhcpv6=relay")
        self.ssh.run("uci set dhcp.lan.ra=relay")
        self.ssh.run("uci set dhcp.lan.ndp=relay")

        self.ssh.run("uci set dhcp.wan6=dhcp")
        self.ssh.run("uci set dhcp.wan6.dhcpv6=relay")
        self.ssh.run("uci set dhcp.wan6.ra=relay")
        self.ssh.run("uci set dhcp.wan6.ndp=relay")
        self.ssh.run("uci set dhcp.wan6.master=1")
        self.ssh.run("uci set dhcp.wan6.interface=wan6")

        # Enable service
        self.service_manager.need_restart(SERVICE_ODHCPD)
        self.commit_changes()

    def remove_ipv6_bridge(self):
        """Discard ipv6 bridge on OpenWrt."""
        if "setup_ipv6_bridge" in self.config:
            self.config.discard("setup_ipv6_bridge")

            self.ssh.run("uci set dhcp.lan.dhcpv6=server")
            self.ssh.run("uci set dhcp.lan.ra=server")
            self.ssh.run("uci delete dhcp.lan.ndp")

            self.ssh.run("uci delete dhcp.wan6")

            self.service_manager.need_restart(SERVICE_ODHCPD)
            self.commit_changes()

    def _add_dhcp_option(self, args):
        self.ssh.run("uci add_list dhcp.lan.dhcp_option=\"%s\"" % args)

    def _remove_dhcp_option(self, args):
        self.ssh.run("uci del_list dhcp.lan.dhcp_option=\"%s\"" % args)

    def add_default_dns(self, addr_list):
        """Add default dns server for client.

        Args:
            addr_list: dns ip address for Openwrt client.
        """
        self._add_dhcp_option("6,%s" % ",".join(addr_list))
        self.config.add("default_dns %s" % addr_list)
        self.service_manager.need_restart(SERVICE_DNSMASQ)
        self.commit_changes()

    def del_default_dns(self, addr_list):
        """Remove default dns server for client.

        Args:
            addr_list: list of dns ip address for Openwrt client.
        """
        self._remove_dhcp_option("6,%s" % addr_list)
        self.config.discard("default_dns %s" % addr_list)
        self.service_manager.need_restart(SERVICE_DNSMASQ)
        self.commit_changes()

    def add_default_v6_dns(self, addr_list):
        """Add default v6 dns server for client.

        Args:
            addr_list: dns ip address for Openwrt client.
        """
        self.ssh.run("uci add_list dhcp.lan.dns=\"%s\"" % addr_list)
        self.config.add("default_v6_dns %s" % addr_list)
        self.service_manager.need_restart(SERVICE_ODHCPD)
        self.commit_changes()

    def del_default_v6_dns(self, addr_list):
        """Del default v6 dns server for client.

        Args:
            addr_list: dns ip address for Openwrt client.
        """
        self.ssh.run("uci del_list dhcp.lan.dns=\"%s\"" % addr_list)
        self.config.add("default_v6_dns %s" % addr_list)
        self.service_manager.need_restart(SERVICE_ODHCPD)
        self.commit_changes()

    def add_ipv6_prefer_option(self):
        self._add_dhcp_option("108,1800i")
        self.config.add("ipv6_prefer_option")
        self.service_manager.need_restart(SERVICE_DNSMASQ)
        self.commit_changes()

    def remove_ipv6_prefer_option(self):
        self._remove_dhcp_option("108,1800i")
        self.config.discard("ipv6_prefer_option")
        self.service_manager.need_restart(SERVICE_DNSMASQ)
        self.commit_changes()

    def add_dhcp_rapid_commit(self):
        self.create_config_file("dhcp-rapid-commit\n","/etc/dnsmasq.conf")
        self.config.add("add_dhcp_rapid_commit")
        self.service_manager.need_restart(SERVICE_DNSMASQ)
        self.commit_changes()

    def remove_dhcp_rapid_commit(self):
        self.create_config_file("","/etc/dnsmasq.conf")
        self.config.discard("add_dhcp_rapid_commit")
        self.service_manager.need_restart(SERVICE_DNSMASQ)
        self.commit_changes()

    def start_tcpdump(self, test_name, args="", interface="br-lan"):
        """"Start tcpdump on OpenWrt.

        Args:
            test_name: Test name for create tcpdump file name.
            args: Option args for tcpdump.
            interface: Interface to logging.
        Returns:
            tcpdump_file_name: tcpdump file name on OpenWrt.
            pid: tcpdump process id.
        """
        self.package_install("tcpdump")
        if not self.path_exists(TCPDUMP_DIR):
            self.ssh.run("mkdir %s" % TCPDUMP_DIR)
        tcpdump_file_name = "openwrt_%s_%s.pcap" % (test_name,
                                                    time.strftime("%Y-%m-%d_%H-%M-%S", time.localtime(time.time())))
        tcpdump_file_path = "".join([TCPDUMP_DIR, tcpdump_file_name])
        cmd = "tcpdump -i %s -s0 %s -w %s" % (interface, args, tcpdump_file_path)
        self.ssh.run_async(cmd)
        pid = self._get_tcpdump_pid(tcpdump_file_name)
        if not pid:
            raise signals.TestFailure("Fail to start tcpdump on OpenWrt.")
        # Set delay to prevent tcpdump fail to capture target packet.
        time.sleep(15)
        return tcpdump_file_name

    def stop_tcpdump(self, tcpdump_file_name, pull_dir=None):
        """Stop tcpdump on OpenWrt and pull the pcap file.

        Args:
            tcpdump_file_name: tcpdump file name on OpenWrt.
            pull_dir: Keep none if no need to pull.
        Returns:
            tcpdump abs_path on host.
        """
        # Set delay to prevent tcpdump fail to capture target packet.
        time.sleep(15)
        pid = self._get_tcpdump_pid(tcpdump_file_name)
        self.ssh.run("kill -9 %s" % pid, ignore_status=True)
        if self.path_exists(TCPDUMP_DIR) and pull_dir:
            tcpdump_path = "".join([TCPDUMP_DIR, tcpdump_file_name])
            tcpdump_remote_path = "/".join([pull_dir, tcpdump_file_name])
            tcpdump_local_path = "%s@%s:%s" % (self.user, self.ip, tcpdump_path)
            utils.exe_cmd("scp %s %s" % (tcpdump_local_path, tcpdump_remote_path))

        if self._get_tcpdump_pid(tcpdump_file_name):
            raise signals.TestFailure("Failed to stop tcpdump on OpenWrt.")
        if self.file_exists(tcpdump_path):
            self.ssh.run("rm -f %s" % tcpdump_path)
        return tcpdump_remote_path if pull_dir else None

    def clear_tcpdump(self):
        self.ssh.run("killall tcpdump", ignore_status=True)
        if self.ssh.run("pgrep tcpdump", ignore_status=True).stdout:
            raise signals.TestFailure("Failed to clean up tcpdump process.")
        if self.path_exists(TCPDUMP_DIR):
            self.ssh.run("rm -f  %s/*" % TCPDUMP_DIR)

    def _get_tcpdump_pid(self, tcpdump_file_name):
        """Check tcpdump process on OpenWrt."""
        return self.ssh.run("pgrep -f %s" % (tcpdump_file_name), ignore_status=True).stdout

    def setup_mdns(self):
        self.config.add("setup_mdns")
        self.package_install(MDNS_PACKAGE)
        self.commit_changes()

    def remove_mdns(self):
        self.config.discard("setup_mdns")
        self.package_remove(MDNS_PACKAGE)
        self.commit_changes()

    def block_dns_response(self):
        self.config.add("block_dns_response")
        iptable_rules = list(network_const.FIREWALL_RULES_DISABLE_DNS_RESPONSE)
        self.add_custom_firewall_rules(iptable_rules)
        self.service_manager.need_restart(SERVICE_FIREWALL)
        self.commit_changes()

    def unblock_dns_response(self):
        self.config.discard("block_dns_response")
        self.remove_custom_firewall_rules()
        self.service_manager.need_restart(SERVICE_FIREWALL)
        self.commit_changes()

    def setup_captive_portal(self, fas_fdqn,fas_port=2080):
        """Create captive portal with Forwarding Authentication Service.

        Args:
             fas_fdqn: String for captive portal page's fdqn add to local dns server.
             fas_port: Port for captive portal page.
        """
        self.package_install(CAPTIVE_PORTAL_PACKAGE)
        self.config.add("setup_captive_portal %s" % fas_port)
        self.ssh.run("uci set opennds.@opennds[0].fas_secure_enabled=2")
        self.ssh.run("uci set opennds.@opennds[0].gatewayport=2050")
        self.ssh.run("uci set opennds.@opennds[0].fasport=%s" % fas_port)
        self.ssh.run("uci set opennds.@opennds[0].fasremotefqdn=%s" % fas_fdqn)
        self.ssh.run("uci set opennds.@opennds[0].faspath=\"/nds/fas-aes.php\"")
        self.ssh.run("uci set opennds.@opennds[0].faskey=1234567890")
        self.service_manager.need_restart(SERVICE_OPENNDS)
        # Config uhttpd
        self.ssh.run("uci set uhttpd.main.interpreter=.php=/usr/bin/php-cgi")
        self.ssh.run("uci add_list uhttpd.main.listen_http=0.0.0.0:%s" % fas_port)
        self.ssh.run("uci add_list uhttpd.main.listen_http=[::]:%s" % fas_port)
        self.service_manager.need_restart(SERVICE_UHTTPD)
        # cp fas-aes.php
        self.create_folder("/www/nds/")
        self.ssh.run("cp /etc/opennds/fas-aes.php /www/nds")
        # Add fdqn
        self.add_resource_record(fas_fdqn, LOCALHOST)
        self.commit_changes()

    def remove_cpative_portal(self, fas_port=2080):
        """Remove captive portal.

        Args:
             fas_port: Port for captive portal page.
        """
        # Remove package
        self.package_remove(CAPTIVE_PORTAL_PACKAGE)
        # Clean up config
        self.ssh.run("rm /etc/config/opennds")
        # Remove fdqn
        self.clear_resource_record()
        # Restore uhttpd
        self.ssh.run("uci del uhttpd.main.interpreter")
        self.ssh.run("uci del_list uhttpd.main.listen_http=\'0.0.0.0:%s\'" % fas_port)
        self.ssh.run("uci del_list uhttpd.main.listen_http=\'[::]:%s\'" % fas_port)
        self.service_manager.need_restart(SERVICE_UHTTPD)
        # Clean web root
        self.ssh.run("rm -r /www/nds")
        self.config.discard("setup_captive_portal %s" % fas_port)
        self.commit_changes()


class ServiceManager(object):
    """Class for service on OpenWrt.

        Attributes:
        ssh: ssh object for the AP.
        _need_restart: Record service need to restart.
    """

    def __init__(self, ssh):
        self.ssh = ssh
        self._need_restart = set()

    def enable(self, service_name):
        """Enable service auto start."""
        self.ssh.run("/etc/init.d/%s enable" % service_name)

    def disable(self, service_name):
        """Disable service auto start."""
        self.ssh.run("/etc/init.d/%s disable" % service_name)

    def restart(self, service_name):
        """Restart the service."""
        self.ssh.run("/etc/init.d/%s restart" % service_name)

    def reload(self, service_name):
        """Restart the service."""
        self.ssh.run("/etc/init.d/%s reload" % service_name)

    def restart_services(self):
        """Restart all services need to restart."""
        for service in self._need_restart:
            if service == SERVICE_NETWORK:
                self.reload(service)
            self.restart(service)
        self._need_restart = set()

    def stop(self, service_name):
        """Stop the service."""
        self.ssh.run("/etc/init.d/%s stop" % service_name)

    def need_restart(self, service_name):
        self._need_restart.add(service_name)
