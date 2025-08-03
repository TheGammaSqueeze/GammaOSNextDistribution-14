#!/bin/sh
""":" # Shell script (in docstring to appease pylint)
# Find and invoke hermetic python3 interpreter
. "`dirname $0`/../../../vendor/google/aosp/scripts/envsetup.sh"
exec "$PY3" "$0" "$@"
# Shell script end

This program will take trusted application's manifest config JSON file as
input. Processes the JSON config file and creates packed data
mapping to C structures and dumps in binary format.

USAGE:
    manifest_compiler.py \
        --input <input_filename_1> \
        --input <input_filename_2> \
        --output <output_filename> \
        --constants <config_constants_file_1> \
        --constants <config_constants_file_2> \
        --header-dir <header_file_path>

    Arguments:
    input_filename  - Trusted app manifest config file in JSON format.
                      At least one manifest config should be provided.
                      Options from additional configs override scalars
                      and merge to lists of the main manifest config,
                      processing manifests in the order specified on the
                      command line.
    output_filename - Binary file containing packed manifest config data mapped
                      to C structres.
    config_constant_file - This is optional
                    Config file with constants in JSON format
                    Corresponding header file will be
                    created with its constants defined in it
    header_file_path - Directory in which header files to be generated.

    example:
        manifest_compiler.py \
                --input manifest.json \
                --input manifest_extra_mem_maps.json \
                --output output.bin \
                --constants manifest_constants.json \
                --header-dir \
                <build_dir>/user_tasks/trusty/user/app/sample/hwcrypto/include

    If the output filename is omitted, the compiler will only generate constants
    headers for the given constants files.


   Input sample JSON Manifest config file content -
   {
        "uuid": "SECURE_STORAGE_SERVER_APP_UUID",
        "min_heap": 4096,
        "min_stack": 4096,
        "mem_map": [
            {
                "id": 1,
                "addr": "0x70000000",
                "size": "0x1000"
            },
            {
                "id": 2,
                "addr": "0x70010000",
                "size": "0x100"
            },
            {
                "id": 3,
                "addr": "0x70020000",
                "size": "0x4",
                "type": "uncached_device",
                "non_secure": false
            }
        ],
        "mgmt_flags": {
            "restart_on_exit": true,
            "deferred_start": false,
            "non_critical_app": false
        },
        "start_ports": [
            {
                "name": "LOADABLE_START_PORT",
                "flags": {
                    "allow_ta_connect": true,
                    "allow_ns_connect": false
                }
            }
        ],
        "pinned_cpu": 3,
        "priority" : 10,
        "version": 1,
        "min_version": 1,
        "apploader_flags": {
            "requires_encryption": false
        }
   }

   JSON manifest constant config -
   {
        "header": "storage_constants.h",
        "constants": [
            {
                "name": "LOADABLE_START_PORT",
                "value": "com.android.trusty.appmgmt.loadable.start",
                "type": "port"
            },
            {
                "name": "SECURE_STORAGE_SERVER_APP_UUID",
                "value": "eca48f94-00aa-560e-8f8c-d94b50d484f3",
                "type": "uuid"
            }
        ]
    }
"""

import argparse
import io
import json
import os.path
import struct
import sys

assert (sys.version_info.major, sys.version_info.minor) >= (3, 6), (
        # pylint: disable-next=consider-using-f-string
        "Python 3.6 or newer is required; found {}. Did you forget to set PY3?"
        .format(sys.version))

# Manifest properties
UUID = "uuid"
MIN_HEAP = "min_heap"
MIN_STACK = "min_stack"
MIN_SHADOW_STACK = "min_shadow_stack"
MEM_MAP = "mem_map"
MEM_MAP_ID = "id"
MEM_MAP_ADDR = "addr"
MEM_MAP_SIZE = "size"
MEM_MAP_TYPE = "type"
MEM_MAP_TYPE_CACHED = "cached"
MEM_MAP_TYPE_UNCACHED = "uncached"
MEM_MAP_TYPE_UNCACHED_DEVICE = "uncached_device"
MEM_MAP_NON_SECURE = "non_secure"
MGMT_FLAGS = "mgmt_flags"
MGMT_FLAG_RESTART_ON_EXIT = "restart_on_exit"
MGMT_FLAG_DEFERRED_START = "deferred_start"
MGMT_FLAG_NON_CRITICAL_APP = "non_critical_app"
START_PORTS = "start_ports"
START_PORT_FLAGS = "flags"
START_PORT_NAME = "name"
START_PORT_ALLOW_TA_CONNECT = "allow_ta_connect"
START_PORT_ALLOW_NS_CONNECT = "allow_ns_connect"
APP_NAME = "app_name"
PINNED_CPU = "pinned_cpu"
PRIORITY = "priority"
VERSION = "version"
MIN_VERSION = "min_version"
APPLOADER_FLAGS = "apploader_flags"
APPLOADER_FLAGS_REQUIRES_ENCRYPTION = "requires_encryption"

# constants configs
CONSTANTS = "constants"
HEADER = "header"
CONST_NAME = "name"
CONST_VALUE = "value"
CONST_TYPE = "type"
CONST_UNSIGNED = "unsigned"
CONST_PORT = "port"
CONST_UUID = "uuid"
CONST_INT = "int"
CONST_BOOL = "bool"

# CONFIG TAGS
# These values need to be kept in sync with lib/app_manifest/app_manifest.h
TRUSTY_APP_CONFIG_KEY_MIN_STACK_SIZE = 1
TRUSTY_APP_CONFIG_KEY_MIN_HEAP_SIZE = 2
TRUSTY_APP_CONFIG_KEY_MAP_MEM = 3
TRUSTY_APP_CONFIG_KEY_MGMT_FLAGS = 4
TRUSTY_APP_CONFIG_KEY_START_PORT = 5
TRUSTY_APP_CONFIG_KEY_PINNED_CPU = 6
TRUSTY_APP_CONFIG_KEY_VERSION = 7
TRUSTY_APP_CONFIG_KEY_MIN_SHADOW_STACK_SIZE = 8
TRUSTY_APP_CONFIG_KEY_APPLOADER_FLAGS = 9
TRUSTY_APP_CONFIG_KEY_PRIORITY = 10
TRUSTY_APP_CONFIG_KEY_MIN_VERSION = 11

