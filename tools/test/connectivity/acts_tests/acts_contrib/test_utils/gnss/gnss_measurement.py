from datetime import datetime
import os
import pathlib
import re
import shutil
import tempfile
from collections import defaultdict


_EVENT_TIME_FORMAT = "%Y/%m/%d %H:%M:%S.%f"
_EVENT_TIME_PATTERN = re.compile(r"(\d+(?:\/\d+){2}\s\d{2}(?::\d{2}){2}\.\d+)\sRead:")
_GNSS_CLOCK_START_LOG_PATTERN = re.compile(r"^GnssClock:")
_GNSS_CLOCK_TIME_NANOS_PATTERN = re.compile(f"^\s+TimeNanos\s*=\s*([-]?\d*)")
_GNSS_CLOCK_FULL_BIAS_NANOS_PATTERN = re.compile(f"^\s+FullBiasNanos\s*=\s*([-]?\d*)")
_GNSS_CLOCK_ELAPSED_TIME_NANOS_PATTERN = re.compile(f"^\s+elapsedRealtimeNanos\s*=\s*([-]?\d*)")

class AdrInfo:
    """Represent one ADR value
    An ADR value is a decimal number range from 0 - 31

    How to parse the ADR value:
        First, transform the decimal number to binary then we will get a 5 bit number
        The meaning of each bit is as follow:
                 0                  0               0         0       0
        HalfCycleReported   HalfCycleResolved   CycleSlip   Reset   Valid
    Special rule:
        For an ADR value in binary fits the pattern: * * 0 0 1, we call it a usable ADR
    More insight of ADR value:
        go/adrstates

    Attributes:
        is_valid: (bool)
        is_reset: (bool)
        is_cycle_slip: (bool)
        is_half_cycle_resolved: (bool)
        is_half_cycle_reported: (bool)
        is_usable: (bool)
    """
    def __init__(self, adr_value: int, count: int):
        src = bin(int(adr_value))
        self._valid = int(src[-1])
        self._reset = int(src[-2])
        self._cycle_slip = int(src[-3])
        self._half_cycle_resolved = int(src[-4])
        self._half_cycle_reported = int(src[-5])
        self.count = count

    @property
    def is_usable(self):
        return self.is_valid and not self.is_reset and not self.is_cycle_slip

    @property
    def is_valid(self):
        return bool(self._valid)

    @property
    def is_reset(self):
        return bool(self._reset)

    @property
    def is_cycle_slip(self):
        return bool(self._cycle_slip)

    @property
    def is_half_cycle_resolved(self):
        return bool(self._half_cycle_resolved)

    @property
    def is_half_cycle_reported(self):
        return bool(self._half_cycle_reported)


class AdrStatistic:
    """Represent the ADR statistic

    Attributes:
        usable_count: (int)
        valid_count: (int)
        reset_count: (int)
        cycle_slip_count: (int)
        half_cycle_resolved_count: (int)
        half_cycle_reported_count: (int)
        total_count: (int)
        usable_rate: (float)
            usable_count / total_count
        valid_rate: (float)
            valid_count / total_count
    """
    def __init__(self):
        self.usable_count = 0
        self.valid_count = 0
        self.reset_count = 0
        self.cycle_slip_count = 0
        self.half_cycle_resolved_count = 0
        self.half_cycle_reported_count = 0
        self.total_count = 0

    @property
    def usable_rate(self):
        denominator = max(1, self.total_count)
        result = self.usable_count / denominator
        return round(result, 3)

    @property
    def valid_rate(self):
        denominator = max(1, self.total_count)
        result = self.valid_count / denominator
        return round(result, 3)

    def add_adr_info(self, adr_info: AdrInfo):
        """Add ADR info record to increase the statistic

        Args:
            adr_info: AdrInfo object
        """
        if adr_info.is_valid:
            self.valid_count += adr_info.count
        if adr_info.is_reset:
            self.reset_count += adr_info.count
        if adr_info.is_cycle_slip:
            self.cycle_slip_count += adr_info.count
        if adr_info.is_half_cycle_resolved:
            self.half_cycle_resolved_count += adr_info.count
        if adr_info.is_half_cycle_reported:
            self.half_cycle_reported_count += adr_info.count
        if adr_info.is_usable:
            self.usable_count += adr_info.count
        self.total_count += adr_info.count


