#!/usr/bin/env python3
#
#   Copyright 2019 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import logging
import time

import os

import acts_contrib.test_utils.power.PowerBaseTest as PBT

from acts import base_test
from acts.controllers import monsoon
from bokeh.layouts import column, layout
from bokeh.models import CustomJS, ColumnDataSource
from bokeh.models import tools as bokeh_tools
from bokeh.models.widgets import DataTable, TableColumn
from bokeh.plotting import figure, output_file, save
from acts.controllers.monsoon_lib.api.common import PassthroughStates
from acts.controllers.monsoon_lib.api.common import MonsoonError

LOGTIME_RETRY_COUNT = 3
RESET_BATTERY_STATS = 'dumpsys batterystats --reset'
RECOVER_MONSOON_RETRY_COUNT = 3
MONSOON_RETRY_INTERVAL = 300

class PowerGnssBaseTest(PBT.PowerBaseTest):
    """
    Base Class for all GNSS Power related tests
    """

    def setup_class(self):
        super().setup_class()
        req_params = ['customjsfile', 'maskfile', 'dpooff_nv_dict',
                      'dpoon_nv_dict', 'mdsapp', 'modemparfile']
        self.unpack_userparams(req_params)

    def collect_power_data(self):
        """Measure power and plot."""
        samples = super().collect_power_data()
        plot_title = '{}_{}_{}_Power'.format(self.test_name, self.dut.model,
                                             self.dut.build_info['build_id'])
        self.monsoon_data_plot_power(samples, self.mon_voltage,
                                     self.mon_info.data_path, plot_title)
        return samples

    def monsoon_data_plot_power(self, samples, voltage, dest_path, plot_title):
        """Plot the monsoon power data using bokeh interactive plotting tool.

        Args:
            samples: a list of tuples in which the first element is a timestamp
            and the second element is the sampled current at that time
            voltage: the voltage that was used during the measurement
            dest_path: destination path
            plot_title: a filename and title for the plot.

        """

        logging.info('Plotting the power measurement data.')

        time_relative = [sample[0] for sample in samples]
        duration = time_relative[-1] - time_relative[0]
        current_data = [sample[1] * 1000 for sample in samples]
        avg_current = sum(current_data) / len(current_data)

        power_data = [current * voltage for current in current_data]

        color = ['navy'] * len(samples)

        # Preparing the data and source link for bokehn java callback
        source = ColumnDataSource(
            data=dict(x0=time_relative, y0=power_data, color=color))
        s2 = ColumnDataSource(
            data=dict(
                z0=[duration],
                y0=[round(avg_current, 2)],
                x0=[round(avg_current * voltage, 2)],
                z1=[round(avg_current * voltage * duration, 2)],
                z2=[round(avg_current * duration, 2)]))
        # Setting up data table for the output
        columns = [
            TableColumn(field='z0', title='Total Duration (s)'),
            TableColumn(field='y0', title='Average Current (mA)'),
            TableColumn(field='x0', title='Average Power (4.2v) (mW)'),
            TableColumn(field='z1', title='Average Energy (mW*s)'),
            TableColumn(field='z2', title='Normalized Average Energy (mA*s)')
        ]
        dt = DataTable(
            source=s2, columns=columns, width=1300, height=60, editable=True)

        output_file(os.path.join(dest_path, plot_title + '.html'))
        tools = 'box_zoom,box_select,pan,crosshair,redo,undo,reset,hover,save'
        # Create a new plot with the datatable above
        plot = figure(
            plot_width=1300,
            plot_height=700,
            title=plot_title,
            tools=tools,
            output_backend='webgl')
        plot.add_tools(bokeh_tools.WheelZoomTool(dimensions='width'))
        plot.add_tools(bokeh_tools.WheelZoomTool(dimensions='height'))
        plot.line('x0', 'y0', source=source, line_width=2)
        plot.circle('x0', 'y0', source=source, size=0.5, fill_color='color')
        plot.xaxis.axis_label = 'Time (s)'
        plot.yaxis.axis_label = 'Power (mW)'
        plot.title.text_font_size = {'value': '15pt'}
        jsscript = open(self.customjsfile, 'r')
        customjsscript = jsscript.read()
        # Callback Java scripting
        source.callback = CustomJS(
            args=dict(mytable=dt),
            code=customjsscript)

        # Layout the plot and the datatable bar
        save(layout([[dt], [plot]]))

    def disconnect_usb(self, ad, sleeptime):
        """Disconnect usb while device is on sleep and
        connect the usb again once the sleep time completes

        sleeptime: sleep time where dut is disconnected from usb
        """
        self.dut.adb.shell(RESET_BATTERY_STATS)
        time.sleep(1)
        for _ in range(LOGTIME_RETRY_COUNT):
            self.monsoons[0].usb(PassthroughStates.OFF)
            if not ad.is_connected():
                time.sleep(sleeptime)
                self.monsoons[0].usb(PassthroughStates.ON)
                break
        else:
            self.log.error('Test failed after maximum retry')
            for _ in range(RECOVER_MONSOON_RETRY_COUNT):
                if self.monsoon_recover():
                    break
                else:
                    self.log.warning(
                        'Wait for {} second then try again'.format(
                            MONSOON_RETRY_INTERVAL))
                    time.sleep(MONSOON_RETRY_INTERVAL)
            else:
                self.log.error('Failed to recover monsoon')

    def monsoon_recover(self):
        """Test loop to wait for monsoon recover from unexpected error.

        Wait for a certain time duration, then quit.0
        Args:
            mon: monsoon object
        Returns:
            True/False
        """
        try:
            self.power_monitor.connect_usb()
            logging.info('Monsoon recovered from unexpected error')
            time.sleep(2)
            return True
        except MonsoonError:
            try:
                self.log.info(self.monsoons[0]._mon.ser.in_waiting)
            except AttributeError:
                # This attribute does not exist for HVPMs.
                pass
            logging.warning('Unable to recover monsoon from unexpected error')
            return False
