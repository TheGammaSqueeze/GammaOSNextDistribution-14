# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# TODO(ihf): Find a good aapt and update to it.
SDK_TOOLS_DIR = 'gs://chromeos-arc-images/builds/git_nyc-mr1-arc-linux-static_sdk_tools/3544738'
SDK_TOOLS_FILES = ['aapt']

# Use old version of adb for a speculative workaround for b/183438202
ADB_DIR_OLD = 'gs://chromeos-arc-images/builds/git_qt-release-static_sdk_tools/6118618'
# adb 31.0.0 from https://developer.android.com/studio/releases/platform-tools
ADB_DIR = 'gs://chromeos-arc-images/builds/aosp-sdk-release/7110759/'
ADB_FILES = ['adb']

ADB_POLLING_INTERVAL_SECONDS = 1
ADB_CONNECT_TIMEOUT_SECONDS = 10
ADB_KILL_SERVER_TIMEOUT_SECONDS = 10
ADB_READY_TIMEOUT_SECONDS = 30
ADB_PUSH_MEDIASTRESS_TIMEOUT_SECONDS = 600

ARC_POLLING_INTERVAL_SECONDS = 1
ARC_READY_TIMEOUT_SECONDS = 60

TRADEFED_PREFIX = 'autotest-tradefed-install_'
# While running CTS tradefed creates state in the installed location (there is
# currently no way to specify a dedicated result directory for all changes).
# For this reason we start each test with a clean copy of the CTS/GTS bundle.
TRADEFED_CACHE_LOCAL = '/tmp/autotest-tradefed-cache'
# On lab servers and moblab all server tests run inside of lxc instances
# isolating file systems from each other. To avoid downloading CTS artifacts
# repeatedly for each test (or lxc instance) we share a common location
# /usr/local/autotest/results/shared which is visible to all lxc instances on
# that server. It needs to be writable as the cache is maintained jointly by
# all CTS/GTS tests. Currently both read and write access require taking the
# lock. Writes happen rougly monthly while reads are many times a day. If this
# becomes a bottleneck we could examine allowing concurrent reads.
TRADEFED_CACHE_CONTAINER = '/usr/local/autotest/results/shared/cache'
TRADEFED_CACHE_CONTAINER_LOCK = '/usr/local/autotest/results/shared/lock'
# The maximum size of the shared global cache. It needs to be able to hold
# P, R, x86, arm, official, dev CTS bundles, as well as GTS bundles, and
# media assets. (See b/126165348#comment40 for the calculation.)
# In the current implementation, each test instance just symlinks to the
# shared cache for majority of the content, so running multiple parallel
# CTS tests should be acceptable in terms of storage.
TRADEFED_CACHE_MAX_SIZE = (100 * 1024 * 1024 * 1024)
# The path that cts-tradefed uses to place media assets. By downloading and
# expanding the archive here beforehand, tradefed can reuse the content.
TRADEFED_MEDIA_PATH = '/tmp/android-cts-media'
# The property tradefed reads to decide which helpers to install.
TRADEFED_CTS_HELPERS_PROPERTY = 'ro.vendor.cts_interaction_helper_packages'
# The directory on the board where CTS helpers can be found.
BOARD_CTS_HELPERS_DIR = '/usr/local/opt/google/vms/android'

# It looks like the GCE builder can be very slow and login on VMs take much
# longer than on hardware or bare metal.
LOGIN_BOARD_TIMEOUT = {'betty': 300, 'betty-arcnext': 300, 'betty-pi-arc': 300}
LOGIN_DEFAULT_TIMEOUT = 90

# List of boards that we want to run CTS in tablet mode for some models.
TABLET_MODE_BOARDS = ('kukui', 'nocturne', 'scarlet')

# Approximately assume ChromeOS revision Rdd-xxxxx.y.z with y>=45 as stable.
APPROXIMATE_STABLE_BRANCH_NUMBER = 45

# Directories for overriding powerd prefs during tests.
POWERD_PREF_DIR = '/var/lib/power_manager'
POWERD_TEMP_DIR = '/tmp/autotest_powerd_prefs'