# MEM_MAP ARCH_MMU_FLAGS
# These values need to be kept in sync with $LKROOT/include/arch/mmu.h
ARCH_MMU_FLAG_CACHED = 0 << 0
ARCH_MMU_FLAG_UNCACHED = 1 << 0
ARCH_MMU_FLAG_UNCACHED_DEVICE = 2 << 0
ARCH_MMU_FLAG_CACHE_MASK = 3 << 0
ARCH_MMU_FLAG_NS = 1 << 5

# MGMT FLAGS
# These values need to be kept in sync with lib/app_manifest/app_manifest.h
TRUSTY_APP_MGMT_FLAGS_NONE = 0
TRUSTY_APP_MGMT_FLAGS_RESTART_ON_EXIT = 1 << 0
TRUSTY_APP_MGMT_FLAGS_DEFERRED_START = 1 << 1
TRUSTY_APP_MGMT_FLAGS_NON_CRITICAL_APP = 1 << 2

# APPLOADER FLAGS
# These values need to be kept in sync with lib/app_manifest/app_manifest.h
TRUSTY_APP_APPLOADER_FLAGS_NONE = 0
TRUSTY_APP_APPLOADER_FLAGS_REQUIRES_ENCRYPTION = 1 << 0

# START_PORT flags
# These values need to be kept in sync with user/base/include/user/trusty_ipc.h
IPC_PORT_ALLOW_TA_CONNECT = 0x1
IPC_PORT_ALLOW_NS_CONNECT = 0x2

IPC_PORT_PATH_MAX = 64


class Constant(object):
    def __init__(self, name, value, type_, unsigned=False, hex_num=False):
        self.name = name
        self.value = value
        self.type = type_
        self.unsigned = unsigned
        self.hex_num = hex_num


class ConfigConstants(object):
    def __init__(self, constants, header):
        self.constants = constants
        self.header = header


class StartPortFlags(object):
    def __init__(self, allow_ta_connect, allow_ns_connect):
        self.allow_ta_connect = allow_ta_connect
        self.allow_ns_connect = allow_ns_connect


class StartPort(object):
    def __init__(self, name, name_size, start_port_flags):
        self.name = name
        self.name_size = name_size
        self.start_port_flags = start_port_flags


class MemIOMap(object):
    def __init__(self, id_, addr, size, type_, non_secure):
        self.id = id_
        self.addr = addr
        self.size = size
        self.type = type_
        self.non_secure = non_secure


class MgmtFlags(object):
    def __init__(self, restart_on_exit, deferred_start, non_critical_app):
        self.restart_on_exit = restart_on_exit
        self.deferred_start = deferred_start
        self.non_critical_app = non_critical_app


class ApploaderFlags(object):
    def __init__(self, requires_encryption):
        self.requires_encryption = requires_encryption


class Manifest(object):
    """Holds Manifest data to be used for packing"""

    def __init__(
            self,
            uuid,
            app_name,
            min_heap,
            min_stack,
            min_shadow_stack,
            mem_io_maps,
            mgmt_flags,
            start_ports,
            pinned_cpu,
            priority,
            version,
            min_version,
            apploader_flags,
    ):
        self.uuid = uuid
        self.app_name = app_name
        self.min_heap = min_heap
        self.min_stack = min_stack
        self.min_shadow_stack = min_shadow_stack
        self.mem_io_maps = mem_io_maps
        self.mgmt_flags = mgmt_flags
        self.start_ports = start_ports
        self.pinned_cpu = pinned_cpu
        self.priority = priority
        self.version = version
        self.min_version = min_version
        self.apploader_flags = apploader_flags


class Log(object):
    """Tracks errors during manifest compilation"""

    def __init__(self):
        self.error_count = 0

    def error(self, msg):
        sys.stderr.write(f"Error: {msg}\n")
        self.error_count += 1

    def error_occurred(self):
        return self.error_count > 0


def get_string_sub_type(field):
    """For the given manifest JSON field it returns its literal value type
    mapped.
    """
    if field == UUID:
        return CONST_UUID
    if field == START_PORT_NAME:
        return CONST_PORT
    # field with string value but doesn't support a constant
    return None


def get_constant(constants, key, type_, log):
    const = constants.get(key)
    if const is None:
        return None

    if const.type != type_:
        log.error(f"{key} constant type mismatch, expected type is {type_}")
        return None

    return const.value


def get_string(manifest_dict, key, constants, log, optional=False,
               default=None):
    """Determines whether the value for the given key in dictionary is of type
    string and if it is a string then returns the value.
    """
    if key not in manifest_dict:
        if not optional:
            log.error(f"Manifest is missing required attribute - {key}")
        return default

    value = manifest_dict.pop(key)

    # try to check is this field holding a constant
    type_ = get_string_sub_type(key)
    if type_:
        const_value = get_constant(constants, value, type_, log)
        if const_value is not None:
            return const_value

    return coerce_to_string(value, key, log)


def coerce_to_string(value, key, log):
    if not isinstance(value, str):
        log.error(
            "Invalid value for" +
            f" {key} - \"{value}\", Valid string value is expected")
        return None

    return value


def get_int(manifest_dict, key, constants, log, optional=False,
            default=None):
    """Determines whether the value for the given key in dictionary is of type
    integer and if it is int then returns the value
    """
    if key not in manifest_dict:
        if not optional:
            log.error(f"Manifest is missing required attribute - {key}")
        return default

    value = manifest_dict.pop(key)
    const_value = get_constant(constants, value, CONST_INT, log)
    if const_value is not None:
        return const_value

    return coerce_to_int(value, key, log)


def coerce_to_int(value, key, log):
    if isinstance(value, int) and not isinstance(value, bool):
        return value
    if isinstance(value, str):
        try:
            return int(value, 0)
        except ValueError:
            log.error(f"Invalid value for {key} - \"{value}\", " +
                      "valid integer or hex string is expected")
            return None
    else:
        log.error("Invalid value for" +
                  f" {key} - \"{value}\", valid integer value is expected")
        return None


