#!/usr/bin/env python3
#
#   Copyright 2020 Google, Inc.
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

import datetime
import logging
import math
import numpy
import os

from bokeh.models import tools as bokeh_tools
from bokeh.models import CustomJS, ColumnDataSource
from bokeh.models.widgets import DataTable, TableColumn
from bokeh.models.formatters import DatetimeTickFormatter
from bokeh.plotting import figure, output_file, save
from bokeh.layouts import layout


def current_waveform_plot(samples, voltage, dest_path, plot_title):
    """Plot the current data using bokeh interactive plotting tool.

    Plotting power measurement data with bokeh to generate interactive plots.
    You can do interactive data analysis on the plot after generating with the
    provided widgets, which make the debugging much easier. To realize that,
    bokeh callback java scripting is used.

    Args:
        samples: a list of tuples in which the first element is a timestamp and
          the second element is the sampled current in milli amps at that time.
        voltage: the voltage that was used during the measurement.
        dest_path: destination path.
        plot_title: a filename and title for the plot.
    Returns:
        plot: the plotting object of bokeh, optional, will be needed if multiple
           plots will be combined to one html file.
        dt: the datatable object of bokeh, optional, will be needed if multiple
           datatables will be combined to one html file.
    """
    logging.info('Plotting the power measurement data.')

    duration = samples[-1][0] - samples[0][0]
    current_data = [sample[1] * 1000 for sample in samples]
    avg_current = sum(current_data) / len(current_data)
    color = ['navy'] * len(samples)
    time_realtime = [
        datetime.datetime.fromtimestamp(sample[0]) for sample in samples
    ]

    # Preparing the data and source link for bokehn java callback
    source = ColumnDataSource(
        data=dict(x=time_realtime, y=current_data, color=color))
    s2 = ColumnDataSource(
        data=dict(a=[duration],
                  b=[round(avg_current, 2)],
                  c=[round(avg_current * voltage, 2)],
                  d=[round(avg_current * voltage * duration, 2)],
                  e=[round(avg_current * duration, 2)]))
    # Setting up data table for the output
    columns = [
        TableColumn(field='a', title='Total Duration (s)'),
        TableColumn(field='b', title='Average Current (mA)'),
        TableColumn(field='c', title='Average Power (4.2v) (mW)'),
        TableColumn(field='d', title='Average Energy (mW*s)'),
        TableColumn(field='e', title='Normalized Average Energy (mA*s)')
    ]
    dt = DataTable(source=s2,
                   columns=columns,
                   width=1300,
                   height=60,
                   editable=True)

    output_file(os.path.join(dest_path, plot_title + '.html'))
    tools = 'box_zoom,box_select,pan,crosshair,redo,undo,reset,hover,save'
    # Create a new plot with the datatable above
    plot = figure(x_axis_type='datetime',
                  plot_width=1300,
                  plot_height=700,
                  title=plot_title,
                  tools=tools)
    plot.add_tools(bokeh_tools.WheelZoomTool(dimensions='width'))
    plot.add_tools(bokeh_tools.WheelZoomTool(dimensions='height'))
    plot.line('x', 'y', source=source, line_width=2)
    plot.circle('x', 'y', source=source, size=0.5, fill_color='color')
    plot.xaxis.axis_label = 'Time (s)'
    plot.yaxis.axis_label = 'Current (mA)'
    plot.xaxis.formatter = DatetimeTickFormatter(
        seconds=["%H:%M:%S"],
        milliseconds=["%H:%M:%S:%3Ns"],
        microseconds=["%H:%M:%S:%fus"],
        minutes=["%H:%M:%S"],
        minsec=["%H:%M:%S"],
        hours=["%H:%M:%S"])

    # Callback JavaScript
    source.selected.js_on_change(
        "indices",
        CustomJS(args=dict(source=source, mytable=dt),
                 code="""
        const inds = source.selected.indices;
        const d1 = source.data;
        const d2 = mytable.source.data;
        var ym = 0
        var ts = 0
        var min=d1['x'][inds[0]]
        var max=d1['x'][inds[0]]
        d2['a'] = []
        d2['b'] = []
        d2['c'] = []
        d2['d'] = []
        d2['e'] = []
        if (inds.length==0) {return;}
        for (var i = 0; i < inds.length; i++) {
        ym += d1['y'][inds[i]]
        d1['color'][inds[i]] = "red"
        if (d1['x'][inds[i]] < min) {
          min = d1['x'][inds[i]]}
        if (d1['x'][inds[i]] > max) {
          max = d1['x'][inds[i]]}
        }
        ym /= inds.length
        ts = max - min
        d2['a'].push(Math.round(ts*1000.0)/1000000.0)
        d2['b'].push(Math.round(ym*100.0)/100.0)
        d2['c'].push(Math.round(ym*4.2*100.0)/100.0)
        d2['d'].push(Math.round(ym*4.2*ts*100.0)/100.0)
        d2['e'].push(Math.round(ym*ts*100.0)/100.0)
        source.change.emit();
        mytable.change.emit();
    """))

    # Layout the plot and the datatable bar
    save(layout([[dt], [plot]]))
    return plot, dt


def monsoon_histogram_plot(samples, dest_path, plot_title):
    """ Creates a histogram from a monsoon result object.

    Args:
        samples: a list of tuples in which the first element is a timestamp and
          the second element is the sampled current in milli amps at that time.
        dest_path: destination path
        plot_title: a filename and title for the plot.
    Returns:
        a tuple of arrays containing the values of the histogram and the
        bin edges.
    """
    milli_amps = [sample[1] * 1000 for sample in samples]
    hist, edges = numpy.histogram(milli_amps,
                                  bins=math.ceil(max(milli_amps)),
                                  range=(0, max(milli_amps)))

    output_file(os.path.join(dest_path, plot_title + '.html'))

    plot = figure(title=plot_title,
                  y_axis_type='log',
                  background_fill_color='#fafafa')

    plot.quad(top=hist,
              bottom=0,
              left=edges[:-1],
              right=edges[1:],
              fill_color='navy')

    plot.y_range.start = 0
    plot.xaxis.axis_label = 'Instantaneous current [mA]'
    plot.yaxis.axis_label = 'Count'
    plot.grid.grid_line_color = 'white'

    save(plot)

    return hist, edges


def monsoon_tx_power_sweep_plot(dest_path, plot_title, currents, txs):
    """ Creates average current vs tx power plot

    Args:
        dest_path: destination path
        plot_title: a filename and title for the plot.
        currents: List of average currents measured during power sweep
        txs: List of uplink input power levels specified for each measurement
    """

    output_file(os.path.join(dest_path, plot_title + '.html'))

    plot = figure(title=plot_title,
                  y_axis_label='Average Current [mA]',
                  x_axis_label='Tx Power [dBm]',
                  background_fill_color='#fafafa')

    plot.line(txs, currents)
    plot.circle(txs, currents, fill_color='white', size=8)
    plot.y_range.start = 0

    save(plot)
