# Copyright (c) 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import re
import subprocess

from autotest_lib.client.cros.audio import cmd_utils

SOX_PATH = 'sox'

def _raw_format_args(channels, bits, rate):
    """Gets raw format args used in sox.

    @param channels: Number of channels.
    @param bits: Bit length for a sample.
    @param rate: Sampling rate.

    @returns: A list of args.

    """
    args = ['-t', 'raw', '-e', 'signed']
    args += _format_args(channels, bits, rate)
    return args


def _format_args(channels, bits, rate):
    """Gets format args used in sox.

    @param channels: Number of channels.
    @param bits: Bit length for a sample.
    @param rate: Sampling rate.

    @returns: A list of args.

    """
    return ['-c', str(channels), '-b', str(bits), '-r', str(rate)]


def generate_sine_tone_cmd(
        filename, channels=2, bits=16, rate=48000, duration=None, frequencies=440,
        gain=None, vol=None, raw=True):
    """Gets a command to generate sine tones at specified ferquencies.

    @param filename: The name of the file to store the sine wave in.
    @param channels: The number of channels.
    @param bits: The number of bits of each sample.
    @param rate: The sampling rate.
    @param duration: The length of the generated sine tone (in seconds).
    @param frequencies: The frequencies of the sine wave. Pass a number or a
                        list to specify frequency for each channel.
    @param gain: The gain (in db).
    @param vol: A float for volume scale used in sox command.
                         E.g. 1.0 is the same. 0.5 to scale volume by
                         half. -1.0 to invert the data.
    @param raw: True to use raw data format. False to use what filename specifies.

    """
    args = [SOX_PATH, '-n']
    if raw:
        args += _raw_format_args(channels, bits, rate)
    else:
        args += _format_args(channels, bits, rate)
    args.append(filename)
    args.append('synth')
    if duration is not None:
        args.append(str(duration))
    if not isinstance(frequencies, list):
        frequencies = [frequencies]
    for freq in frequencies:
        args += ['sine', str(freq)]
    if gain is not None:
        args += ['gain', str(gain)]
    if vol is not None:
        args += ['vol', str(vol)]
    return args


def noise_profile(*args, **kwargs):
    """A helper function to execute the noise_profile_cmd."""
    return cmd_utils.execute(noise_profile_cmd(*args, **kwargs))


def noise_profile_cmd(input, output, channels=1, bits=16, rate=48000):
    """Gets the noise profile of the input audio.

    @param input: The input audio.
    @param output: The file where the output profile will be stored in.
    @param channels: The number of channels.
    @param bits: The number of bits of each sample.
    @param rate: The sampling rate.
    """
    args = [SOX_PATH]
    args += _raw_format_args(channels, bits, rate)
    args += [input, '-n', 'noiseprof', output]
    return args


def noise_reduce(*args, **kwargs):
    """A helper function to execute the noise_reduce_cmd."""
    return cmd_utils.execute(noise_reduce_cmd(*args, **kwargs))


def noise_reduce_cmd(
        input, output, noise_profile, channels=1, bits=16, rate=48000):
    """Reduce noise in the input audio by the given noise profile.

    @param input: The input audio file.
    @param output: The output file in which the noise reduced audio is stored.
    @param noise_profile: The noise profile.
    @param channels: The number of channels.
    @param bits: The number of bits of each sample.
    @param rate: The sampling rate.
    """
    args = [SOX_PATH]
    format_args = _raw_format_args(channels, bits, rate)
    args += format_args
    args.append(input)
    # Uses the same format for output.
    args += format_args
    args.append(output)
    args.append('noisered')
    args.append(noise_profile)
    return args


def extract_channel_cmd(
        input, output, channel_index, channels=2, bits=16, rate=48000):
    """Extract the specified channel data from the given input audio file.

    @param input: The input audio file.
    @param output: The output file to which the extracted channel is stored
    @param channel_index: The index of the channel to be extracted.
                          Note: 1 for the first channel.
    @param channels: The number of channels.
    @param bits: The number of bits of each sample.
    @param rate: The sampling rate.
    """
    args = [SOX_PATH]
    args += _raw_format_args(channels, bits, rate)
    args.append(input)
    args += ['-t', 'raw', output]
    args += ['remix', str(channel_index)]
    return args