def get_list(manifest_dict, key, log, optional=False, default=None):
    """Determines whether the value for the given key in dictionary is of type
    List and if it is List then returns the value
    """
    if key not in manifest_dict:
        if not optional:
            log.error(f"Manifest is missing required attribute - {key}")
        return default

    return coerce_to_list(manifest_dict.pop(key), key, log)


def coerce_to_list(value, key, log):
    if not isinstance(value, list):
        log.error("Invalid value for" +
                  f" {key} - \"{value}\", valid list is expected")
        return None

    return value


def get_dict(manifest_dict, key, log, optional=False, default=None):
    """Determines whether the value for the given key in dictionary is of type
    Dictionary and if it is Dictionary then returns the value
    """
    if key not in manifest_dict:
        if not optional:
            log.error(f"Manifest is missing required attribute - {key}")
        return default

    return coerce_to_dict(manifest_dict.pop(key), key, log)


def coerce_to_dict(value, key, log):
    if not isinstance(value, dict):
        log.error("Invalid value for" +
                  f" {key} - \"{value}\", valid dict is expected")
        return None

    return value


def get_boolean(manifest_dict, key, constants, log, optional=False,
                default=None):
    """Determines whether the value for the given key in dictionary is of type
    boolean and if it is boolean then returns the value
    """
    if key not in manifest_dict:
        if not optional:
            log.error(f"Manifest is missing required attribute - {key}")
        return default

    value = manifest_dict.pop(key)
    const_value = get_constant(constants, value, CONST_BOOL, log)
    if const_value is not None:
        return const_value

    return coerce_to_boolean(value, key, log)


def coerce_to_boolean(value, key, log):
    if not isinstance(value, bool):
        log.error(
            "Invalid value for" +
            f" {key} - \"{value}\", Valid boolean value is expected")
        return None

    return value


def get_uuid(manifest_dict, key, constants, log, optional=False, default=None):
    if key not in manifest_dict:
        if not optional:
            log.error(f"Manifest is missing required attribute - {key}")
        return default

    uuid = get_string(manifest_dict, key, {}, log, optional, default)
    const_value = get_constant(constants, uuid, CONST_UUID, log)
    if const_value is not None:
        return const_value

    return parse_uuid(uuid, log)


def get_port(port, key, constants, log, optional=False, default=None):
    return get_string(port, key, constants, log, optional, default)


def parse_uuid(uuid, log):
    """Validate and arrange UUID byte order. If it is valid UUID then return 16
    byte UUID
    """
    if uuid is None:
        return None

    # Example UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf"
    if len(uuid) != 36:
        log.error(f"Invalid UUID {uuid}. UUID should be 16 bytes long")
        return None

    uuid_data = uuid.split("-")
    if len(uuid_data) != 5:
        log.error(
            f"Invalid UUID {uuid}. UUID should be 16 hexadecimal numbers"
            " divided into 5 groups by hyphens (-)"
        )
        return None

    try:
        uuid_data = [bytearray.fromhex(part) for part in uuid_data]
    except ValueError:
        log.error(
            f"Invalid UUID {uuid}. UUID should only contain hexadecimal"
            " numbers (separated by hyphens)"
        )
        return None

    if len(uuid_data[0]) != 4 or \
            len(uuid_data[1]) != 2 or \
            len(uuid_data[2]) != 2 or \
            len(uuid_data[3]) != 2 or \
            len(uuid_data[4]) != 6:
        log.error(f"Wrong grouping of UUID {uuid}")
        return None

    return b"".join(uuid_data)


def parse_memory_size(memory_size, memory_kind, log, zero_is_ok=True):
    """Validate memory size value. If valid, return memory size value else
    return None
    """
    if memory_size is None:
        return None

    if memory_size == 0 and not zero_is_ok:
        log.error(f"{memory_kind}: Minimum memory size cannot be zero.")
        return None
    if memory_size < 0 or memory_size % 4096 != 0:
        log.error(f"{memory_kind}: {memory_size}, Minimum memory size should " +
                  "be a non-negative multiple of 4096")
        return None

    return memory_size


def parse_shadow_stack_size(stack_size, log):
    """Validate the shadow stack size

    :returns: validated shadow stack size or None
    """
    if stack_size is None:
        return None

    # shadow call stack is only supported on arm64 where pointers are 8 bytes
    ptr_size = 8
    if stack_size < 0 or stack_size % ptr_size != 0:
        log.error(f"{MIN_SHADOW_STACK}: {stack_size}, Minimum shadow stack " +
                  "size should be a non-negative multiple of the native " +
                  "pointer size")
        return None

    return stack_size


def parse_mem_map_type(mem_map_type, log):
    if mem_map_type not in {MEM_MAP_TYPE_CACHED,
                            MEM_MAP_TYPE_UNCACHED,
                            MEM_MAP_TYPE_UNCACHED_DEVICE}:
        log.error(f"Unknown mem_map.type entry in manifest: {mem_map_type}")

    return mem_map_type


def parse_mem_map(mem_maps, key, constants, log):
    if mem_maps is None:
        return None

    mem_io_maps = []
    for mem_map_entry in mem_maps:
        mem_map_entry = coerce_to_dict(mem_map_entry, key, log)
        if mem_map_entry is None:
            continue
        mem_map = MemIOMap(
            get_int(mem_map_entry, MEM_MAP_ID, constants, log),
            get_int(mem_map_entry, MEM_MAP_ADDR, constants, log),
            get_int(mem_map_entry, MEM_MAP_SIZE, constants, log),
            parse_mem_map_type(
                get_string(mem_map_entry, MEM_MAP_TYPE, constants, log,
                           optional=True,
                           default=MEM_MAP_TYPE_UNCACHED_DEVICE), log),
            get_boolean(mem_map_entry, MEM_MAP_NON_SECURE, constants, log,
                        optional=True)
        )
        if mem_map_entry:
            log.error("Unknown attributes in mem_map entries in "
                      f"manifest: {mem_map_entry}")
        if any(item.id == mem_map.id for item in mem_io_maps):
            log.error(f"Duplicate mem_map ID found: {mem_map.id}")
        mem_io_maps.append(mem_map)

    return mem_io_maps


