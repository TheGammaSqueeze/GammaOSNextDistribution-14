LOCALHOST = "192.168.1.1"

# params for ipsec.conf
IPSEC_CONF = {
    "config setup": {
        "charondebug": "chd 2,ike 2,knl 2,net 2,esp 2,dmn 2,"
                       "mgr 2,lib 1,cfg 2,enc 1".__repr__(),
        "uniqueids": "never"
    },
    "conn %default": {
        "ike": "aes128-sha-modp1024",
        "esp": "aes128-sha1"
    }
}

IPSEC_L2TP_PSK = {
    "conn L2TP_PSK": {
        "keyexchange": "ikev1",
        "type": "transport",
        "left": LOCALHOST,
        "leftprotoport": "17/1701",
        "leftauth": "psk",
        "right": "%any",
        "rightprotoport": "17/%any",
        "rightsubnet": "0.0.0.0/0",
        "rightauth": "psk",
        "auto": "add"
    }
}

IPSEC_L2TP_RSA = {
    "conn L2TP_RSA": {
        "keyexchange": "ikev1",
        "type": "transport",
        "left": LOCALHOST,
        "leftprotoport": "17/1701",
        "leftauth": "pubkey",
        "leftcert": "serverCert.der",
        "right": "%any",
        "rightprotoport": "17/%any",
        "rightsubnet": "0.0.0.0/0",
        "rightauth": "pubkey",
        "auto": "add"
    }
}

IPSEC_HYBRID_RSA = {
    "conn HYBRID_RSA": {
        "keyexchange": "ikev1",
        "left": LOCALHOST,
        "leftsubnet": "0.0.0.0/0",
        "leftauth": "pubkey",
        "leftcert": "serverCert.der",
        "leftsendcert": "always",
        "right": "%any",
        "rightsubnet": "0.0.0.0/0",
        "rightauth": "pubkey",
        "rightauth2": "xauth",
        "xauth": "server",
        "auto": "add",
    }
}

IPSEC_XAUTH_PSK = {
    "conn XAUTH_PSK": {
        "keyexchange": "ikev1",
        "left": LOCALHOST,
        "leftsubnet": "0.0.0.0/0",
        "leftauth": "psk",
        "right": "%any",
        "rightsubnet": "0.0.0.0/0",
        "rightauth": "psk",
        "rightauth2": "xauth",
        "auto": "add",
    }
}

IPSEC_XAUTH_RSA = {
    "conn XAUTH_RSA": {
        "keyexchange": "ikev1",
        "left": LOCALHOST,
        "leftsubnet": "0.0.0.0/0",
        "leftcert": "serverCert.der",
        "leftsendcert": "always",
        "right": "%any",
        "rightsubnet": "0.0.0.0/0",
        "rightauth": "xauth",
        "xauth": "server",
        "auto": "add",
    }
}

IPSEC_IKEV2_MSCHAPV2 = {
    "conn IKEV2_MSCHAPV2": {
        "keyexchange": "ikev2",
        "left": LOCALHOST,
        "leftid": LOCALHOST,
        "leftcert": "serverCert.der",
        "leftsubnet": "0.0.0.0/0",
        "leftauth": "pubkey",
        "leftsendcert": "always",
        "right": "%any",
        "rightid": "vpntest",
        "rightauth": "eap-mschapv2",
        "auto": "add"
    }
}

IPSEC_IKEV2_PSK = {
    "conn IKEV2_PSK": {
        "keyexchange": "ikev2",
        "left": LOCALHOST,
        "leftid": LOCALHOST,
        "leftauth": "psk",
        "leftsubnet": "0.0.0.0/0",
        "right": "%any",
        "rightid": "vpntest",
        "rightauth": "psk",
        "auto": "add"
    }
}

IPSEC_IKEV2_RSA = {
    "conn IKEV2_RSA": {
        "keyexchange": "ikev2",
        "left": LOCALHOST,
        "leftid": LOCALHOST,
        "leftcert": "serverCert.der",
        "leftsubnet": "0.0.0.0/0",
        "leftauth": "pubkey",
        "leftsendcert": "always",
        "right": "%any",
        "rightid": "vpntest@%s" % LOCALHOST,
        "rightauth": "pubkey",
        "rightcert": "clientCert.pem",
        "auto": "add"
    }
}

IPSEC_IKEV2_MSCHAPV2_HOSTNAME = {
    "conn IKEV2_MSCHAPV2_HOSTNAME": {
        "keyexchange": "ikev2",
        "left": LOCALHOST,
        "leftid": "strongswan-vpn-server.android-iperf.com",
        "leftcert": "serverCert.der",
        "leftsubnet": "0.0.0.0/0",
        "leftauth": "pubkey",
        "leftsendcert": "always",
        "right": "%any",
        "rightid": "vpntest",
        "rightauth": "eap-mschapv2",
        "auto": "add"
    }
}

