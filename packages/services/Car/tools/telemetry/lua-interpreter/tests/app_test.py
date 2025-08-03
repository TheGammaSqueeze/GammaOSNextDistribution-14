"""
    Test Script for Lua Interpreter Flask server.
"""

import unittest
import app
from bs4 import BeautifulSoup


class TestWebApp(unittest.TestCase):

  def setUp(self):
    """This method will be run before each of the test methods in the class."""
    self.client = app.app.test_client()

  def test_home_page_render(self):
    response = self.client.get('/')
    self.assertIn('Lua Telemetry Interpreter', str(response.data))

  def test_get_published_data_file_names_and_content(self):
    response = self.client.post('/get_published_data_file_names_and_content')
    data = response.get_json()['file_names']
    self.assertCountEqual([
        'activity_foreground_state_changed', 'anr_occurred',
        'app_start_memory_state_captured', 'app_crash_occurred',
        'connectivity_publisher', 'memory_publisher', 'process_cpu_time',
        'process_memory_snapshot', 'process_memory_state',
        'vehicle_property_publisher', 'wtf_occurred'
    ], data)
    data = response.get_json()['memory_publisher']
    self.assertIn('"mem.timestamp_millis": 1664995933733', data)

  def test_execute_script_output(self):
    response = self.client.post(
        '/execute_script',
        data={
            'script':
                'function test(data,state) ' \
                'tbl = {}; tbl[\'sessionId\'] = state.data + data.id;' \
                'on_metrics_report(tbl) end',
            'function-name': 'test',
            'published-data': '{"id": 2}',
            'saved-state': '{"data": 5}'
        })
    rendered_html = BeautifulSoup(response.data.decode('UTF-8'), 'html.parser')
    self.assertIn('{<br/>  "sessionId": 7<br/>}',
                  str(rendered_html.find(id='script-output')))

  def test_execute_script_loading_error(self):
    response = self.client.post(
        '/execute_script',
        data={
            'script': 'function test(data, state) x == 1 end',
            'function-name': 'test',
            'published-data': "{}",
            'saved-state': "{}"
        })
    rendered_html = BeautifulSoup(response.data.decode('UTF-8'), 'html.parser')
    span = rendered_html.find(id='script-output').find('span')
    self.assertIn(
        'Error encountered while loading the script. A possible cause could ' \
        'be syntax errors in the script.',
        str(span))

  def test_execute_script_running_error(self):
    response = self.client.post(
        '/execute_script',
        data={
            'script': 'function test(data, state) call() end',
            'function-name': 'test',
            'published-data': "{}",
            'saved-state': "{}"
        })
    rendered_html = BeautifulSoup(response.data.decode('UTF-8'), 'html.parser')
    span = rendered_html.find(id='script-output').find('span')
    self.assertIn('Error encountered while running the script.', str(span))

  def test_execute_script_running_error_with_stacktrace(self):
    response = self.client.post(
        '/execute_script',
        data={
            'script': 'function func_1(data, state) func_2() end function func_2() func_3() end',
            'function-name': 'func_1',
            'published-data': "{}",
            'saved-state': "{}"
        })
    rendered_html = BeautifulSoup(response.data.decode('UTF-8'), 'html.parser')
    span = rendered_html.find(id='script-output').find('span')
    self.assertIn('Error encountered while running the script.', str(span))
    self.assertIn('func_3', str(span))
    self.assertIn('func_2', str(span))
    self.assertIn('func_1', str(span))

  def test_execute_script_saved_state_unchanged(self):
    response = self.client.post(
        '/execute_script',
        data={
            'script': 'function test(data, state) log(2) end',
            'function-name': 'test',
            'published-data': "{}",
            'saved-state': '{"test": "state"}'
        })
    rendered_html = BeautifulSoup(response.data.decode('UTF-8'), 'html.parser')
    span = rendered_html.find(id='saved-state-input').getText()
    self.assertIn('{"test": "state"}', str(span))

  def test_execute_script_saved_state_changed(self):
    response = self.client.post(
        '/execute_script',
        data={
            'script': 'function test(data, state) on_success(data) end',
            'function-name': 'test',
            'published-data': '{"test": "data"}',
            'saved-state': '{"test": "state"}'
        })
    rendered_html = BeautifulSoup(response.data.decode('UTF-8'), 'html.parser')
    span = rendered_html.find(id='saved-state-input').getText()
    self.assertIn('{\n  "test": "data"\n}', str(span))

  def test_execute_script_faulty_published_data(self):
    response = self.client.post('/execute_script',
                                data={
                                    'script': 'function test(data, state) end',
                                    'function-name': 'test',
                                    'published-data': "",
                                    'saved-state': "{}"
                                })
    rendered_html = BeautifulSoup(response.data.decode('UTF-8'), 'html.parser')
    span = rendered_html.find(id='script-output').find('span')
    self.assertIn('Error from parsing published data', str(span))

  def test_execute_script_faulty_saved_state(self):
    response = self.client.post('/execute_script',
                                data={
                                    'script': 'function test(data, state) end',
                                    'function-name': 'test',
                                    'published-data': "{}",
                                    'saved-state': "f"
                                })
    rendered_html = BeautifulSoup(response.data.decode('UTF-8'), 'html.parser')
    span = rendered_html.find(id='script-output').find('span')
    self.assertIn('Error from parsing saved state', str(span))

  def test_execute_script_wrong_function(self):
    response = self.client.post('/execute_script',
                                data={
                                    'script': 'function test(data, state) end',
                                    'function-name': 'tes',
                                    'published-data': "{}",
                                    'saved-state': "{}"
                                })
    rendered_html = BeautifulSoup(response.data.decode('UTF-8'), 'html.parser')
    span = rendered_html.find(id='script-output').find('span')
    self.assertIn('Wrong function name.', str(span))

  def test_prettify_json_success(self):
    self.assertEqual('{\n  "test": 2\n}', app.prettify_json('{"test":2}'))

  def test_prettify_json_failure(self):
    self.assertEqual('not_a_json', 'not_a_json')


if __name__ == '__main__':
  unittest.main()