def parse_mgmt_flags(flags, constants, log):
    if flags is None:
        return None

    mgmt_flags = MgmtFlags(
        get_boolean(flags, MGMT_FLAG_RESTART_ON_EXIT, constants, log,
                    optional=True),
        get_boolean(flags, MGMT_FLAG_DEFERRED_START, constants, log,
                    optional=True),
        get_boolean(flags, MGMT_FLAG_NON_CRITICAL_APP, constants, log,
                    optional=True)
    )

    if flags:
        log.error("Unknown attributes in mgmt_flags entries in " +
                  f"manifest: {flags}")

    return mgmt_flags


def parse_apploader_flags(flags, constants, log):
    if flags is None:
        return None

    apploader_flags = ApploaderFlags(
        get_boolean(flags, APPLOADER_FLAGS_REQUIRES_ENCRYPTION, constants, log,
                    optional=True)
    )

    if flags:
        log.error("Unknown attributes in apploader_flags entries in " +
                  f"manifest: {flags}")

    return apploader_flags


def parse_app_start_ports(start_port_list, key, constants, log):
    start_ports = []

    for port_entry in start_port_list:
        port_entry = coerce_to_dict(port_entry, key, log)
        if port_entry is None:
            continue

        name = get_port(port_entry, START_PORT_NAME, constants, log)
        if len(name) >= IPC_PORT_PATH_MAX:
            log.error("Length of start port name should be less than " +
                      str(IPC_PORT_PATH_MAX))

        flags = get_dict(port_entry, START_PORT_FLAGS, log)
        start_ports_flag = None
        if flags:
            start_ports_flag = StartPortFlags(
                get_boolean(flags, START_PORT_ALLOW_TA_CONNECT, constants,
                            log),
                get_boolean(flags, START_PORT_ALLOW_NS_CONNECT, constants,
                            log))

        if port_entry:
            log.error("Unknown attributes in start_ports entries" +
                      f" in manifest: {port_entry}")
        if flags:
            log.error("Unknown attributes in start_ports.flags entries" +
                      f" in manifest: {flags}")

        start_ports.append(StartPort(name, len(name), start_ports_flag))

    return start_ports


def parse_app_name(app_name, log):
    if app_name is None:
        return None

    if not app_name:
        log.error("empty app-name is not allowed in manifest")
        return None

    return app_name.strip()


def parse_manifest_config(manifest_dict, constants, default_app_name, log):
    """validate the manifest config and extract key, values"""
    # UUID
    uuid = get_uuid(manifest_dict, UUID, constants, log)

    # MIN_HEAP
    min_heap = parse_memory_size(get_int(manifest_dict, MIN_HEAP, constants,
                                         log), MIN_HEAP, log)

    # MIN_STACK
    min_stack = parse_memory_size(get_int(manifest_dict, MIN_STACK, constants,
                                          log), MIN_STACK, log, False)

    # MIN_SHADOW_STACK
    min_shadow_stack = parse_shadow_stack_size(get_int(manifest_dict,
                                                       MIN_SHADOW_STACK,
                                                       constants, log,
                                                       optional=True), log)

    # MEM_MAP
    mem_io_maps = parse_mem_map(
        get_list(manifest_dict, MEM_MAP, log, optional=True, default=[]),
        MEM_MAP,
        constants, log)

    # MGMT_FLAGS
    mgmt_flags = parse_mgmt_flags(
        get_dict(manifest_dict, MGMT_FLAGS, log, optional=True,
                 default={
                     MGMT_FLAG_RESTART_ON_EXIT: False,
                     MGMT_FLAG_DEFERRED_START: False,
                     MGMT_FLAG_NON_CRITICAL_APP: False}),
        constants, log)

    # START_PORTS
    start_ports = parse_app_start_ports(
        get_list(manifest_dict, START_PORTS, log,
                 optional=True, default=[]),
        START_PORTS,
        constants,
        log)

    # APP_NAME
    app_name = parse_app_name(
        get_string(manifest_dict, APP_NAME, constants, log,
                   optional=True, default=default_app_name), log)

    # PINNED_CPU
    pinned_cpu = get_int(manifest_dict, PINNED_CPU, constants, log,
                         optional=True)

    # PRIORITY
    priority = get_int(manifest_dict, PRIORITY, constants, log, optional=True)

    # VERSION
    version = get_int(manifest_dict, VERSION, constants, log, optional=True)

    # MIN_VERSION
    min_version = get_int(
        manifest_dict, MIN_VERSION, constants, log, optional=True)

    if min_version is not None:
        if version is None:
            log.error("'min_version' cannot be specified without 'version'")
        elif version < min_version:
            log.error("'version' cannot be less than 'min_version'")

    # APPLOADER_FLAGS
    apploader_flags = parse_apploader_flags(
        get_dict(manifest_dict, APPLOADER_FLAGS, log, optional=True,
                 default={APPLOADER_FLAGS_REQUIRES_ENCRYPTION: False}),
        constants, log)

    # look for any extra attributes
    if manifest_dict:
        log.error(f"Unknown attributes in manifest: {manifest_dict} ")

    if log.error_occurred():
        return None

    return Manifest(uuid, app_name, min_heap, min_stack, min_shadow_stack,
                    mem_io_maps, mgmt_flags, start_ports, pinned_cpu,
                    priority, version, min_version, apploader_flags)


def swap_uuid_bytes(uuid):
    """This script represents UUIDs in a purely big endian order.
    Trusty stores the first three components of the UUID in little endian order.
    Rearrange the byte order accordingly by doing inverse
    on first three components of UUID
    """
    return uuid[3::-1] + uuid[5:3:-1] + uuid[7:5:-1] + uuid[8:]


def pack_mem_map_arch_mmu_flags(mem_map):
    arch_mmu_flags = 0

    if mem_map.type == MEM_MAP_TYPE_CACHED:
        arch_mmu_flags |= ARCH_MMU_FLAG_CACHED
    elif mem_map.type == MEM_MAP_TYPE_UNCACHED:
        arch_mmu_flags |= ARCH_MMU_FLAG_UNCACHED
    elif mem_map.type == MEM_MAP_TYPE_UNCACHED_DEVICE:
        arch_mmu_flags |= ARCH_MMU_FLAG_UNCACHED_DEVICE

    if mem_map.non_secure:
        arch_mmu_flags |= ARCH_MMU_FLAG_NS

    return arch_mmu_flags


