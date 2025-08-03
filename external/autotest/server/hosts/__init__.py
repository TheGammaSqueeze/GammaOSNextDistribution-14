#
# Copyright 2007 Google Inc. Released under the GPL v2

"""This is a convenience module to import all available types of hosts.

Implementation details:
You should 'import hosts' instead of importing every available host module.
"""

try:
    # host abstract classes
    from autotest_lib.server.hosts.base_classes import Host
    from autotest_lib.server.hosts.remote import RemoteHost

    # host implementation classes
    from autotest_lib.server.hosts.ssh_host import SSHHost
    from autotest_lib.server.hosts.cros_host import CrosHost
    from autotest_lib.server.hosts.chameleon_host import ChameleonHost
    from autotest_lib.server.hosts.servo_host import ServoHost
    from autotest_lib.server.hosts.labstation_host import LabstationHost
    from autotest_lib.server.hosts.android_host import AndroidHost

    # factory function
    from autotest_lib.server.hosts.factory import create_host
    from autotest_lib.server.hosts.factory import create_target_machine
    from autotest_lib.server.hosts.factory import create_companion_hosts

except ImportError:
    # host abstract classes
    from base_classes import Host
    from remote import RemoteHost

    # host implementation classes
    from ssh_host import SSHHost
    from cros_host import CrosHost
    from chameleon_host import ChameleonHost
    from servo_host import ServoHost
    from labstation_host import LabstationHost
    from android_host import AndroidHost

    # factory function
    from factory import create_host
    from factory import create_target_machine
    from factory import create_companion_hosts
