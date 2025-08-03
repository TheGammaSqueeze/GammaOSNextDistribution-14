#!/bin/bash
# 4 mesh nodes in a diamond topology
# paths must go through one of two intermediate nodes.

num_nodes=4
session=wmediumd
subnet=10.10.10
macfmt='02:00:00:00:%02x:00'

. func

if [[ $UID -ne 0 ]]; then
	echo "Sorry, run me as root."
	exit 1
fi

if [[ $# -eq 0 ]]; then
	freq=2412
else
	freq=$1
fi

modprobe -r mac80211_hwsim
modprobe mac80211_hwsim radios=$num_nodes
iw reg set US

for i in `seq 0 $((num_nodes-1))`; do
	addrs[$i]=`printf $macfmt $i`
done

cat <<__EOM > diamond.cfg
ifaces :
{
	ids = [
		"02:00:00:00:00:00",
		"02:00:00:00:01:00",
		"02:00:00:00:02:00",
		"02:00:00:00:03:00"
	];

	links = (
		(0, 1, 10),
		(0, 2, 20),
		(0, 3, 0),
		(1, 2, 30),
		(1, 3, 10),
		(2, 3, 20)
	);
};
__EOM

tmux new -s $session -d
# find out the index of the first window as we can't assume zero-indexing
first_idx=`tmux list-windows -t $session | head -n1 | cut -d: -f1`

rm /tmp/netns.pid.* 2>/dev/null
i=0
for addr in ${addrs[@]}; do
	phy=`addr2phy $addr`
	dev=`ls /sys/class/ieee80211/$phy/device/net`
	phys[$i]=$phy
	devs[$i]=$dev

	ip=${subnet}.$((10 + i))

	# put this phy in own netns and tmux window, and start a mesh node
	tmux new-window -t $session

	# start netns
	pidfile=/tmp/netns.pid.$i
	win=$session:$((first_idx+i+1))
	tmux send-keys -t $win 'lxc-unshare -s NETWORK /bin/bash' C-m
	tmux send-keys -t $win 'echo $$ > '$pidfile C-m

	# wait for netns to exist
	while [[ ! -e $pidfile ]]; do
		echo "Waiting for netns $i -- $pidfile"
		sleep 0.5
	done

	tmux send-keys -t $session:$first_idx \
        'iw phy '$phy' set netns `cat '$pidfile'`' C-m

	# wait for phy to exist in netns
	while [[ -e /sys/class/ieee80211/$phy ]]; do
		echo "Waiting for $phy to move to netns..."
		sleep 0.5
	done

	# start mesh node
	tmux send-keys -t $win '. func' C-m
	tmux send-keys -t $win 'meshup-iw '$dev' diamond '$freq' '$ip C-m

	i=$((i+1))
done

# start wmediumd
tmux send-keys -t $session:$first_idx '../wmediumd/wmediumd -c diamond.cfg' C-m

# start iperf server on 10.10.10.13
node_idx=$((first_idx + 4))
tmux send-keys -t $session:$node_idx 'iperf -s' C-m

# enable monitor
tmux new-window -t $session
cap_idx=$((first_idx + 5))
tmux send-keys -t $session:$cap_idx 'ip link set hwsim0 up' C-m
# capture traffic as normal user (if possible) or root 
CAP_USER=${SUDO_USER:-root}
tmux send-keys -t $session:$cap_idx "sudo -u $CAP_USER dumpcap -i hwsim0" C-m

node_idx=$((first_idx + 1))
tmux select-window -t $session:$node_idx
tmux send-keys -t $session:$node_idx 'ping -c 5 10.10.10.13' C-m
tmux send-keys -t $session:$node_idx 'iperf -c 10.10.10.13 -i 5 -t 120'

tmux attach