def pack_mgmt_flags(mgmt_flags):
    flags = TRUSTY_APP_MGMT_FLAGS_NONE
    if mgmt_flags.restart_on_exit:
        flags |= TRUSTY_APP_MGMT_FLAGS_RESTART_ON_EXIT
    if mgmt_flags.deferred_start:
        flags |= TRUSTY_APP_MGMT_FLAGS_DEFERRED_START
    if mgmt_flags.non_critical_app:
        flags |= TRUSTY_APP_MGMT_FLAGS_NON_CRITICAL_APP

    return flags


def pack_apploader_flags(apploader_flags):
    flags = TRUSTY_APP_APPLOADER_FLAGS_NONE
    if apploader_flags.requires_encryption:
        flags |= TRUSTY_APP_APPLOADER_FLAGS_REQUIRES_ENCRYPTION

    return flags


def pack_start_port_flags(flags):
    start_port_flags = TRUSTY_APP_MGMT_FLAGS_NONE
    if flags.allow_ta_connect:
        start_port_flags |= IPC_PORT_ALLOW_TA_CONNECT
    if flags.allow_ns_connect:
        start_port_flags |= IPC_PORT_ALLOW_NS_CONNECT

    return start_port_flags


def pack_inline_string(value):
    """Pack a given string with null padding to make its size
    multiple of 4.
    packed data includes length + string + null + padding
    """
    size = len(value) + 1
    pad_len = 3 - (size + 3) % 4
    packed = struct.pack("I", size) + value.encode() + b"\0" + pad_len * b"\0"
    assert len(packed) % 4 == 0
    return packed


def pack_manifest_data(manifest):
    """Creates Packed data from extracted manifest data.
    Writes the packed data to binary file
    """
    # PACK {
    #        uuid, app_name_size, app_name,
    #        TRUSTY_APP_CONFIG_KEY_MIN_HEAP_SIZE, min_heap,
    #        TRUSTY_APP_CONFIG_KEY_MIN_STACK_SIZE, min_stack,
    #        TRUSTY_APP_CONFIG_KEY_MAP_MEM, id, addr, size,
    #        TRUSTY_APP_CONFIG_KEY_MGMT_FLAGS, mgmt_flags
    #        TRUSTY_APP_CONFIG_KEY_START_PORT, flag, name_size, name
    #        TRUSTY_APP_CONFIG_KEY_PINNED_CPU, pinned_cpu
    #        TRUSTY_APP_CONFIG_KEY_PRIORITY, priority
    #        TRUSTY_APP_CONFIG_KEY_VERSION, version
    #        TRUSTY_APP_CONFIG_KEY_MIN_VERSION, min_version
    #        TRUSTY_APP_CONFIG_KEY_MIN_SHADOW_STACK_SIZE, min_shadow_stack,
    #        TRUSTY_APP_CONFIG_KEY_APPLOADER_FLAGS, apploader_flags,
    #      }
    out = io.BytesIO()

    uuid = swap_uuid_bytes(manifest.uuid)
    out.write(uuid)

    out.write(pack_inline_string(manifest.app_name))

    if manifest.min_heap is not None:
        out.write(struct.pack("II", TRUSTY_APP_CONFIG_KEY_MIN_HEAP_SIZE,
                              manifest.min_heap))

    if manifest.min_stack is not None:
        out.write(struct.pack("II", TRUSTY_APP_CONFIG_KEY_MIN_STACK_SIZE,
                              manifest.min_stack))

    for memio_map in manifest.mem_io_maps:
        out.write(struct.pack("IIQQI",
                              TRUSTY_APP_CONFIG_KEY_MAP_MEM,
                              memio_map.id,
                              memio_map.addr,
                              memio_map.size,
                              pack_mem_map_arch_mmu_flags(memio_map)))

    if manifest.mgmt_flags is not None:
        out.write(struct.pack("II",
                              TRUSTY_APP_CONFIG_KEY_MGMT_FLAGS,
                              pack_mgmt_flags(manifest.mgmt_flags)))

    for port_entry in manifest.start_ports:
        out.write(struct.pack("II",
                              TRUSTY_APP_CONFIG_KEY_START_PORT,
                              pack_start_port_flags(
                                  port_entry.start_port_flags)))
        out.write(pack_inline_string(port_entry.name))

    if manifest.pinned_cpu is not None:
        out.write(struct.pack("II",
                              TRUSTY_APP_CONFIG_KEY_PINNED_CPU,
                              manifest.pinned_cpu))

    if manifest.priority is not None:
        out.write(struct.pack("II",
                              TRUSTY_APP_CONFIG_KEY_PRIORITY,
                              manifest.priority))

    if manifest.version is not None:
        out.write(struct.pack("II",
                              TRUSTY_APP_CONFIG_KEY_VERSION,
                              manifest.version))

    if manifest.min_version is not None:
        out.write(struct.pack("II",
                              TRUSTY_APP_CONFIG_KEY_MIN_VERSION,
                              manifest.min_version))

    if manifest.min_shadow_stack is not None:
        out.write(struct.pack("II",
                              TRUSTY_APP_CONFIG_KEY_MIN_SHADOW_STACK_SIZE,
                              manifest.min_shadow_stack))

    if manifest.apploader_flags is not None:
        out.write(struct.pack("II",
                              TRUSTY_APP_CONFIG_KEY_APPLOADER_FLAGS,
                              pack_apploader_flags(manifest.apploader_flags)))

    return out.getvalue()


def unpack_binary_manifest_to_json(packed_data):
    """Creates manifest JSON string from packed manifest data"""
    return manifest_data_to_json(unpack_binary_manifest_to_data(packed_data))


def manifest_data_to_json(manifest):
    return json.dumps(manifest, sort_keys=True, indent=4)


