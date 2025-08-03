# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Constants that will be used as key name in device health profile.
SERVO_TOPOLOGY_LABEL_PREFIX = 'servo_topology'
ST_DEVICE_MAIN = 'main'
ST_DEVICE_CHILDREN = 'children'

# Fields of servo topology item
ST_DEVICE_SERIAL = 'serial'
ST_DEVICE_TYPE = 'type'
ST_DEVICE_PRODUCT = 'sysfs_product'
ST_DEVICE_HUB_PORT = 'usb_hub_port'

ST_V4_TYPE = 'servo_v4'
ST_V4P1_TYPE = 'servo_v4p1'
ST_CR50_TYPE = 'ccd_cr50'
ST_C2D2_TYPE = 'c2d2'
ST_SERVO_MICRO_TYPE = 'servo_micro'
ST_SWEETBERRY_TYPE = 'sweetberry'

# Mapping between product names and types.
ST_PRODUCT_TYPES = {
        'Servo V4': ST_V4_TYPE,
        'Servo V4p1': ST_V4P1_TYPE,
        'Cr50': ST_CR50_TYPE,
        'Servo Micro': ST_SERVO_MICRO_TYPE,
        'C2D2': ST_C2D2_TYPE,
        'Sweetberry': ST_SWEETBERRY_TYPE
}

# Mapping vid-pid to servo types
VID_PID_SERVO_TYPES = {
        '18d1:501b': ST_V4_TYPE,
        '18d1:520d': ST_V4P1_TYPE,
        '18d1:5014': ST_CR50_TYPE,
        '18d1:501a': ST_SERVO_MICRO_TYPE,
        '18d1:5041': ST_C2D2_TYPE,
        '18d1:5020': ST_SWEETBERRY_TYPE
}

# List unchangeable fields per device.
SERVO_TOPOLOGY_ITEM_COMPARE_FIELDS = (
        ST_DEVICE_SERIAL,
        ST_DEVICE_TYPE,
        ST_DEVICE_PRODUCT,
)
