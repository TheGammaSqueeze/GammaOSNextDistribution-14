#!/usr/bin/python3

# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Performance stats proto parser utils."""

import datetime
from typing import Optional, TypeVar
from . import carwatchdog_dump_parser
from . import deviceperformancestats_pb2
from . import performancestats_pb2


def _create_date_pb(date: datetime.datetime) -> performancestats_pb2.Date:
  return performancestats_pb2.Date(
      year=date.year, month=date.month, day=date.day
  )


def _create_timeofday_pb(date) -> performancestats_pb2.TimeOfDay:
  return performancestats_pb2.TimeOfDay(
      hours=date.hour, minutes=date.minute, seconds=date.second
  )


def _add_system_event_pb(
    system_event_stats: carwatchdog_dump_parser.SystemEventStats,
    system_event_pb: performancestats_pb2.SystemEventStats,
) -> None:
  """Adds the parser SystemEventStats object to the proto object."""
  for collection in system_event_stats.collections:
    stats_collection_pb = performancestats_pb2.StatsCollection(
        id=collection.id,
        date=_create_date_pb(collection.date),
        time=_create_timeofday_pb(collection.date),
        total_cpu_time_ms=collection.total_cpu_time_ms,
        total_cpu_cycles=collection.total_cpu_cycles,
        idle_cpu_time_ms=collection.idle_cpu_time_ms,
        io_wait_time_ms=collection.io_wait_time_ms,
        context_switches=collection.context_switches,
        io_blocked_processes=collection.io_blocked_processes,
        major_page_faults=collection.major_page_faults,
    )

    for package_cpu_stats in collection.package_cpu_stats:
      package_cpu_stats_pb = performancestats_pb2.PackageCpuStats(
          user_id=package_cpu_stats.user_id,
          package_name=package_cpu_stats.package_name,
          cpu_time_ms=package_cpu_stats.cpu_time_ms,
          total_cpu_time_percent=package_cpu_stats.total_cpu_time_percent,
          cpu_cycles=package_cpu_stats.cpu_cycles,
      )

      for process_cpu_stats in package_cpu_stats.process_cpu_stats:
        package_cpu_stats_pb.process_cpu_stats.append(
            performancestats_pb2.ProcessCpuStats(
                command=process_cpu_stats.command,
                cpu_time_ms=process_cpu_stats.cpu_time_ms,
                package_cpu_time_percent=(
                    process_cpu_stats.package_cpu_time_percent
                ),
                cpu_cycles=process_cpu_stats.cpu_cycles,
            )
        )

    stats_collection_pb.package_cpu_stats.append(package_cpu_stats_pb)
    system_event_pb.collections.append(stats_collection_pb)

    for (
        package_storage_io_read_stats
    ) in collection.package_storage_io_read_stats:
      stats_collection_pb.package_storage_io_read_stats.append(
          performancestats_pb2.PackageStorageIoStats(
              user_id=package_storage_io_read_stats.user_id,
              package_name=package_storage_io_read_stats.package_name,
              fg_bytes=package_storage_io_read_stats.fg_bytes,
              fg_bytes_percent=package_storage_io_read_stats.fg_bytes_percent,
              fg_fsync=package_storage_io_read_stats.fg_fsync,
              fg_fsync_percent=package_storage_io_read_stats.fg_fsync_percent,
              bg_bytes=package_storage_io_read_stats.bg_bytes,
              bg_bytes_percent=package_storage_io_read_stats.bg_bytes_percent,
              bg_fsync=package_storage_io_read_stats.bg_fsync,
              bg_fsync_percent=package_storage_io_read_stats.bg_fsync_percent,
          )
      )

    for (
        package_storage_io_write_stats
    ) in collection.package_storage_io_write_stats:
      stats_collection_pb.package_storage_io_read_stats.append(
          performancestats_pb2.PackageStorageIoStats(
              user_id=package_storage_io_write_stats.user_id,
              package_name=package_storage_io_write_stats.package_name,
              fg_bytes=package_storage_io_write_stats.fg_bytes,
              fg_bytes_percent=package_storage_io_write_stats.fg_bytes_percent,
              fg_fsync=package_storage_io_write_stats.fg_fsync,
              fg_fsync_percent=package_storage_io_write_stats.fg_fsync_percent,
              bg_bytes=package_storage_io_write_stats.bg_bytes,
              bg_bytes_percent=package_storage_io_write_stats.bg_bytes_percent,
              bg_fsync=package_storage_io_write_stats.bg_fsync,
              bg_fsync_percent=package_storage_io_write_stats.bg_fsync_percent,
          )
      )