class GnssClockSubEvent:
    time_nanos: int
    full_bias_nanos: int
    elapsed_real_time_nanos: int

    def __init__(self, event_time):
        self.event_time = event_time

    def parse(self, line):
        if _GNSS_CLOCK_TIME_NANOS_PATTERN.search(line):
            self.time_nanos = int(_GNSS_CLOCK_TIME_NANOS_PATTERN.search(line).group(1))
        elif _GNSS_CLOCK_FULL_BIAS_NANOS_PATTERN.search(line):
            self.full_bias_nanos = int(
                _GNSS_CLOCK_FULL_BIAS_NANOS_PATTERN.search(line).group(1))
        elif _GNSS_CLOCK_ELAPSED_TIME_NANOS_PATTERN.search(line):
            self.elapsed_real_time_nanos = int(
                _GNSS_CLOCK_ELAPSED_TIME_NANOS_PATTERN.search(line).group(1))

    def __repr__(self) -> str:
        return (f"event time: {self.event_time}, "
                f"timenanos: {self.time_nanos}, "
                f"full_bias: {self.full_bias_nanos}, "
                f"elapsed_realtime: {self.elapsed_real_time_nanos}")

    @property
    def gps_elapsed_realtime_diff(self):
        return self.time_nanos - self.full_bias_nanos - self.elapsed_real_time_nanos


class GnssMeasurement:
    """Represent the content of measurement file generated by gps tool"""

    FILE_PATTERN = "/storage/emulated/0/Android/data/com.android.gpstool/files/MEAS*.txt"

    def __init__(self, ad):
        self.ad = ad

    def _generate_local_temp_path(self, file_name="file.txt"):
        """Generate a file path for temporarily usage

        Returns:
            string: local file path
        """
        folder = tempfile.mkdtemp()
        file_path = os.path.join(folder, file_name)
        return file_path

    def _get_latest_measurement_file_path(self):
        """Get the latest measurement file path on device

        Returns:
            string: file path on device
        """
        command = f"ls -tr {self.FILE_PATTERN} | tail -1"
        result = self.ad.adb.shell(command)
        return result

    def get_latest_measurement_file(self):
        """Pull the latest measurement file from device to local

        Returns:
            string: local file path to the measurement file

        Raise:
            FileNotFoundError: can't get measurement file from device
        """
        self.ad.log.info("Get measurement file from device")
        dest = self._generate_local_temp_path(file_name="measurement.txt")
        src = self._get_latest_measurement_file_path()
        if not src:
            raise FileNotFoundError(f"Can not find measurement file: pattern {self.FILE_PATTERN}")
        self.ad.pull_files(src, dest)
        return dest

    def _get_adr_src_value(self):
        """Get ADR value from measurement file

        Returns:
            dict: {<ADR_value>: count, <ADR_value>: count...}
        """
        try:
            file_path = self.get_latest_measurement_file()
            adr_src = defaultdict(int)
            adr_src_regex = re.compile("=\s(\d*)")
            with open(file_path) as f:
                for line in f:
                    if "AccumulatedDeltaRangeState" in line:
                        value = re.search(adr_src_regex, line)
                        if not value:
                            self.ad.log.warn("Can't get ADR value %s" % line)
                            continue
                        key = value.group(1)
                        adr_src[key] += 1
            return adr_src
        finally:
            folder = pathlib.PurePosixPath(file_path).parent
            shutil.rmtree(folder, ignore_errors=True)

    def get_adr_static(self):
        """Get ADR statistic

        Summarize ADR value from measurement file

        Returns:
            AdrStatistic object
        """
        self.ad.log.info("Get ADR statistic")
        adr_src = self._get_adr_src_value()
        adr_static = AdrStatistic()
        for key, value in adr_src.items():
            self.ad.log.debug("ADR value: %s - count: %s" % (key, value))
            adr_info = AdrInfo(key, value)
            adr_static.add_adr_info(adr_info)
        return adr_static

    def get_gnss_clock_info(self):
        sub_events = []
        event_time = None
        with tempfile.TemporaryDirectory() as folder:
            local_measurement_file = os.path.join(folder, "measurement_file")
            self.ad.pull_files(self._get_latest_measurement_file_path(), local_measurement_file)
            with open(local_measurement_file) as f:
                for line in f:
                    if re.search(_EVENT_TIME_PATTERN, line):
                        event_time = re.search(_EVENT_TIME_PATTERN, line).group(1)
                        event_time = datetime.strptime(event_time, _EVENT_TIME_FORMAT)
                    elif re.search(_GNSS_CLOCK_START_LOG_PATTERN, line):
                        sub_events.append(GnssClockSubEvent(event_time))
                    elif line.startswith(" "):
                        sub_events[-1].parse(line)
        return sub_events

