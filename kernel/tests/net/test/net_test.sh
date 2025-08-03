#!/bin/bash
if [[ -n "${verbose}" ]]; then
  echo 'Current working directory:'
  echo " - according to builtin:  [$(pwd)]"
  echo " - according to /bin/pwd: [$(/bin/pwd)]"
  echo

  echo 'Shell environment:'
  env
  echo

  echo -n "net_test.sh (pid $$, parent ${PPID}, tty $(tty)) running [$0] with args:"
  for arg in "$@"; do
    echo -n " [${arg}]"
  done
  echo
  echo
fi

if [[ "$(tty)" == 'not a tty' ]]; then
  echo 'not a tty? perhaps not quite real kernel default /dev/console - trying to fix.'
  if [[ -c /dev/console ]]; then
    [[ "$(readlink /proc/$$/fd/0)" != '/dev/console' ]] || exec < /dev/console
    [[ "$(readlink /proc/$$/fd/1)" != '/dev/console' ]] || exec > /dev/console
    [[ "$(readlink /proc/$$/fd/2)" != '/dev/console' ]] || exec 2> /dev/console
  fi
fi

if [[ "$(tty)" == '/dev/console' ]]; then
  ARCH="$(uname -m)"
  # Underscore is illegal in hostname, replace with hyphen
  ARCH="${ARCH//_/-}"

  # setsid + /dev/tty{,AMA,S}0 allows bash's job control to work, ie. Ctrl+C/Z
  if [[ -e '/proc/exitcode' ]]; then
    # exists only in UML
    CON='/dev/tty0'
    hostname "uml-${ARCH}"
  elif [[ -c '/dev/ttyAMA0' ]]; then
    # Qemu for arm (note: /dev/ttyS0 also exists for exitcode)
    CON='/dev/ttyAMA0'
    hostname "qemu-${ARCH}"
  elif [[ -c '/dev/ttyS0' ]]; then
    # Qemu for x86 (note: /dev/ttyS1 also exists for exitcode)
    CON='/dev/ttyS0'
    hostname "qemu-${ARCH}"
  else
    # Can't figure it out, job control won't work, tough luck
    echo 'Unable to figure out proper console - job control will not work.' >&2
    CON=''
    hostname "local-${ARCH}"
  fi

  unset ARCH

  echo -n "$(hostname): Currently tty[/dev/console], but it should be [${CON}]..."

  if [[ -n "${CON}" ]]; then
    # Redirect std{in,out,err} to the console equivalent tty
    # which actually supports all standard tty ioctls
    exec <"${CON}" >&"${CON}"

    # Bash wants to be session leader, hence need for setsid
    echo " re-executing..."
    exec /usr/bin/setsid "$0" "$@"
    # If the above exec fails, we just fall through...
    # (this implies failure to *find* setsid, not error return from bash,
    #  in practice due to image construction this cannot happen)
  else
    echo
  fi

  # In case we fall through, clean up
  unset CON
fi

if [[ -n "${verbose}" ]]; then
  echo 'TTY settings:'
  stty
  echo

  echo 'TTY settings (verbose):'
  stty -a
  echo

  echo 'Restoring TTY sanity...'
fi

stty sane
stty 115200
[[ -z "${console_cols}" ]] || stty columns "${console_cols}"
[[ -z "${console_rows}" ]] || stty rows    "${console_rows}"

if [[ -n "${verbose}" ]]; then
  echo

  echo 'TTY settings:'
  stty
  echo

  echo 'TTY settings (verbose):'
  stty -a
  echo
fi

# By the time we get here we should have a sane console:
#  - 115200 baud rate
#  - appropriate (and known) width and height (note: this assumes
#    that the terminal doesn't get further resized)
#  - it is no longer /dev/console, so job control should function
#    (this means working ctrl+c [abort] and ctrl+z [suspend])


# This defaults to 60 which is needlessly long during boot
# (we will reset it back to the default later)
echo 0 > /proc/sys/kernel/random/urandom_min_reseed_secs

if [[ -n "${entropy}" ]]; then
  echo "adding entropy from hex string [${entropy}]" >&2

  # In kernel/include/uapi/linux/random.h RNDADDENTROPY is defined as
  # _IOW('R', 0x03, int[2]) =(R is 0x52)= 0x40085203 = 1074287107
  /usr/bin/python3 3>/dev/random <<EOF
import base64, fcntl, struct
rnd = base64.b64decode('${entropy}')
fcntl.ioctl(3, 0x40085203, struct.pack('ii', len(rnd) * 8, len(rnd)) + rnd)
EOF

fi

# Make sure the urandom pool has a chance to initialize before we reset
# the reseed timer back to 60 seconds.  One timer tick should be enough.
sleep 1.1

# By this point either 'random: crng init done' (newer kernels)
# or 'random: nonblocking pool is initialized' (older kernels)
# should have been printed out to dmesg/console.

# Reset it back to boot time default
echo 60 > /proc/sys/kernel/random/urandom_min_reseed_secs

# Make sure /sys is mounted
[[ -d /sys/fs ]] || mount -t sysfs sysfs -o nosuid,nodev,noexec /sys

if ! [[ "$(uname -r)" =~ ^([0-3]|4[.][0-8])[.] ]]; then
  # Mount the bpf filesystem on Linux version 4.9+
  mount -t bpf bpf -o nosuid,nodev,noexec /sys/fs/bpf
fi

if ! [[ "$(uname -r)" =~ ^([0-3]|4[.][0-9]|4[.]1[0-3])[.] ]]; then
  # Mount the Cgroup v2 filesystem on Linux version 4.14+
  mount -t cgroup2 cgroup2 -o nosuid,nodev,noexec /sys/fs/cgroup
fi

# In case IPv6 is compiled as a module.
[ -f /proc/net/if_inet6 ] || insmod $DIR/kernel/net-next/net/ipv6/ipv6.ko

# Minimal network setup.
ip link set lo up
ip link set lo mtu 16436
if [[ -d /sys/class/net/eth0 ]]; then
  ip link set eth0 up
fi

# Allow people to run ping.
echo '0 2147483647' > /proc/sys/net/ipv4/ping_group_range

# Adjust tcp_rmem_default on UML as needed by Linux 6.6
if [[ -e /proc/exitcode ]]; then
  # UML with mem=512M defaults to '4096 131072 ~4021664'
  read tcp_rmem_min tcp_rmem_default tcp_rmem_max < /proc/sys/net/ipv4/tcp_rmem
  if [[ tcp_rmem_default -lt 262144 ]]; then
    echo "${tcp_rmem_min} 262144 ${tcp_rmem_max}" > /proc/sys/net/ipv4/tcp_rmem
  fi
fi

# Allow unprivileged use of eBPF (matches Android OS)
if [[ "$(< /proc/sys/kernel/unprivileged_bpf_disabled)" != '0' ]]; then
  echo 0 > /proc/sys/kernel/unprivileged_bpf_disabled
fi

# Read environment variables passed to the kernel to determine if script is
# running on builder and to find which test to run.

if [ "$net_test_mode" != "builder" ]; then
  # Fall out to a shell once the test completes or if there's an error.
  trap "exec /bin/bash" ERR EXIT
fi

echo -e "Running $net_test $net_test_args\n"
$net_test $net_test_args
rv="$?"

# Write exit code of net_test to a file so that the builder can use it
# to signal failure if any tests fail.
echo "${rv}" > "${exitcode}"

# Additionally on UML make it the exit code of UML kernel binary itself.
if [[ -e '/proc/exitcode' ]]; then
  echo "${rv}" > /proc/exitcode
fi