def _get_system_event(
    system_event_pb: performancestats_pb2.SystemEventStats,
) -> Optional[carwatchdog_dump_parser.SystemEventStats]:
  """Generates carwatchdog_dump_parser.SystemEventStats from the given proto."""
  if not system_event_pb.collections:
    return None

  system_event_stats = carwatchdog_dump_parser.SystemEventStats()
  for stats_collection_pb in system_event_pb.collections:
    stats_collection = carwatchdog_dump_parser.StatsCollection()
    stats_collection.id = stats_collection_pb.id
    date_pb = stats_collection_pb.date
    time_pb = stats_collection_pb.time
    stats_collection.date = datetime.datetime(
        date_pb.year,
        date_pb.month,
        date_pb.day,
        time_pb.hours,
        time_pb.minutes,
        time_pb.seconds,
    )
    stats_collection.total_cpu_time_ms = stats_collection_pb.total_cpu_time_ms
    stats_collection.total_cpu_cycles = stats_collection_pb.total_cpu_cycles
    stats_collection.idle_cpu_time_ms = stats_collection_pb.idle_cpu_time_ms
    stats_collection.io_wait_time_ms = stats_collection_pb.io_wait_time_ms
    stats_collection.context_switches = stats_collection_pb.context_switches
    stats_collection.io_blocked_processes = (
        stats_collection_pb.io_blocked_processes
    )
    stats_collection.major_page_faults = stats_collection_pb.major_page_faults

    for package_cpu_stats_pb in stats_collection_pb.package_cpu_stats:
      package_cpu_stats = carwatchdog_dump_parser.PackageCpuStats.from_proto(
          package_cpu_stats_pb
      )
      for process_cpu_stats_pb in package_cpu_stats_pb.process_cpu_stats:
        package_cpu_stats.process_cpu_stats.append(
            carwatchdog_dump_parser.ProcessCpuStats.from_proto(
                process_cpu_stats_pb
            )
        )
      stats_collection.package_cpu_stats.append(package_cpu_stats)

    for (
        package_storage_io_read_stats_pb
    ) in stats_collection_pb.package_storage_io_read_stats:
      stats_collection.package_storage_io_read_stats.append(
          carwatchdog_dump_parser.PackageStorageIoStats.from_proto(
              package_storage_io_read_stats_pb
          )
      )

    for (
        package_storage_io_write_stats_pb
    ) in stats_collection_pb.package_storage_io_write_stats:
      stats_collection.package_storage_io_write_stats.append(
          carwatchdog_dump_parser.PackageStorageIoStats.from_proto(
              package_storage_io_write_stats_pb
          )
      )

    system_event_stats.add(stats_collection)

  return system_event_stats


def _get_perf_stats(
    perf_stats_pb: performancestats_pb2.PerformanceStats,
) -> carwatchdog_dump_parser.PerformanceStats:
  """Generates carwatchdog_dump_parser.PerformanceStats from the given proto."""
  perf_stats = carwatchdog_dump_parser.PerformanceStats()
  perf_stats.boot_time_stats = _get_system_event(perf_stats_pb.boot_time_stats)
  perf_stats.last_n_minutes_stats = _get_system_event(
      perf_stats_pb.last_n_minutes_stats
  )
  perf_stats.custom_collection_stats = _get_system_event(
      perf_stats_pb.custom_collection_stats
  )
  return perf_stats


def _get_build_info(
    build_info_pb: deviceperformancestats_pb2.BuildInformation,
) -> carwatchdog_dump_parser.BuildInformation:
  """Generates carwatchdog_dump_parser.BuildInformation from the given proto."""
  build_info = carwatchdog_dump_parser.BuildInformation()
  build_info.fingerprint = build_info_pb.fingerprint
  build_info.brand = build_info_pb.brand
  build_info.product = build_info_pb.product
  build_info.device = build_info_pb.device
  build_info.version_release = build_info_pb.version_release
  build_info.id = build_info_pb.id
  build_info.version_incremental = build_info_pb.version_incremental
  build_info.type = build_info_pb.type
  build_info.tags = build_info_pb.tags
  build_info.sdk = build_info_pb.sdk
  build_info.platform_minor = build_info_pb.platform_minor
  build_info.codename = build_info_pb.codename
  return build_info