IPSEC_IKEV2_PSK_HOSTNAME = {
    "conn IKEV2_PSK_HOSTNAME": {
        "keyexchange": "ikev2",
        "left": LOCALHOST,
        "leftid": "strongswan-vpn-server.android-iperf.com",
        "leftauth": "psk",
        "leftsubnet": "0.0.0.0/0",
        "right": "%any",
        "rightid": "vpntest",
        "rightauth": "psk",
        "auto": "add"
    }
}

IPSEC_IKEV2_RSA_HOSTNAME = {
    "conn IKEV2_RSA_HOSTNAME": {
        "keyexchange": "ikev2",
        "left": LOCALHOST,
        "leftid": "strongswan-vpn-server.android-iperf.com",
        "leftcert": "serverCert.der",
        "leftsubnet": "0.0.0.0/0",
        "leftauth": "pubkey",
        "leftsendcert": "always",
        "right": "%any",
        "rightid": "vpntest@strongswan-vpn-server.android-iperf.com",
        "rightauth": "pubkey",
        "rightcert": "clientCert.pem",
        "auto": "add"
    }
}

# parmas for lx2tpd

XL2TPD_CONF_GLOBAL = (
    "[global]",
    "ipsec saref = no",
    "debug tunnel = no",
    "debug avp = no",
    "debug network = no",
    "debug state = no",
    "access control = no",
    "rand source = dev",
    "port = 1701",
)

XL2TPD_CONF_INS = (
    "[lns default]",
    "require authentication = yes",
    "pass peer = yes",
    "ppp debug = no",
    "length bit = yes",
    "refuse pap = yes",
    "refuse chap = yes",
)

XL2TPD_OPTION = (
    "require-mschap-v2",
    "refuse-mschap",
    "ms-dns 8.8.8.8",
    "ms-dns 8.8.4.4",
    "asyncmap 0",
    "auth",
    "crtscts",
    "idle 1800",
    "mtu 1410",
    "mru 1410",
    "connect-delay 5000",
    "lock",
    "hide-password",
    "local",
    "debug",
    "modem",
    "proxyarp",
    "lcp-echo-interval 30",
    "lcp-echo-failure 4",
    "nomppe"
)

# iptable rules for vpn_pptp
FIREWALL_RULES_FOR_PPTP = (
    "iptables -A input_rule -i ppp+ -j ACCEPT",
    "iptables -A output_rule -o ppp+ -j ACCEPT",
    "iptables -A forwarding_rule -i ppp+ -j ACCEPT"
)

# iptable rules for vpn_l2tp
FIREWALL_RULES_FOR_L2TP = (
    "iptables -I INPUT  -m policy --dir in --pol ipsec --proto esp -j ACCEPT",
    "iptables -I FORWARD  -m policy --dir in --pol ipsec --proto esp -j ACCEPT",
    "iptables -I FORWARD  -m policy --dir out --pol ipsec --proto esp -j ACCEPT",
    "iptables -I OUTPUT   -m policy --dir out --pol ipsec --proto esp -j ACCEPT",
    "iptables -t nat -I POSTROUTING -m policy --pol ipsec --dir out -j ACCEPT",
    "iptables -A INPUT -p esp -j ACCEPT",
    "iptables -A INPUT -i eth0.2 -p udp --dport 500 -j ACCEPT",
    "iptables -A INPUT -i eth0.2 -p tcp --dport 500 -j ACCEPT",
    "iptables -A INPUT -i eth0.2 -p udp --dport 4500 -j ACCEPT",
    "iptables -A INPUT -p udp --dport 500 -j ACCEPT",
    "iptables -A INPUT -p udp --dport 4500 -j ACCEPT",
    "iptables -A INPUT -p udp -m policy --dir in --pol ipsec -m udp --dport 1701 -j ACCEPT"
)

FIREWALL_RULES_DISABLE_DNS_RESPONSE = (
    "iptables -I OUTPUT -p udp --sport 53 -j DROP",
    "iptables -I OUTPUT -p tcp --sport 53 -j DROP",
    "ip6tables -I OUTPUT -p udp --sport 53 -j DROP",
    "ip6tables -I OUTPUT -p tcp --sport 53 -j DROP",
)


# Object for vpn profile
class VpnL2tp(object):
    """Profile for vpn l2tp type.

    Attributes:
        hostname: vpn server domain name
        address: vpn server address
        username: vpn user account
        password: vpn user password
        psk_secret: psk for ipsec
        name: vpn server name for register in OpenWrt
    """

    def __init__(self,
                 vpn_server_hostname,
                 vpn_server_address,
                 vpn_username,
                 vpn_password,
                 psk_secret,
                 server_name):
        self.name = server_name
        self.hostname = vpn_server_hostname
        self.address = vpn_server_address
        self.username = vpn_username
        self.password = vpn_password
        self.psk_secret = psk_secret