def unpack_binary_manifest_to_data(packed_data):
    """This method can be used for extracting manifest data from packed binary.
    UUID should be present in packed data.
    """
    manifest = {}

    # Extract UUID
    uuid, packed_data = packed_data[:16], packed_data[16:]
    uuid = swap_uuid_bytes(uuid)
    uuid = uuid.hex()
    uuid = uuid[:8] + "-" \
           + uuid[8:12] + "-" \
           + uuid[12:16] + "-" \
           + uuid[16:20] + "-" \
           + uuid[20:]

    manifest[UUID] = uuid

    # Extract APP_NAME
    # read size of the name, this includes a null character
    (name_size,), packed_data = struct.unpack(
        "I", packed_data[:4]), packed_data[4:]
    # read the name without a trailing null character
    manifest[APP_NAME], packed_data = \
        packed_data[:name_size - 1].decode(), packed_data[name_size - 1:]
    # discard trailing null characters
    # it includes trailing null character of a string and null padding
    pad_len = 1 + 3 - (name_size + 3) % 4
    packed_data = packed_data[pad_len:]

    # Extract remaining app configurations
    while len(packed_data) > 0:
        (tag,), packed_data = struct.unpack(
            "I", packed_data[:4]), packed_data[4:]

        if tag == TRUSTY_APP_CONFIG_KEY_MIN_HEAP_SIZE:
            assert MIN_HEAP not in manifest
            (manifest[MIN_HEAP],), packed_data = struct.unpack(
                "I", packed_data[:4]), packed_data[4:]
        elif tag == TRUSTY_APP_CONFIG_KEY_MIN_STACK_SIZE:
            assert MIN_STACK not in manifest
            (manifest[MIN_STACK],), packed_data = struct.unpack(
                "I", packed_data[:4]), packed_data[4:]
        elif tag == TRUSTY_APP_CONFIG_KEY_MIN_SHADOW_STACK_SIZE:
            assert MIN_SHADOW_STACK not in manifest
            (manifest[MIN_SHADOW_STACK],), packed_data = struct.unpack(
                "I", packed_data[:4]), packed_data[4:]
        elif tag == TRUSTY_APP_CONFIG_KEY_MAP_MEM:
            if MEM_MAP not in manifest:
                manifest[MEM_MAP] = []
            mem_map_entry = {}
            (id_,), packed_data = struct.unpack(
                "I", packed_data[:4]), packed_data[4:]
            (addr,), packed_data = struct.unpack(
                "Q", packed_data[:8]), packed_data[8:]
            (size,), packed_data = struct.unpack(
                "Q", packed_data[:8]), packed_data[8:]
            (arch_mmu_flags,), packed_data = struct.unpack(
                "I", packed_data[:4]), packed_data[4:]
            mem_map_entry[MEM_MAP_ID] = id_
            mem_map_entry[MEM_MAP_ADDR] = hex(addr)
            mem_map_entry[MEM_MAP_SIZE] = hex(size)
            mem_map_entry[MEM_MAP_TYPE] = {
                ARCH_MMU_FLAG_CACHED: MEM_MAP_TYPE_CACHED,
                ARCH_MMU_FLAG_UNCACHED: MEM_MAP_TYPE_UNCACHED,
                ARCH_MMU_FLAG_UNCACHED_DEVICE: MEM_MAP_TYPE_UNCACHED_DEVICE,
            }[arch_mmu_flags & ARCH_MMU_FLAG_CACHE_MASK]
            mem_map_entry[MEM_MAP_NON_SECURE] = bool(arch_mmu_flags &
                                                     ARCH_MMU_FLAG_NS)
            manifest[MEM_MAP].append(mem_map_entry)
        elif tag == TRUSTY_APP_CONFIG_KEY_MGMT_FLAGS:
            (flag,), packed_data = struct.unpack(
                "I", packed_data[:4]), packed_data[4:]
            mgmt_flag = {
                MGMT_FLAG_RESTART_ON_EXIT: False,
                MGMT_FLAG_DEFERRED_START: False,
                MGMT_FLAG_NON_CRITICAL_APP: False
            }
            if flag & TRUSTY_APP_MGMT_FLAGS_RESTART_ON_EXIT:
                mgmt_flag[MGMT_FLAG_RESTART_ON_EXIT] = True
            if flag & TRUSTY_APP_MGMT_FLAGS_DEFERRED_START:
                mgmt_flag[MGMT_FLAG_DEFERRED_START] = True
            if flag & TRUSTY_APP_MGMT_FLAGS_NON_CRITICAL_APP:
                mgmt_flag[MGMT_FLAG_NON_CRITICAL_APP] = True
            manifest[MGMT_FLAGS] = mgmt_flag
        elif tag == TRUSTY_APP_CONFIG_KEY_START_PORT:
            if START_PORTS not in manifest:
                manifest[START_PORTS] = []
            start_port_entry = {}

            (flag,), packed_data = struct.unpack(
                "I", packed_data[:4]), packed_data[4:]

            # read size of the name, this includes a null character
            (name_size,), packed_data = struct.unpack(
                "I", packed_data[:4]), packed_data[4:]
            # read the name without a trailing null character
            start_port_entry[START_PORT_NAME], packed_data = (
                packed_data[:name_size - 1].decode(),
                packed_data[name_size - 1:]
            )
            # discard trailing null characters
            # it includes trailing null character of a string and null padding
            pad_len = 1 + 3 - (name_size + 3) % 4
            packed_data = packed_data[pad_len:]

            start_port_flags = {
                START_PORT_ALLOW_TA_CONNECT: False,
                START_PORT_ALLOW_NS_CONNECT: False
            }
            if flag & IPC_PORT_ALLOW_TA_CONNECT:
                start_port_flags[START_PORT_ALLOW_TA_CONNECT] = True
            if flag & IPC_PORT_ALLOW_NS_CONNECT:
                start_port_flags[IPC_PORT_ALLOW_NS_CONNECT] = True
            start_port_entry[START_PORT_FLAGS] = start_port_flags

            manifest[START_PORTS].append(start_port_entry)
        elif tag == TRUSTY_APP_CONFIG_KEY_PINNED_CPU:
            assert PINNED_CPU not in manifest
            (pinned_cpu,), packed_data = struct.unpack(
                "I", packed_data[:4]), packed_data[4:]
            manifest[PINNED_CPU] = pinned_cpu
        elif tag == TRUSTY_APP_CONFIG_KEY_PRIORITY:
            assert PRIORITY not in manifest
            (priority,), packed_data = struct.unpack(
                "I", packed_data[:4]), packed_data[4:]
            manifest[PRIORITY] = priority
        elif tag == TRUSTY_APP_CONFIG_KEY_VERSION:
            assert VERSION not in manifest
            (version,), packed_data = struct.unpack(
                "I", packed_data[:4]), packed_data[4:]
            manifest[VERSION] = version
        elif tag == TRUSTY_APP_CONFIG_KEY_MIN_VERSION:
            assert MIN_VERSION not in manifest
            (min_version,), packed_data = struct.unpack(
                "I", packed_data[:4]), packed_data[4:]
            manifest[MIN_VERSION] = min_version
        elif tag == TRUSTY_APP_CONFIG_KEY_APPLOADER_FLAGS:
            assert APPLOADER_FLAGS not in manifest
            (flag,), packed_data = struct.unpack(
                "I", packed_data[:4]), packed_data[4:]
            apploader_flag = {
                APPLOADER_FLAGS_REQUIRES_ENCRYPTION: False,
            }
            if flag & TRUSTY_APP_APPLOADER_FLAGS_REQUIRES_ENCRYPTION:
                apploader_flag[APPLOADER_FLAGS_REQUIRES_ENCRYPTION] = True
            manifest[APPLOADER_FLAGS] = apploader_flag
        else:
            raise Exception(f"Unknown tag: {tag}")

    return manifest