def write_pb(
    perf_stats: carwatchdog_dump_parser.PerformanceStats,
    out_file: str,
    build_info: Optional[carwatchdog_dump_parser.BuildInformation] = None,
    out_build_file: Optional[str] = None,
) -> bool:
  """Generates proto from parser objects and writes text proto to out files."""
  if perf_stats.is_empty():
    print("Cannot write proto since performance stats are empty")
    return False

  perf_stats_pb = performancestats_pb2.PerformanceStats()

  # Boot time proto
  if (stats := perf_stats.get_boot_time_stats()) is not None:
    boot_time_stats_pb = performancestats_pb2.SystemEventStats()
    _add_system_event_pb(stats, boot_time_stats_pb)
    perf_stats_pb.boot_time_stats.CopyFrom(boot_time_stats_pb)

  if (stats := perf_stats.get_last_n_minutes_stats()) is not None:
    last_n_minutes_stats_pb = performancestats_pb2.SystemEventStats()
    _add_system_event_pb(stats, last_n_minutes_stats_pb)
    perf_stats_pb.last_n_minutes_stats.CopyFrom(last_n_minutes_stats_pb)

  # TODO(b/256654082): Add user switch events to proto

  # Custom collection proto
  if (stats := perf_stats.get_custom_collection_stats()) is not None:
    custom_collection_stats_pb = performancestats_pb2.SystemEventStats()
    _add_system_event_pb(stats, custom_collection_stats_pb)
    perf_stats_pb.custom_collection_stats.CopyFrom(custom_collection_stats_pb)

  # Write pb binary to disk
  if out_file:
    with open(out_file, "wb") as f:
      f.write(perf_stats_pb.SerializeToString())

  if build_info is not None:
    build_info_pb = deviceperformancestats_pb2.BuildInformation(
        fingerprint=build_info.fingerprint,
        brand=build_info.brand,
        product=build_info.product,
        device=build_info.device,
        version_release=build_info.version_release,
        id=build_info.id,
        version_incremental=build_info.version_incremental,
        type=build_info.type,
        tags=build_info.tags,
        sdk=build_info.sdk,
        platform_minor=build_info.platform_minor,
        codename=build_info.codename,
    )

    device_run_perf_stats_pb = (
        deviceperformancestats_pb2.DevicePerformanceStats()
    )
    device_run_perf_stats_pb.build_info.CopyFrom(build_info_pb)
    device_run_perf_stats_pb.perf_stats.add().CopyFrom(perf_stats_pb)

    with open(out_build_file, "wb") as f:
      f.write(device_run_perf_stats_pb.SerializeToString())

  return True


T = TypeVar("T")


def _read_proto_from_file(pb_file: str, proto: T) -> Optional[T]:
  """Reads the text proto from the given file and returns the proto object."""
  pb_type = (
      "DevicePerformanceStats"
      if isinstance(proto, deviceperformancestats_pb2.DevicePerformanceStats)
      else "PerformanceStats"
  )
  with open(pb_file, "rb") as f:
    try:
      proto.ParseFromString(f.read())
      proto.DiscardUnknownFields()
    except UnicodeDecodeError:
      print(f"Error: Proto in {pb_file} probably is not '{pb_type}'")
      return None

  if not proto:
    print(f"Error: Proto stored in {pb_file} has incorrect format.")
    return None

  return proto


def read_performance_stats_pb(
    pb_file: str,
) -> Optional[carwatchdog_dump_parser.PerformanceStats]:
  """Reads text proto from file and returns a PerformanceStats object."""
  performance_stats_pb = performancestats_pb2.PerformanceStats()
  performance_stats_pb = _read_proto_from_file(pb_file, performance_stats_pb)
  if performance_stats_pb is None:
    return None
  return _get_perf_stats(performance_stats_pb)


def read_device_performance_stats_pb(
    pb_file: str,
) -> Optional[carwatchdog_dump_parser.DevicePerformanceStats]:
  """Reads text proto from file and returns a DevicePerformanceStats object."""

  device_performance_stats_pb = (
      deviceperformancestats_pb2.DevicePerformanceStats()
  )
  device_performance_stats_pb = _read_proto_from_file(
      pb_file, device_performance_stats_pb
  )
  if device_performance_stats_pb is None:
    return None

  device_run_perf_stats = carwatchdog_dump_parser.DevicePerformanceStats()
  device_run_perf_stats.build_info = _get_build_info(
      device_performance_stats_pb.build_info
  )

  for perf_stat in device_performance_stats_pb.perf_stats:
    device_run_perf_stats.perf_stats.append(_get_perf_stats(perf_stat))

  return device_run_perf_stats