def stat_cmd(input, channels=1, bits=16, rate=44100):
    """Get statistical information about the input audio data.

    The statistics will be output to standard error.

    @param input: The input audio file.
    @param channels: The number of channels.
    @param bits: The number of bits of each sample.
    @param rate: The sampling rate.
    """
    args = [SOX_PATH]
    args += _raw_format_args(channels, bits, rate)
    args += [input, '-n', 'stat']
    return args


def get_stat(*args, **kargs):
    """A helper function to execute the stat_cmd.

    It returns the statistical information (in text) read from the standard
    error.
    """
    p = cmd_utils.popen(stat_cmd(*args, **kargs), stderr=subprocess.PIPE)

    #The output is read from the stderr instead of stdout
    stat_output = p.stderr.read()
    cmd_utils.wait_and_check_returncode(p)
    return parse_stat_output(stat_output)


_SOX_STAT_ATTR_MAP = {
        'Samples read': ('sameple_count', int),
        'Length (seconds)': ('length', float),
        'RMS amplitude': ('rms', float),
        'Rough frequency': ('rough_frequency', float)}

_RE_STAT_LINE = re.compile('(.*):(.*)')

class _SOX_STAT:
    def __str__(self):
        return str(vars(self))


def _remove_redundant_spaces(value):
    return ' '.join(value.split()).strip()


def parse_stat_output(stat_output):
    """A helper function to parses the stat_cmd's output to get a python object
    for easy access to the statistics.

    It returns a python object with the following attributes:
      .sample_count: The number of the audio samples.
      .length: The length of the audio (in seconds).
      .rms: The RMS value of the audio.
      .rough_frequency: The rough frequency of the audio (in Hz).

    @param stat_output: The statistics ouput to be parsed.
    """
    stat = _SOX_STAT()

    for line in stat_output.splitlines():
        match = _RE_STAT_LINE.match(line.decode('utf-8'))
        if not match:
            continue
        key, value = (_remove_redundant_spaces(x) for x in match.groups())
        attr, convfun = _SOX_STAT_ATTR_MAP.get(key, (None, None))
        if attr:
            setattr(stat, attr, convfun(value))

    if not all(hasattr(stat, x[0]) for x in _SOX_STAT_ATTR_MAP.values()):
        logging.error('stat_output: %s', stat_output)
        raise RuntimeError('missing entries: ' + str(stat))

    return stat


def convert_raw_file(path_src, channels_src, bits_src, rate_src,
                     path_dst):
    """Converts a raw file to a new format.

    @param path_src: The path to the source file.
    @param channels_src: The channel number of the source file.
    @param bits_src: The size of sample in bits of the source file.
    @param rate_src: The sampling rate of the source file.
    @param path_dst: The path to the destination file. The file name determines
                     the new file format.

    """
    sox_cmd = [SOX_PATH]
    sox_cmd += _raw_format_args(channels_src, bits_src, rate_src)
    sox_cmd += [path_src]
    sox_cmd += [path_dst]
    cmd_utils.execute(sox_cmd)


def convert_format(path_src, channels_src, bits_src, rate_src,
                   path_dst, channels_dst, bits_dst, rate_dst,
                   volume_scale, use_src_header=False, use_dst_header=False):
    """Converts a raw file to a new format.

    @param path_src: The path to the source file.
    @param channels_src: The channel number of the source file.
    @param bits_src: The size of sample in bits of the source file.
    @param rate_src: The sampling rate of the source file.
    @param path_dst: The path to the destination file.
    @param channels_dst: The channel number of the destination file.
    @param bits_dst: The size of sample in bits of the destination file.
    @param rate_dst: The sampling rate of the destination file.
    @param volume_scale: A float for volume scale used in sox command.
                         E.g. 1.0 is the same. 0.5 to scale volume by
                         half. -1.0 to invert the data.
    @param use_src_header: True to use header from source file and skip
                           specifying channel, sample format, and rate for
                           source. False otherwise.
    @param use_dst_header: True to use header for dst file. False to treat
                           dst file as a raw file.

    """
    sox_cmd = [SOX_PATH]

    if not use_src_header:
        sox_cmd += _raw_format_args(channels_src, bits_src, rate_src)
    sox_cmd += ['-v', '%f' % volume_scale]
    sox_cmd += [path_src]

    if not use_dst_header:
        sox_cmd += _raw_format_args(channels_dst, bits_dst, rate_dst)
    else:
        sox_cmd += _format_args(channels_dst, bits_dst, rate_dst)
    sox_cmd += [path_dst]

    cmd_utils.execute(sox_cmd)