def write_packed_data_to_bin_file(packed_data, output_file, log):
    """Write packed data to binary file"""
    try:
        with open(output_file, "wb") as out_file:
            out_file.write(packed_data)
            out_file.close()
    except IOError as ex:
        log.error(f"Unable to write to output file: {output_file}\n" + str(ex))


def read_json_config_file(input_file, log):
    try:
        with open(input_file, "r", encoding="utf-8") as read_file:
            manifest_dict = json.load(read_file)
        return manifest_dict
    except IOError as ex:
        log.error(f"{input_file}: unable to open input file: {ex}")
        return None
    except json.JSONDecodeError as jde:
        location = f"{input_file}:{jde.lineno}:{jde.colno}"
        log.error(f"{location}: Unable to parse config JSON: {jde.msg}")
        return None
    except ValueError as ex:
        log.error(f"{input_file}: Unexpected error: {ex}")
        return None


def read_config_constants(const_config_files, log):
    const_configs_list = []
    for const_file in const_config_files:
        const_configs_list.append(read_json_config_file(const_file, log))

    return const_configs_list


def define_integer_const_entry(const):
    text = hex(const.value) if const.hex_num else str(const.value)
    if const.unsigned:
        text += "U"

    return f"#define {const.name} ({text})\n"


def define_string_const_entry(const):
    return f"#define {const.name} {json.dumps(const.value)}\n"


def define_bool_const_entry(const):
    return f"#define {const.name} ({json.dumps(const.value)})\n"


def define_uuid_const_entry(const):
    uuid = const.value.hex()

    part = ", ".join(
        ["0x" + uuid[index:index + 2] for index in range(16, len(uuid), 2)])

    value = f"{{0x{uuid[:8]}, 0x{uuid[8:12]}, 0x{uuid[12:16]}, {{ {part} }}}}\n"

    return f"#define {const.name} {value}"


def create_header_entry(constant):
    if constant.type == CONST_PORT:
        return define_string_const_entry(constant)
    if constant.type == CONST_UUID:
        return define_uuid_const_entry(constant)
    if constant.type == CONST_INT:
        return define_integer_const_entry(constant)
    if constant.type == CONST_BOOL:
        return define_bool_const_entry(constant)
    raise Exception(f"Unknown tag: {constant.type}")


def write_consts_to_header_file(const_config, header_dir, log):
    """Writes given constants to header file in given header directory."""
    # Construct header file path
    header_file = os.path.join(header_dir, const_config.header)
    # Check whether the output directory of header file exist
    # If it not exists create it.
    dir_name = os.path.dirname(header_file)
    if dir_name and not os.path.exists(dir_name):
        os.makedirs(dir_name)

    try:
        with open(header_file, "w", encoding="utf-8") as out_file:
            out_file.write("#pragma once\n")
            out_file.write("#include <stdbool.h>\n\n")
            for const in const_config.constants:
                header_entries = create_header_entry(const)
                out_file.write(header_entries)
    except IOError as ex:
        log.error(f"Unable to write to header file: {header_file}\n" + str(ex))


def parse_constant(constant, log):
    """Parse a give JSON constant data structure"""
    const_type = get_string(constant, CONST_TYPE, {}, log)
    if const_type is None:
        return None

    name = get_string(constant, CONST_NAME, {}, log)
    if const_type == CONST_PORT:
        value = get_string(constant, CONST_VALUE, {}, log)
        return Constant(name, value, const_type)
    if const_type == CONST_UUID:
        value = get_string(constant, CONST_VALUE, {}, log)
        return Constant(name, parse_uuid(value, log), const_type)
    if const_type == CONST_INT:
        unsigned = get_boolean(constant, CONST_UNSIGNED, {}, log)
        text_value = constant.get(CONST_VALUE)
        hex_num = isinstance(text_value, str) and text_value.startswith("0x")
        value = get_int(constant, CONST_VALUE, {}, log)
        return Constant(name, value, const_type, unsigned, hex_num)
    if const_type == CONST_BOOL:
        value = get_boolean(constant, CONST_VALUE, {}, log)
        return Constant(name, value, const_type)

    log.error(f"Unknown constant type: {const_type}")
    return None


def parse_config_constant(const_config, log):
    """Parse a given JSON constant-config data structure containing a header and
    list of constants
    """
    header_file = get_string(const_config, HEADER, {}, log)

    const_list = get_list(const_config, CONSTANTS, log, optional=False,
                          default=[])

    constants = []
    for item in const_list:
        item = coerce_to_dict(item, CONSTANTS, log)
        if item is None:
            continue
        constants.append(parse_constant(item, log))
        if item:
            log.error("Unknown attributes in constant: {item}")

    if const_config:
        log.error(f"Unknown attributes in constants config: {const_config}")

    return ConfigConstants(constants, header_file)


