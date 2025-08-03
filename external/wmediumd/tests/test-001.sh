#!/bin/bash

SUBNET=10.10.10
NUM_PHYS=2

if [[ $UID -ne 0 ]]; then
	echo "Sorry, run me as root."
	exit 1
fi

function cleanup() {
	echo "Cleaning up!"
	# restore default routing rules
	echo 0 > /proc/sys/net/ipv4/conf/all/arp_ignore
	for i in `seq 0 $NUM_PHYS`; do
		prio=$((i+10))
		prio2=$((256+prio))
		tbl=$prio2

		ip rule del priority $prio2 2> /dev/null
		ip rule del priority $prio 2> /dev/null
		ip route flush table $tbl 2> /dev/null
	done
	ip rule del priority 1000
	ip rule add priority 0 table local

	# kill whatever we started
	killall wmediumd
}

trap 'cleanup' INT TERM EXIT

modprobe -r mac80211_hwsim
modprobe mac80211_hwsim radios=$NUM_PHYS

# routing-based send-to-self (Patrick McHardy)
# lower priority of kernel local table
ip rule add priority 1000 lookup local
ip rule del priority 0 &>/dev/null

# only arp reply for self
echo 1 > /proc/sys/net/ipv4/conf/all/arp_ignore

i=0
# Assume most recently modified phys are hwsim phys (hence the ls -t)
for phy in `ls -t /sys/class/ieee80211 | head -$NUM_PHYS`; do
	# The usual stuff
	dev=`ls /sys/class/ieee80211/$phy/device/net`
	ip=${SUBNET}.$((10 + i))

	ip link set $dev down
	ip link set address 42:00:00:00:0${i}:00 dev $dev
	iw dev $dev set type mesh
	iw dev $dev set channel 36
	ip link set $dev up
	iw dev $dev mesh join meshtest

	ip addr flush dev $dev
	ip addr add $ip/24 dev $dev

	# set up local delivery
	prio=$((i+10))
	prio2=$((256+prio))
	tbl=$prio2

	# incoming traffic to us delivered via local table
	echo 1 > /proc/sys/net/ipv4/conf/$dev/accept_local
	ip rule del priority $prio 2> /dev/null
	ip rule add priority $prio iif $dev lookup local

	# outgoing frames with our ip will be generated on our interface
	# and go over the wire.
	ip rule del priority $prio2 2> /dev/null
	ip rule add priority $prio2 from $ip table $tbl
	ip route flush table $tbl 2> /dev/null
	ip route add default dev $dev table $tbl

	i=$((i+1))
done

# enable wmediumd
../wmediumd/wmediumd -c 2node.cfg > wmediumd.log &

# see if we can establish a mesh path
ping -i 1 -c 5 -I ${SUBNET}.10 ${SUBNET}.11 || { echo FAIL; exit 1; }

echo PASS
