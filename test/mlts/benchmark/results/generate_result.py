#!/usr/bin/python3
#
# Copyright 2018, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""MLTS benchmark result generator.

Reads a CSV produced by MLTS benchmark and generates
an HTML page with results summary.

Usage:
  generate_result [csv input file] [html output file]
"""

import argparse
import collections
import csv
import os
import re
import math


class ScoreException(Exception):
  """Generator base exception type. """
  pass


LatencyResult = collections.namedtuple(
    'LatencyResult',
    ['iterations', 'total_time_sec', 'time_freq_start_sec', 'time_freq_step_sec', 'time_freq_sec'])


COMPILATION_TYPES = ['compile_without_cache', 'save_to_cache', 'prepare_from_cache']
BASELINE_COMPILATION_TYPE = COMPILATION_TYPES[0]
CompilationResult = collections.namedtuple(
    'CompilationResult',
    ['cache_size_bytes'] + COMPILATION_TYPES)


BenchmarkResult = collections.namedtuple(
    'BenchmarkResult',
    ['name', 'backend_type', 'inference_latency', 'max_single_error',
     'testset_size', 'evaluator_keys', 'evaluator_values', 'validation_errors',
     'compilation_results'])


ResultsWithBaseline = collections.namedtuple(
    'ResultsWithBaseline',
    ['baseline', 'other'])


BASELINE_BACKEND = 'TFLite_CPU'
KNOWN_GROUPS = [
    (re.compile('mobilenet_v1.*quant.*'), 'MobileNet v1 Quantized'),
    (re.compile('mobilenet_v1.*'), 'MobileNet v1 Float'),
    (re.compile('mobilenet_v2.*quant.*'), 'MobileNet v2 Quantized'),
    (re.compile('mobilenet_v2.*'), 'MobileNet v2 Float'),
    (re.compile('mobilenet_v3.*uint8.*'), 'MobileNet v3 Quantized'),
    (re.compile('mobilenet_v3.*'), 'MobileNet v3 Float'),
    (re.compile('tts.*'), 'LSTM Text-to-speech'),
    (re.compile('asr.*'), 'LSTM Automatic Speech Recognition'),
]


class BenchmarkResultParser:
  """A helper class to parse the input CSV file."""

  def __init__(self, csvfile):
    self.csv_reader = csv.reader(filter(lambda row: row[0] != '#', csvfile))
    self.row = None
    self.index = 0

  def next(self):
    """Advance to the next row, returns the current row or None if reaches the end."""
    try:
      self.row = next(self.csv_reader)
    except StopIteration:
      self.row = None
    finally:
      self.index = 0
      return self.row

  def read_boolean(self):
    """Read the next CSV cell as a boolean."""
    s = self.read_typed(str).lower()
    if s == 'true':
      return True
    elif s == 'false':
      return False
    else:
      raise ValueError('Cannot convert \'%s\' to a boolean' % s)

  def read_typed(self, Type):
    """Read the next CSV cell as the given type."""
    if Type is bool:
      return self.read_boolean()
    entry = self.row[self.index]
    self.index += 1
    return Type(entry)

  def read_typed_array(self, Type, length):
    """Read the next CSV cells as a typed array."""
    return [self.read_typed(Type) for _ in range(length)]

  def read_latency_result(self):
    """Read the next CSV cells as a LatencyResult."""
    result = {}
    result['iterations'] = self.read_typed(int)
    result['total_time_sec'] = self.read_typed(float)
    result['time_freq_start_sec'] = self.read_typed(float)
    result['time_freq_step_sec'] = self.read_typed(float)
    time_freq_sec_count = self.read_typed(int)
    result['time_freq_sec'] = self.read_typed_array(float, time_freq_sec_count)
    return LatencyResult(**result)

  def read_compilation_result(self):
    """Read the next CSV cells as a CompilationResult."""
    result = {}
    for compilation_type in COMPILATION_TYPES:
      has_results = self.read_typed(bool)
      result[compilation_type] = self.read_latency_result() if has_results else None
    result['cache_size_bytes'] = self.read_typed(int)
    return CompilationResult(**result)

  def read_benchmark_result(self):
    """Read the next CSV cells as a BenchmarkResult."""
    result = {}
    result['name'] = self.read_typed(str)
    result['backend_type'] = self.read_typed(str)
    result['inference_latency'] = self.read_latency_result()
    result['max_single_error'] = self.read_typed(float)
    result['testset_size'] = self.read_typed(int)
    evaluator_keys_count = self.read_typed(int)
    validation_error_count = self.read_typed(int)
    result['evaluator_keys'] = self.read_typed_array(str, evaluator_keys_count)
    result['evaluator_values'] = self.read_typed_array(float, evaluator_keys_count)
    result['validation_errors'] = self.read_typed_array(str, validation_error_count)
    result['compilation_results'] = self.read_compilation_result()
    return BenchmarkResult(**result)


def parse_csv_input(input_filename):
  """Parse input CSV file, returns: (benchmarkInfo, list of BenchmarkResult)."""
  with open(input_filename, 'r') as csvfile:
    parser = BenchmarkResultParser(csvfile)

    # First line contain device info
    benchmark_info = parser.next()

    results = []
    while parser.next():
      results.append(parser.read_benchmark_result())

    return (benchmark_info, results)


def group_results(results):
  """Group list of results by their name/backend, returns list of lists."""
  # Group by name
  groupings = collections.defaultdict(list)
  for result in results:
    groupings[result.name].append(result)

  # Find baseline for each group, make ResultsWithBaseline for each name
  groupings_baseline = {}
  for name, results in groupings.items():
    baseline = next(filter(lambda x: x.backend_type == BASELINE_BACKEND,
                           results))
    other = sorted(filter(lambda x: x is not baseline, results),
                   key=lambda x: x.backend_type)
    groupings_baseline[name] = ResultsWithBaseline(
        baseline=baseline,
        other=other)

  # Merge ResultsWithBaseline for known groups
  known_groupings_baseline = collections.defaultdict(list)
  for name, results_with_bl in sorted(groupings_baseline.items()):
    group_name = name
    for known_group in KNOWN_GROUPS:
      if known_group[0].match(results_with_bl.baseline.name):
        group_name = known_group[1]
        break
    known_groupings_baseline[group_name].append(results_with_bl)

  # Turn into a list sorted by name
  groupings_list = []
  for name, results_wbl in sorted(known_groupings_baseline.items()):
    groupings_list.append((name, results_wbl))
  return groupings_list


def get_frequency_graph_min_max(latencies):
  """Get min and max times of latencies frequency."""
  mins = []
  maxs = []
  for latency in latencies:
    mins.append(latency.time_freq_start_sec)
    to_add = len(latency.time_freq_sec) * latency.time_freq_step_sec
    maxs.append(latency.time_freq_start_sec + to_add)
  return min(mins), max(maxs)


def get_frequency_graph(time_freq_start_sec, time_freq_step_sec, time_freq_sec,
                        start_sec, end_sec):
  """Generate input x/y data for latency frequency graph."""
  left_to_pad = (int((time_freq_start_sec - start_sec) / time_freq_step_sec)
                 if time_freq_step_sec != 0
                 else math.inf)
  end_time = time_freq_start_sec + len(time_freq_sec) * time_freq_step_sec
  right_to_pad = (int((end_sec - end_time) / time_freq_step_sec)
                  if time_freq_step_sec != 0
                  else math.inf)

  # After pading more that 64 values, graphs start to look messy,
  # bail out in that case.
  if (left_to_pad + right_to_pad) < 64:
    left_pad = (['{:.2f}ms'.format(
        (start_sec + x * time_freq_step_sec) * 1000.0)
                 for x in range(left_to_pad)], [0] * left_to_pad)

    right_pad = (['{:.2f}ms'.format(
        (end_time + x * time_freq_step_sec) * 1000.0)
                  for x in range(right_to_pad)], [0] * right_to_pad)
  else:
    left_pad = [[], []]
    right_pad = [[], []]

  data = (['{:.2f}ms'.format(
      (time_freq_start_sec + x * time_freq_step_sec) * 1000.0)
           for x in range(len(time_freq_sec))], time_freq_sec)

  return (left_pad[0] + data[0] + right_pad[0],
          left_pad[1] + data[1] + right_pad[1])


def is_topk_evaluator(evaluator_keys):
  """Are these evaluator keys from TopK evaluator?"""
  return (len(evaluator_keys) == 5 and
          evaluator_keys[0] == 'top_1' and
          evaluator_keys[1] == 'top_2' and
          evaluator_keys[2] == 'top_3' and
          evaluator_keys[3] == 'top_4' and
          evaluator_keys[4] == 'top_5')


def is_melceplogf0_evaluator(evaluator_keys):
  """Are these evaluator keys from MelCepLogF0 evaluator?"""
  return (len(evaluator_keys) == 2 and
          evaluator_keys[0] == 'max_mel_cep_distortion' and
          evaluator_keys[1] == 'max_log_f0_error')


def is_phone_error_rate_evaluator(evaluator_keys):
  """Are these evaluator keys from PhoneErrorRate evaluator?"""
  return (len(evaluator_keys) == 1 and
          evaluator_keys[0] == 'max_phone_error_rate')


def generate_accuracy_headers(result):
  """Accuracy-related headers for result table."""
  if is_topk_evaluator(result.evaluator_keys):
    return ACCURACY_HEADERS_TOPK_TEMPLATE
  elif is_melceplogf0_evaluator(result.evaluator_keys):
    return ACCURACY_HEADERS_MELCEPLOGF0_TEMPLATE
  elif is_phone_error_rate_evaluator(result.evaluator_keys):
    return ACCURACY_HEADERS_PHONE_ERROR_RATE_TEMPLATE
  else:
    return ACCURACY_HEADERS_BASIC_TEMPLATE
  raise ScoreException('Unknown accuracy headers for: ' + str(result))


def get_diff_span(value, same_delta, positive_is_better):
  if abs(value) < same_delta:
    return 'same'
  if positive_is_better and value > 0 or not positive_is_better and value < 0:
    return 'better'
  return 'worse'


def generate_accuracy_values(baseline, result):
  """Accuracy-related data for result table."""
  if is_topk_evaluator(result.evaluator_keys):
    val = [float(x) * 100.0 for x in result.evaluator_values]
    if result is baseline:
      topk = [TOPK_BASELINE_TEMPLATE.format(val=x) for x in val]
      return ACCURACY_VALUES_TOPK_TEMPLATE.format(
          top1=topk[0], top2=topk[1], top3=topk[2], top4=topk[3],
          top5=topk[4]
      )
    else:
      base = [float(x) * 100.0 for x in baseline.evaluator_values]
      diff = [a - b for a, b in zip(val, base)]
      topk = [TOPK_DIFF_TEMPLATE.format(
          val=v, diff=d, span=get_diff_span(d, 1.0, positive_is_better=True))
              for v, d in zip(val, diff)]
      return ACCURACY_VALUES_TOPK_TEMPLATE.format(
          top1=topk[0], top2=topk[1], top3=topk[2], top4=topk[3],
          top5=topk[4]
      )
  elif is_melceplogf0_evaluator(result.evaluator_keys):
    val = [float(x) for x in
           result.evaluator_values + [result.max_single_error]]
    if result is baseline:
      return ACCURACY_VALUES_MELCEPLOGF0_TEMPLATE.format(
          max_log_f0=MELCEPLOGF0_BASELINE_TEMPLATE.format(
              val=val[0]),
          max_mel_cep_distortion=MELCEPLOGF0_BASELINE_TEMPLATE.format(
              val=val[1]),
          max_single_error=MELCEPLOGF0_BASELINE_TEMPLATE.format(
              val=val[2]),
      )
    else:
      base = [float(x) for x in
              baseline.evaluator_values + [baseline.max_single_error]]
      diff = [a - b for a, b in zip(val, base)]
      v = [MELCEPLOGF0_DIFF_TEMPLATE.format(
          val=v, diff=d, span=get_diff_span(d, 1.0, positive_is_better=False))
           for v, d in zip(val, diff)]
      return ACCURACY_VALUES_MELCEPLOGF0_TEMPLATE.format(
          max_log_f0=v[0],
          max_mel_cep_distortion=v[1],
          max_single_error=v[2],
      )
  elif is_phone_error_rate_evaluator(result.evaluator_keys):
    val = [float(x) for x in
           result.evaluator_values + [result.max_single_error]]
    if result is baseline:
      return ACCURACY_VALUES_PHONE_ERROR_RATE_TEMPLATE.format(
          max_phone_error_rate=PHONE_ERROR_RATE_BASELINE_TEMPLATE.format(
              val=val[0]),
          max_single_error=PHONE_ERROR_RATE_BASELINE_TEMPLATE.format(
              val=val[1]),
      )
    else:
      base = [float(x) for x in
              baseline.evaluator_values + [baseline.max_single_error]]
      diff = [a - b for a, b in zip(val, base)]
      v = [PHONE_ERROR_RATE_DIFF_TEMPLATE.format(
          val=v, diff=d, span=get_diff_span(d, 1.0, positive_is_better=False))
           for v, d in zip(val, diff)]
      return ACCURACY_VALUES_PHONE_ERROR_RATE_TEMPLATE.format(
          max_phone_error_rate=v[0],
          max_single_error=v[1],
      )
  else:
    return ACCURACY_VALUES_BASIC_TEMPLATE.format(
        max_single_error=result.max_single_error,
    )
  raise ScoreException('Unknown accuracy values for: ' + str(result))


def getchartjs_source():
  return open(os.path.dirname(os.path.abspath(__file__)) + '/' +
              CHART_JS_FILE).read()


def generate_avg_ms(baseline, latency):
  """Generate average latency value."""
  if latency is None:
    latency = baseline

  result_avg_ms = (latency.total_time_sec / latency.iterations)*1000.0
  if latency is baseline:
    return LATENCY_BASELINE_TEMPLATE.format(val=result_avg_ms)
  baseline_avg_ms = (baseline.total_time_sec / baseline.iterations)*1000.0
  diff = (result_avg_ms/baseline_avg_ms - 1.0) * 100.0
  diff_val = result_avg_ms - baseline_avg_ms
  return LATENCY_DIFF_TEMPLATE.format(
      val=result_avg_ms,
      diff=diff,
      diff_val=diff_val,
      span=get_diff_span(diff, same_delta=1.0, positive_is_better=False))


def generate_result_entry(baseline, result):
  if result is None:
    result = baseline

  return RESULT_ENTRY_TEMPLATE.format(
      row_class='failed' if result.validation_errors else 'normal',
      name=result.name,
      backend=result.backend_type,
      iterations=result.inference_latency.iterations,
      testset_size=result.testset_size,
      accuracy_values=generate_accuracy_values(baseline, result),
      avg_ms=generate_avg_ms(baseline.inference_latency, result.inference_latency))


def generate_latency_graph_entry(tag, latency, tmin, tmax):
  """Generate a single latency graph."""
  return LATENCY_GRAPH_ENTRY_TEMPLATE.format(
      tag=tag,
      i=id(latency),
      freq_data=get_frequency_graph(latency.time_freq_start_sec,
                                    latency.time_freq_step_sec,
                                    latency.time_freq_sec,
                                    tmin, tmax))


def generate_latency_graphs_group(tags, latencies):
  """Generate a group of latency graphs with the same tmin and tmax."""
  tmin, tmax = get_frequency_graph_min_max(latencies)
  return ''.join(
      generate_latency_graph_entry(tag, latency, tmin, tmax)
      for tag, latency in zip(tags, latencies))


def snake_case_to_title(string):
  return string.replace('_', ' ').title()


def generate_inference_latency_graph_entry(results_with_bl):
  """Generate a group of latency graphs for inference latencies."""
  results = [results_with_bl.baseline] + results_with_bl.other
  tags = [result.backend_type for result in results]
  latencies = [result.inference_latency for result in results]
  return generate_latency_graphs_group(tags, latencies)


def generate_compilation_latency_graph_entry(results_with_bl):
  """Generate a group of latency graphs for compilation latencies."""
  tags = [
      result.backend_type + ', ' + snake_case_to_title(type)
      for result in results_with_bl.other
      for type in COMPILATION_TYPES
      if getattr(result.compilation_results, type)
  ]
  latencies = [
      getattr(result.compilation_results, type)
      for result in results_with_bl.other
      for type in COMPILATION_TYPES
      if getattr(result.compilation_results, type)
  ]
  return generate_latency_graphs_group(tags, latencies)


def generate_validation_errors(entries_group):
  """Generate validation errors table."""
  errors = []
  for result_and_bl in entries_group:
    for result in [result_and_bl.baseline] + result_and_bl.other:
      for error in result.validation_errors:
        errors.append((result.name, result.backend_type, error))

  if errors:
    return VALIDATION_ERRORS_TEMPLATE.format(
        results=''.join(
            VALIDATION_ERRORS_ENTRY_TEMPLATE.format(
                name=name,
                backend=backend,
                error=error) for name, backend, error in errors))
  return ''


def generate_compilation_result_entry(result):
  format_args = {
      'row_class':
          'failed' if result.validation_errors else 'normal',
      'name':
          result.name,
      'backend':
          result.backend_type,
      'cache_size':
          f'{result.compilation_results.cache_size_bytes:,}'
          if result.compilation_results.cache_size_bytes > 0 else '-'
  }
  for compilation_type in COMPILATION_TYPES:
    latency = getattr(result.compilation_results, compilation_type)
    if latency:
      format_args[compilation_type + '_iterations'] = f'{latency.iterations}'
      format_args[compilation_type + '_avg_ms'] = generate_avg_ms(
          result.compilation_results.compile_without_cache, latency)
    else:
      format_args[compilation_type + '_iterations'] = '-'
      format_args[compilation_type + '_avg_ms'] = '-'
  return COMPILATION_RESULT_ENTRY_TEMPLATE.format(**format_args)


def generate_result(benchmark_info, data):
  """Turn list of results into HTML."""
  return MAIN_TEMPLATE.format(
      jsdeps=getchartjs_source(),
      device_info=DEVICE_INFO_TEMPLATE.format(
          benchmark_time=benchmark_info[0],
          device_info=benchmark_info[1],
          ),
      results_list=''.join((
          RESULT_GROUP_TEMPLATE.format(
              group_name=entries_name,
              accuracy_headers=generate_accuracy_headers(
                  entries_group[0].baseline),
              results=''.join(
                  RESULT_ENTRY_WITH_BASELINE_TEMPLATE.format(
                      baseline=generate_result_entry(
                          result_and_bl.baseline, None),
                      other=''.join(
                          generate_result_entry(
                              result_and_bl.baseline, x)
                          for x in result_and_bl.other)
                  ) for result_and_bl in entries_group),
              validation_errors=generate_validation_errors(entries_group),
              latency_graphs=LATENCY_GRAPHS_TEMPLATE.format(
                  results=''.join(
                      LATENCY_GRAPH_ENTRY_GROUP_TEMPLATE.format(
                          name=result_and_bl.baseline.name,
                          results=generate_inference_latency_graph_entry(result_and_bl)
                      ) for result_and_bl in entries_group)
              ),
              compilation_results=''.join(
                  COMPILATION_RESULT_ENTRIES_TEMPLATE.format(
                      entries=''.join(
                          generate_compilation_result_entry(x) for x in result_and_bl.other)
                  ) for result_and_bl in entries_group),
              compilation_latency_graphs=LATENCY_GRAPHS_TEMPLATE.format(
                  results=''.join(
                      LATENCY_GRAPH_ENTRY_GROUP_TEMPLATE.format(
                          name=result_and_bl.baseline.name,
                          results=generate_compilation_latency_graph_entry(result_and_bl)
                      ) for result_and_bl in entries_group)
              ),
          ) for entries_name, entries_group in group_results(data))
                          ))


def main():
  parser = argparse.ArgumentParser()
  parser.add_argument('input', help='input csv filename')
  parser.add_argument('output', help='output html filename')
  args = parser.parse_args()

  benchmark_info, data = parse_csv_input(args.input)

  with open(args.output, 'w') as htmlfile:
    htmlfile.write(generate_result(benchmark_info, data))


# -----------------
# Templates below

MAIN_TEMPLATE = """<!doctype html>
<html lang='en-US'>
<head>
  <meta http-equiv='Content-Type' content='text/html; charset=utf-8'>
  <script src='https://ajax.googleapis.com/ajax/libs/jquery/3.3.1/jquery.min.js'></script>
  <script>{jsdeps}</script>
  <title>MLTS results</title>
  <style>
    .results {{
      border-collapse: collapse;
      width: 100%;
    }}
    .results td, .results th {{
      border: 1px solid #ddd;
      padding: 6px;
    }}
    .results tbody.values {{
      border-bottom: 8px solid #333;
    }}
    span.better {{
      color: #070;
    }}
    span.worse {{
      color: #700;
    }}
    span.same {{
      color: #000;
    }}
    .results tr:nth-child(even) {{background-color: #eee;}}
    .results tr:hover {{background-color: #ddd;}}
    .results th {{
      padding: 10px;
      font-weight: bold;
      text-align: left;
      background-color: #333;
      color: white;
    }}
    .results tr.failed {{
      background-color: #ffc4ca;
    }}
    .group {{
      padding-top: 25px;
    }}
    .group_name {{
      padding-left: 10px;
      font-size: 140%;
      font-weight: bold;
    }}
    .section_name {{
      padding: 10px;
      font-size: 120%;
      font-weight: bold;
    }}
    .latency_results {{
       padding: 10px;
       border: 1px solid #ddd;
       overflow: hidden;
    }}
    .latency_with_baseline {{
       padding: 10px;
       border: 1px solid #ddd;
       overflow: hidden;
    }}
  </style>
</head>
<body>
{device_info}
{results_list}
</body>
</html>"""

DEVICE_INFO_TEMPLATE = """<div id='device_info'>
Benchmark for {device_info}, started at {benchmark_time}
</div>"""


RESULT_GROUP_TEMPLATE = """<div class="group">
<div class="group_name">{group_name}</div>
<div class="section_name">Inference results</div>
<table class="results">
 <tr>
   <th>Name</th>
   <th>Backend</th>
   <th>Iterations</th>
   <th>Test set size</th>
   <th>Average latency ms</th>
   {accuracy_headers}
 </tr>
 {results}
</table>
{validation_errors}
{latency_graphs}
<div class="section_name">Compilation results</div>
<table class="results">
 <tr>
   <th rowspan="2">Name</th>
   <th rowspan="2">Backend</th>
   <th colspan="2">Compile Without Cache</th>
   <th colspan="2">Save To Cache</th>
   <th colspan="2">Prepare From Cache</th>
   <th rowspan="2">Cache size bytes</th>
 </tr>
 <tr>
   <th>Iterations</th>
   <th>Average latency ms</th>
   <th>Iterations</th>
   <th>Average latency ms</th>
   <th>Iterations</th>
   <th>Average latency ms</th>
 </tr>
 {compilation_results}
</table>
{compilation_latency_graphs}
</div>"""


VALIDATION_ERRORS_TEMPLATE = """
<table class="results">
 <tr>
   <th>Name</th>
   <th>Backend</th>
   <th>Error</th>
 </tr>
 {results}
</table>"""
VALIDATION_ERRORS_ENTRY_TEMPLATE = """
  <tr class="failed">
    <td>{name}</td>
    <td>{backend}</td>
    <td>{error}</td>
  </tr>
"""

LATENCY_GRAPHS_TEMPLATE = """
<div class="latency_results">
{results}
</div>
<div style="clear: left;"></div>
"""

LATENCY_GRAPH_ENTRY_GROUP_TEMPLATE = """
<div class="latency_with_baseline" style="float: left;">
<b>{name}</b>
{results}
</div>
"""

LATENCY_GRAPH_ENTRY_TEMPLATE = """
<div class="latency_result" style='width: 350px;'>
{tag}
<canvas id='latency_chart{i}' class='latency_chart'></canvas>
  <script>
   $(function() {{
       var freqData = {{
         labels: {freq_data[0]},
         datasets: [{{
            data: {freq_data[1]},
            backgroundColor: 'rgba(255, 99, 132, 0.6)',
            borderColor:  'rgba(255, 0, 0, 0.6)',
            borderWidth: 1,
         }}]
       }};
       var ctx = $('#latency_chart{i}')[0].getContext('2d');
       window.latency_chart{i} = new Chart(ctx,
        {{
          type: 'bar',
          data: freqData,
          options: {{
           responsive: true,
           title: {{
             display: false,
             text: 'Latency frequency'
           }},
           legend: {{
             display: false
           }},
           scales: {{
            xAxes: [ {{
              barPercentage: 1.0,
              categoryPercentage: 0.9,
            }}],
            yAxes: [{{
              scaleLabel: {{
                display: false,
                labelString: 'Iterations Count'
              }}
            }}]
           }}
         }}
       }});
     }});
    </script>
</div>
"""


RESULT_ENTRY_WITH_BASELINE_TEMPLATE = """
 <tbody class="values">
 {baseline}
 {other}
 </tbody>
"""
RESULT_ENTRY_TEMPLATE = """
  <tr class={row_class}>
   <td>{name}</td>
   <td>{backend}</td>
   <td>{iterations:d}</td>
   <td>{testset_size:d}</td>
   <td>{avg_ms}</td>
   {accuracy_values}
  </tr>"""

COMPILATION_RESULT_ENTRIES_TEMPLATE = """
 <tbody class="values">
 {entries}
 </tbody>
"""
COMPILATION_RESULT_ENTRY_TEMPLATE = """
  <tr class={row_class}>
   <td>{name}</td>
   <td>{backend}</td>
   <td>{compile_without_cache_iterations}</td>
   <td>{compile_without_cache_avg_ms}</td>
   <td>{save_to_cache_iterations}</td>
   <td>{save_to_cache_avg_ms}</td>
   <td>{prepare_from_cache_iterations}</td>
   <td>{prepare_from_cache_avg_ms}</td>
   <td>{cache_size}</td>
  </tr>"""

LATENCY_BASELINE_TEMPLATE = """{val:.2f}ms"""
LATENCY_DIFF_TEMPLATE = """{val:.2f}ms <span class='{span}'>
({diff_val:.2f}ms, {diff:.1f}%)</span>"""


ACCURACY_HEADERS_TOPK_TEMPLATE = """
<th>Top 1</th>
<th>Top 2</th>
<th>Top 3</th>
<th>Top 4</th>
<th>Top 5</th>
"""
ACCURACY_VALUES_TOPK_TEMPLATE = """
<td>{top1}</td>
<td>{top2}</td>
<td>{top3}</td>
<td>{top4}</td>
<td>{top5}</td>
"""
TOPK_BASELINE_TEMPLATE = """{val:.3f}%"""
TOPK_DIFF_TEMPLATE = """{val:.3f}% <span class='{span}'>({diff:.1f}%)</span>"""


ACCURACY_HEADERS_MELCEPLOGF0_TEMPLATE = """
<th>Max log(F0) error</th>
<th>Max Mel Cep distortion</th>
<th>Max scalar error</th>
"""

ACCURACY_VALUES_MELCEPLOGF0_TEMPLATE = """
<td>{max_log_f0}</td>
<td>{max_mel_cep_distortion}</td>
<td>{max_single_error}</td>
"""

MELCEPLOGF0_BASELINE_TEMPLATE = """{val:.2E}"""
MELCEPLOGF0_DIFF_TEMPLATE = \
"""{val:.2E} <span class='{span}'>({diff:.1f}%)</span>"""


ACCURACY_HEADERS_PHONE_ERROR_RATE_TEMPLATE = """
<th>Max phone error rate</th>
<th>Max scalar error</th>
"""

ACCURACY_VALUES_PHONE_ERROR_RATE_TEMPLATE = """
<td>{max_phone_error_rate}</td>
<td>{max_single_error}</td>
"""

PHONE_ERROR_RATE_BASELINE_TEMPLATE = """{val:.3f}"""
PHONE_ERROR_RATE_DIFF_TEMPLATE = \
"""{val:.3f} <span class='{span}'>({diff:.1f}%)</span>"""


ACCURACY_HEADERS_BASIC_TEMPLATE = """
<th>Max single scalar error</th>
"""


ACCURACY_VALUES_BASIC_TEMPLATE = """
<td>{max_single_error:.2f}</td>
"""

CHART_JS_FILE = 'Chart.bundle.min.js'

if __name__ == '__main__':
  main()