def lowpass_filter(path_src, channels_src, bits_src, rate_src,
                   path_dst, frequency):
    """Passes a raw file to a lowpass filter.

    @param path_src: The path to the source file.
    @param channels_src: The channel number of the source file.
    @param bits_src: The size of sample in bits of the source file.
    @param rate_src: The sampling rate of the source file.
    @param path_dst: The path to the destination file.
    @param frequency: A float for frequency used in sox command. The 3dB
                      frequency of the lowpass filter. Checks manual of sox
                      command for detail.

    """
    sox_cmd = [SOX_PATH]
    sox_cmd += _raw_format_args(channels_src, bits_src, rate_src)
    sox_cmd += [path_src]
    sox_cmd += _raw_format_args(channels_src, bits_src, rate_src)
    sox_cmd += [path_dst]
    sox_cmd += ['lowpass', '-2', str(frequency)]
    cmd_utils.execute(sox_cmd)


def trim_silence_from_wav_file(path_src,
                               path_dst,
                               new_duration,
                               volume=1,
                               duration_threshold=0.1):
    """Trim silence from beginning of a file.

    Trim silence from beginning of file, and trim remaining audio to
    new_duration seconds in length.

    @param path_src: The path to the source file.
    @oaram path_dst: The path to the destination file.
    @param new_duration: The new duration of the destination file in seconds.
    @param volume: [Optional] A float indicating the volume in percent, below
                   which sox will consider silence, defaults to 1 (1%).
    @param duration_threshold: [Optional] A float of the duration in seconds of
                               sound above volume parameter required to consider
                               end of silence. Defaults to 0.1 (0.1 seconds).
    """
    mins, secs = divmod(new_duration, 60)
    hrs, mins = divmod(mins, 60)
    length_str = '{:d}:{:02d}:{:.3f}'.format(int(hrs), int(mins), float(secs))

    sox_cmd = [SOX_PATH]
    sox_cmd += ['-G', path_src, path_dst]
    sox_cmd += ['silence', '1', str(duration_threshold), '{}%'.format(volume)]
    sox_cmd += ['trim', '0', length_str]

    cmd_utils.execute(sox_cmd)


def mix_two_wav_files(path_src1, path_src2, path_dst, input_volume=None):
    """Generate the mixed WAV file from two input WAV files.

    Use "man sox" for more details on the mixing.

    @param path_src1: Path to the first source.
    @param path_src2: Path to the second source.
    @param path_dst: Path for the generated mixed file.
    @param input_volume: The volume (0.0~1.0) of input sources on mixing. If not
                         given, the default value for sox is 1 / (# of sources).
    """
    sox_cmd = [SOX_PATH]
    sox_cmd += ['--combine', 'mix']

    if isinstance(input_volume, (int, float)):
        input_volume = min(1.0, max(0.0, input_volume))
        sox_cmd += ['-v', '{:.3f}'.format(input_volume)]

    sox_cmd += [path_src1, path_src2, path_dst]

    cmd_utils.execute(sox_cmd)


def get_infos_from_wav_file(file_path):
    """Get the information set from the header of the input WAV file.

    It returns None if the input file is not WAV format.

    @param file_path: Path to the WAV file.

    @returns: A dict with the following elements:
        'duration': The length of the audio (in seconds).
        'channels': The number of channels.
        'bits': The number of bits of each sample.
        'rate': The sampling rate.
    """
    sox_cmd = [SOX_PATH]
    sox_cmd += ['--i', None, file_path]  # sox_cmd[2] is placeholder

    def _execute_sox_cmd_info(info_arg):
        sox_cmd_info = sox_cmd[:2] + [info_arg] + sox_cmd[3:]
        return cmd_utils.execute(
                sox_cmd_info, stdout=subprocess.PIPE).decode('utf-8').strip()

    format_output = _execute_sox_cmd_info('-t')
    if format_output != 'wav':
        logging.error('the input file format: %s', format_output)
        return None

    return dict(duration=float(_execute_sox_cmd_info('-D')),
                channels=int(_execute_sox_cmd_info('-c')),
                bits=int(_execute_sox_cmd_info('-b')),
                rate=int(_execute_sox_cmd_info('-r')))


def get_file_length(file_path, channels, bits, rate):
    """Get the length in seconds of an audio file.

    @param file_path: Path to audio file.
    @param channels: The number of channels.
    @param bits: The number of bits of each sample.
    @param rate: The sampling rate.

    @returns: float length in seconds
    """
    return get_stat(file_path, channels, bits, rate).length
