#!/usr/bin/env python3.4
#
#   Copyright 2021 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import bokeh, bokeh.plotting, bokeh.io
import collections
import itertools
import json
import math
from acts_contrib.test_utils.wifi import wifi_performance_test_utils as wputils


# Plotting Utilities
class BokehFigure():
    """Class enabling  simplified Bokeh plotting."""

    COLORS = [
        'black',
        'blue',
        'blueviolet',
        'brown',
        'burlywood',
        'cadetblue',
        'cornflowerblue',
        'crimson',
        'cyan',
        'darkblue',
        'darkgreen',
        'darkmagenta',
        'darkorange',
        'darkred',
        'deepskyblue',
        'goldenrod',
        'green',
        'grey',
        'indigo',
        'navy',
        'olive',
        'orange',
        'red',
        'salmon',
        'teal',
        'yellow',
    ]
    MARKERS = [
        'asterisk', 'circle', 'circle_cross', 'circle_x', 'cross', 'diamond',
        'diamond_cross', 'hex', 'inverted_triangle', 'square', 'square_x',
        'square_cross', 'triangle', 'x'
    ]

    TOOLS = ('box_zoom,box_select,pan,crosshair,redo,undo,reset,hover,save')

    def __init__(self,
                 title=None,
                 x_label=None,
                 primary_y_label=None,
                 secondary_y_label=None,
                 height=700,
                 width=1100,
                 title_size='15pt',
                 axis_label_size='12pt',
                 legend_label_size='12pt',
                 legend_location = 'top_right',
                 axis_tick_label_size='12pt',
                 x_axis_type='auto',
                 sizing_mode='scale_both',
                 json_file=None):
        if json_file:
            self.load_from_json(json_file)
        else:
            self.figure_data = []
            self.fig_property = {
                'title': title,
                'x_label': x_label,
                'primary_y_label': primary_y_label,
                'secondary_y_label': secondary_y_label,
                'num_lines': 0,
                'height': height,
                'width': width,
                'title_size': title_size,
                'axis_label_size': axis_label_size,
                'legend_label_size': legend_label_size,
                'legend_location': legend_location,
                'axis_tick_label_size': axis_tick_label_size,
                'x_axis_type': x_axis_type,
                'sizing_mode': sizing_mode
            }

    def init_plot(self):
        self.plot = bokeh.plotting.figure(
            sizing_mode=self.fig_property['sizing_mode'],
            width=self.fig_property['width'],
            height=self.fig_property['height'],
            title=self.fig_property['title'],
            tools=self.TOOLS,
            x_axis_type=self.fig_property['x_axis_type'],
            output_backend='webgl')
        tooltips = [
            ('index', '$index'),
            ('(x,y)', '($x, $y)'),
        ]
        hover_set = []
        for line in self.figure_data:
            hover_set.extend(line['hover_text'].keys())
        hover_set = set(hover_set)
        for item in hover_set:
            tooltips.append((item, '@{}'.format(item)))
        self.plot.hover.tooltips = tooltips
        self.plot.add_tools(
            bokeh.models.tools.WheelZoomTool(dimensions='width'))
        self.plot.add_tools(
            bokeh.models.tools.WheelZoomTool(dimensions='height'))

    def _filter_line(self, x_data, y_data, hover_text=None):
        """Function to remove NaN points from bokeh plots."""
        x_data_filtered = []
        y_data_filtered = []
        hover_text_filtered = {}
        for idx, xy in enumerate(
                itertools.zip_longest(x_data, y_data, fillvalue=float('nan'))):
            if not math.isnan(xy[1]):
                x_data_filtered.append(xy[0])
                y_data_filtered.append(xy[1])
                if hover_text:
                    for key, value in hover_text.items():
                        hover_text_filtered.setdefault(key, [])
                        hover_text_filtered[key].append(
                            value[idx] if len(value) > idx else '')
        return x_data_filtered, y_data_filtered, hover_text_filtered

    def add_line(self,
                 x_data,
                 y_data,
                 legend,
                 hover_text=None,
                 color=None,
                 width=3,
                 style='solid',
                 marker=None,
                 marker_size=10,
                 shaded_region=None,
                 y_axis='default'):
        """Function to add line to existing BokehFigure.

        Args:
            x_data: list containing x-axis values for line
            y_data: list containing y_axis values for line
            legend: string containing line title
            hover_text: text to display when hovering over lines
            color: string describing line color
            width: integer line width
            style: string describing line style, e.g, solid or dashed
            marker: string specifying line marker, e.g., cross
            shaded region: data describing shaded region to plot
            y_axis: identifier for y-axis to plot line against
        """
        if y_axis not in ['default', 'secondary']:
            raise ValueError('y_axis must be default or secondary')
        if color == None:
            color = self.COLORS[self.fig_property['num_lines'] %
                                len(self.COLORS)]
        if style == 'dashed':
            style = [5, 5]
        if isinstance(hover_text, list):
            hover_text = {'info': hover_text}
        x_data_filter, y_data_filter, hover_text_filter = self._filter_line(
            x_data, y_data, hover_text)
        self.figure_data.append({
            'x_data': x_data_filter,
            'y_data': y_data_filter,
            'legend': legend,
            'hover_text': hover_text_filter,
            'color': color,
            'width': width,
            'style': style,
            'marker': marker,
            'marker_size': marker_size,
            'shaded_region': shaded_region,
            'y_axis': y_axis
        })
        self.fig_property['num_lines'] += 1

    def add_scatter(self,
                    x_data,
                    y_data,
                    legend,
                    hover_text=None,
                    color=None,
                    marker=None,
                    marker_size=10,
                    y_axis='default'):
        """Function to add line to existing BokehFigure.

        Args:
            x_data: list containing x-axis values for line
            y_data: list containing y_axis values for line
            legend: string containing line title
            hover_text: text to display when hovering over lines
            color: string describing line color
            marker: string specifying marker, e.g., cross
            y_axis: identifier for y-axis to plot line against
        """
        if y_axis not in ['default', 'secondary']:
            raise ValueError('y_axis must be default or secondary')
        if color == None:
            color = self.COLORS[self.fig_property['num_lines'] %
                                len(self.COLORS)]
        if marker == None:
            marker = self.MARKERS[self.fig_property['num_lines'] %
                                  len(self.MARKERS)]
        self.figure_data.append({
            'x_data': x_data,
            'y_data': y_data,
            'legend': legend,
            'hover_text': hover_text,
            'color': color,
            'width': 0,
            'style': 'solid',
            'marker': marker,
            'marker_size': marker_size,
            'shaded_region': None,
            'y_axis': y_axis
        })
        self.fig_property['num_lines'] += 1

    def generate_figure(self, output_file=None, save_json=True):
        """Function to generate and save BokehFigure.

        Args:
            output_file: string specifying output file path
        """
        self.init_plot()
        two_axes = False
        for line in self.figure_data:
            data_dict = {'x': line['x_data'], 'y': line['y_data']}
            for key, value in line['hover_text'].items():
                data_dict[key] = value
            source = bokeh.models.ColumnDataSource(data=data_dict)
            if line['width'] > 0:
                self.plot.line(x='x',
                               y='y',
                               legend_label=line['legend'],
                               line_width=line['width'],
                               color=line['color'],
                               line_dash=line['style'],
                               name=line['y_axis'],
                               y_range_name=line['y_axis'],
                               source=source)
            if line['shaded_region']:
                band_x = line['shaded_region']['x_vector']
                band_x.extend(line['shaded_region']['x_vector'][::-1])
                band_y = line['shaded_region']['lower_limit']
                band_y.extend(line['shaded_region']['upper_limit'][::-1])
                self.plot.patch(band_x,
                                band_y,
                                color='#7570B3',
                                line_alpha=0.1,
                                fill_alpha=0.1)
            if line['marker'] in self.MARKERS:
                marker_func = getattr(self.plot, line['marker'])
                marker_func(x='x',
                            y='y',
                            size=line['marker_size'],
                            legend_label=line['legend'],
                            line_color=line['color'],
                            fill_color=line['color'],
                            name=line['y_axis'],
                            y_range_name=line['y_axis'],
                            source=source)
            if line['y_axis'] == 'secondary':
                two_axes = True

        #x-axis formatting
        self.plot.xaxis.axis_label = self.fig_property['x_label']
        self.plot.x_range.range_padding = 0
        self.plot.xaxis[0].axis_label_text_font_size = self.fig_property[
            'axis_label_size']
        self.plot.xaxis.major_label_text_font_size = self.fig_property[
            'axis_tick_label_size']
        #y-axis formatting
        self.plot.yaxis[0].axis_label = self.fig_property['primary_y_label']
        self.plot.yaxis[0].axis_label_text_font_size = self.fig_property[
            'axis_label_size']
        self.plot.yaxis.major_label_text_font_size = self.fig_property[
            'axis_tick_label_size']
        self.plot.y_range = bokeh.models.DataRange1d(names=['default'])
        if two_axes and 'secondary' not in self.plot.extra_y_ranges:
            self.plot.extra_y_ranges = {
                'secondary': bokeh.models.DataRange1d(names=['secondary'])
            }
            self.plot.add_layout(
                bokeh.models.LinearAxis(
                    y_range_name='secondary',
                    axis_label=self.fig_property['secondary_y_label'],
                    axis_label_text_font_size=self.
                    fig_property['axis_label_size']), 'right')
        # plot formatting
        self.plot.legend.location = self.fig_property['legend_location']
        self.plot.legend.click_policy = 'hide'
        self.plot.title.text_font_size = self.fig_property['title_size']
        self.plot.legend.label_text_font_size = self.fig_property[
            'legend_label_size']

        if output_file is not None:
            self.save_figure(output_file, save_json)
        return self.plot

    def load_from_json(self, file_path):
        with open(file_path, 'r') as json_file:
            fig_dict = json.load(json_file)
        self.fig_property = fig_dict['fig_property']
        self.figure_data = fig_dict['figure_data']

    def _save_figure_json(self, output_file):
        """Function to save a json format of a figure"""
        figure_dict = collections.OrderedDict(fig_property=self.fig_property,
                                              figure_data=self.figure_data)
        output_file = output_file.replace('.html', '_plot_data.json')
        with open(output_file, 'w') as outfile:
            json.dump(wputils.serialize_dict(figure_dict), outfile, indent=4)

    def save_figure(self, output_file, save_json=True):
        """Function to save BokehFigure.

        Args:
            output_file: string specifying output file path
            save_json: flag controlling json outputs
        """
        if save_json:
            self._save_figure_json(output_file)
        bokeh.io.output_file(output_file)
        bokeh.io.save(self.plot)

    @staticmethod
    def save_figures(figure_array, output_file_path, save_json=True):
        """Function to save list of BokehFigures in one file.

        Args:
            figure_array: list of BokehFigure object to be plotted
            output_file: string specifying output file path
        """
        for idx, figure in enumerate(figure_array):
            figure.generate_figure()
            if save_json:
                json_file_path = output_file_path.replace(
                    '.html', '{}-plot_data.json'.format(idx))
                figure._save_figure_json(json_file_path)
        plot_array = [figure.plot for figure in figure_array]
        all_plots = bokeh.layouts.column(children=plot_array,
                                         sizing_mode='scale_width')
        bokeh.plotting.output_file(output_file_path)
        bokeh.plotting.save(all_plots)