def extract_config_constants(config_consts_list, log):
    """Collects ConfigConstant(s) from list of JSON config constants data"""
    config_constants = []

    for config_const in config_consts_list:
        config_constants.append(parse_config_constant(config_const, log))

    return config_constants


def process_config_constants(const_config_files, header_dir, log):
    """Parse JSON config constants and creates separate header files with
    constants for each JSON config
    """
    if const_config_files is None:
        return []

    config_consts_list = read_config_constants(const_config_files, log)
    if log.error_occurred():
        return []

    config_constants = extract_config_constants(config_consts_list, log)
    if log.error_occurred():
        return []

    # generate header files
    for const_config in config_constants:
        write_consts_to_header_file(const_config, header_dir, log)

    return config_constants

def merge_manifest_dicts(manifests: list, log):
    """Merges multiple manifests
    """
    def merge(base, overlay, log):
        match base, overlay:
            case dict(), dict():
                common_keys = base.keys() & overlay.keys()
                res =  {k: merge(base[k], overlay[k], log) for k in common_keys}
                res |= {k: base[k] for k in base.keys() - common_keys}
                res |= {k: overlay[k] for k in overlay.keys() - common_keys}
                return res
            case list(), list():
                res = base.copy()
                res.extend(i for i in overlay if i not in base)
                return res
            case int() | float() | str(), int() | float() | str():
                return overlay  # overlay overrides base for scalars
            case _:
                log.error(
                    f"Unhandled type pair: {type(base)} and {type(overlay)}")
                return base

    manifest_dict_merged : dict = {}

    for manifest in manifests:
        manifest_dict_merged = merge(manifest_dict_merged, manifest, log)

    if log.error_occurred():
        return None

    return manifest_dict_merged

def process_manifest_files(manifest_files, constants, log):
    """Parse JSON manifest(s)
    """
    assert manifest_files

    manifest_dicts = []

    for manifest_file in manifest_files:
        if not os.path.exists(manifest_file):
            log.error(
                f"Manifest config JSON file doesn't exist: {manifest_file}")
            return None

        manifest_dict = read_json_config_file(manifest_file, log)
        if log.error_occurred():
            return None

        manifest_dicts.append(manifest_dict)

    manifest_dict_merged = merge_manifest_dicts(manifest_dicts, log)

    if manifest_dict_merged is None or log.error_occurred():
        return None

    default_app_name = os.path.basename(os.path.dirname(manifest_files[0]))

    return parse_manifest_config(manifest_dict_merged, constants,
                                 default_app_name, log)


def index_constants(config_constants):
    constants = {}
    for const_config in config_constants:
        for const in const_config.constants:
            constants[const.name] = const

    return constants


def main():
    """Handles the command line arguments. Parses the given manifest input file
    and creates packed data. Writes the packed data to binary output file.
    """
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "-i", "--input",
        dest="input_filenames",
        required=False,
        action="append",
        type=str,
        help="Trusty app manifest in JSON format. "
             "If the flag is used to provide multiple input files, "
             "subsequent files overwrite the values provided in "
             "the first manifest file."
    )
    parser.add_argument(
        "-o", "--output",
        dest="output_filename",
        required=False,
        type=str,
        help="It will be binary file with packed manifest data"
    )
    parser.add_argument(
        "-c", "--constants",
        dest="constants",
        required=False,
        action="append",
        help="JSON file with manifest config constants"
    )
    parser.add_argument(
        "--header-dir",
        dest="header_dir",
        required=False,
        type=str,
        help="Directory path for generating headers"
    )
    parser.add_argument(
        "--enable-shadow-call-stack",
        dest="shadow_call_stack",
        required=False,
        action="store_true",  # implies default := False
        help="Allow apps to opt into having a shadow call stack. "
             "Without this flag, apps will not have shadow stacks "
             "even if their manifests define \"min_shadow_stack\"."
    )
    parser.add_argument(
        "--default-shadow-call-stack-size",
        dest="default_shadow_call_stack_size",
        required=False,
        default=4096,
        type=int,
        metavar="DEFAULT_SIZE",
        help="Controls the size of the default shadow call stack."
             "This option has no effect unless shadow call stacks "
             "are enabled via the --enable-shadow-call-stack flag."
    )
    # Parse the command line arguments
    args = parser.parse_args()
    if args.constants and not args.header_dir:
        parser.error("--header-dir is required if --constants are specified")

    if args.input_filenames and not args.output_filename:
        parser.error("Input file provided with no manifest output file.")

    if args.output_filename and not args.input_filenames:
        parser.error("Building a manifest output file requires an input file.")

    if args.default_shadow_call_stack_size <= 0:
        parser.error(
            "--default-shadow-call-stack-size expects a positive integer")

    log = Log()

    # collect config constants and create header files for each const config
    config_constants = process_config_constants(args.constants,
                                                args.header_dir, log)
    if log.error_occurred():
        return 1

    if not args.output_filename:
        return 0

    constants = index_constants(config_constants)

    manifest = process_manifest_files(args.input_filenames, constants, log)

    if manifest is None:
        return 1

    if log.error_occurred():
        return 1

    # Optionally adjust min_shadow_stack based on command line arguments
    if args.shadow_call_stack:
        # If shadow callstack is enabled but the size is not specified in the
        # manifest, set it to the default value.
        if manifest.min_shadow_stack is None:
            manifest.min_shadow_stack = args.default_shadow_call_stack_size
    else:
        # If shadow call stack is not enabled, make sure the size is set to
        # zero in the binary manifest. In the future, "not present" may
        # indicate the binary does not use a shadow callstack, but for now
        # we're making sure a value is always present.
        manifest.min_shadow_stack = 0

    assert (args.shadow_call_stack and manifest.min_shadow_stack > 0) != \
           (manifest.min_shadow_stack == 0)

    # Pack the data as per C structures
    packed_data = pack_manifest_data(manifest)
    if log.error_occurred():
        return 1

    # Write to file.
    write_packed_data_to_bin_file(packed_data, args.output_filename, log)
    if log.error_occurred():
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