PRIVATE_KEY = '''-----BEGIN PRIVATE KEY-----
MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCnHNzujonYRLoI
F2pyJX1SSrqmiT/3rTRCP1X0pj1V/sPGwgvIr+3QjZehLUGRQL0wneBNXd6EVrST
drO4cOPwSxRJjCf+/PtS1nwkz+o/BGn5yhNppdSro7aPoQxEVM8qLtN5Ke9tx/zE
ggxpF8D3XBC6Los9lAkyesZI6xqXESeofOYu3Hndzfbz8rAjC0X+p6Sx561Bt1dn
T7k2cP0mwWfITjW8tAhzmKgL4tGcgmoLhMHl9JgScFBhW2Nd0QAR4ACyVvryJ/Xa
2L6T2YpUjqWEDbiJNEApFb+m+smIbyGz0H/Kj9znoRs84z3/8rfyNQOyf7oqBpr2
52XG4totAgMBAAECggEARisKYWicXKDO9CLQ4Uj4jBswsEilAVxKux5Y+zbqPjeR
AN3tkMC+PHmXl2enRlRGnClOS24ExtCZVenboLBWJUmBJTiieqDC7o985QAgPYGe
9fFxoUSuPbuqJjjbK73olq++v/tpu1Djw6dPirkcn0CbDXIJqTuFeRqwM2H0ckVl
mVGUDgATckY0HWPyTBIzwBYIQTvAYzqFHmztcUahQrfi9XqxnySI91no8X6fR323
R8WQ44atLWO5TPCu5JEHCwuTzsGEG7dEEtRQUxAsH11QC7S53tqf10u40aT3bXUh
XV62ol9Zk7h3UrrlT1h1Ae+EtgIbhwv23poBEHpRQQKBgQDeUJwLfWQj0xHO+Jgl
gbMCfiPYvjJ9yVcW4ET4UYnO6A9bf0aHOYdDcumScWHrA1bJEFZ/cqRvqUZsbSsB
+thxa7gjdpZzBeSzd7M+Ygrodi6KM/ojSQMsen/EbRFerZBvsXimtRb88NxTBIW1
RXRPLRhHt+VYEF/wOVkNZ5c2eQKBgQDAbwNkkVFTD8yQJFxZZgr1F/g/nR2IC1Yb
ylusFztLG998olxUKcWGGMoF7JjlM6pY3nt8qJFKek9bRJqyWSqS4/pKR7QTU4Nl
a+gECuD3f28qGFgmay+B7Fyi9xmBAsGINyVxvGyKH95y3QICw1V0Q8uuNwJW2feo
3+UD2/rkVQKBgFloh+ljC4QQ3gekGOR0rf6hpl8D1yCZecn8diB8AnVRBOQiYsX9
j/XDYEaCDQRMOnnwdSkafSFfLbBrkzFfpe6viMXSap1l0F2RFWhQW9yzsvHoB4Br
W7hmp73is2qlWQJimIhLKiyd3a4RkoidnzI8i5hEUBtDsqHVHohykfDZAoGABNhG
q5eFBqRVMCPaN138VKNf2qon/i7a4iQ8Hp8PHRr8i3TDAlNy56dkHrYQO2ULmuUv
Erpjvg5KRS/6/RaFneEjgg9AF2R44GrREpj7hP+uWs72GTGFpq2+v1OdTsQ0/yr0
RGLMEMYwoY+y50Lnud+jFyXHZ0xhkdzhNTGqpWkCgYBigHVt/p8uKlTqhlSl6QXw
1AyaV/TmfDjzWaNjmnE+DxQfXzPi9G+cXONdwD0AlRM1NnBRN+smh2B4RBeU515d
x5RpTRFgzayt0I4Rt6QewKmAER3FbbPzaww2pkfH1zr4GJrKQuceWzxUf46K38xl
yee+dcuGhs9IGBOEEF7lFA==
-----END PRIVATE KEY-----
'''
