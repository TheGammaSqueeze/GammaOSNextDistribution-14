# Copyright 2014 The Android Open Source Project
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

# --------------------------------------------------------------------------- #
# The Google Python style guide should be used for scripts:                   #
# http://google-styleguide.googlecode.com/svn/trunk/pyguide.html              #
# --------------------------------------------------------------------------- #

# The ITS modules that are in the utils directory. To see formatted
# docs, use the "pydoc" command:
#
# > pydoc image_processing_utils
#
"""Tutorial script for CameraITS tests."""
import capture_request_utils
import image_processing_utils
import its_base_test
import its_session_utils

# Standard Python modules.
import logging
import os.path

# Modules from the numpy, scipy, and matplotlib libraries. These are used for
# the image processing code, and images are represented as numpy arrays.
from matplotlib import pylab
import numpy
import matplotlib
import matplotlib.pyplot

# Module for Mobly
from mobly import test_runner

# A convention in each script is to use the filename (without the extension)
# as the name of the test, when printing results to the screen or dumping files.
_NAME = os.path.basename(__file__).split('.')[0]


# Each script has a class definition
class TutorialTest(its_base_test.ItsBaseTest):
  """Test the validity of some metadata entries.

  Looks at the capture results and at the camera characteristics objects.
  Script uses a config.yml file in the CameraITS directory.
  A sample config.yml file:
    TestBeds:
    - Name: TEST_BED_TUTORIAL
      Controllers:
          AndroidDevice:
            - serial: 03281FDD40008Y
              label: dut
      TestParams:
        camera: "1"
        scene: "0"

  A sample script call:
    python tests/tutorial.py --config config.yml

  """

  def test_tutorial(self):
    # Each script has a string description of what it does. This is the first
    # entry inside the main function.
    """Tutorial script to show how to use the ITS infrastructure."""

    # The standard way to open a session with a connected camera device. This
    # creates a cam object which encapsulates the session and which is active
    # within the scope of the 'with' block; when the block exits, the camera
    # session is closed. The device and camera are defined in the config.yml
    # file.
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:

      # Append the log_path to store images in the proper location.
      # Images will be stored in the test output folder:
      # /tmp/logs/mobly/$TEST_BED_NAME/$DATE/TutorialTest
      file_name = os.path.join(self.log_path, _NAME)

      # Get the static properties of the camera device. Returns a Python
      # associative array object; print it to the console.
      props = cam.get_camera_properties()
      logging.debug('props\n%s', str(props))

      # Grab a YUV frame with manual exposure of sensitivity = 200, exposure
      # duration = 50ms.
      req = capture_request_utils.manual_capture_request(200, 50*1000*1000)
      cap = cam.do_capture(req)

      # Print the properties of the captured frame; width and height are
      # integers, and the metadata is a Python associative array object.
      # logging.info will be printed to screen & test_log.INFO
      # logging.debug to test_log.DEBUG in /tmp/logs/mobly/... directory
      logging.info('Captured image width: %d, height: %d',
                   cap['width'], cap['height'])
      logging.debug('metadata\n%s', str(cap['metadata']))

      # The captured image is YUV420. Convert to RGB, and save as a file.
      rgbimg = image_processing_utils.convert_capture_to_rgb_image(cap)
      image_processing_utils.write_image(rgbimg, f'{file_name}_rgb.jpg')

      # Can also get the Y,U,V planes separately; save these to greyscale
      # files.
      yimg, uimg, vimg = image_processing_utils.convert_capture_to_planes(cap)
      image_processing_utils.write_image(yimg, f'{file_name}_y_plane.jpg')
      image_processing_utils.write_image(uimg, f'{file_name}_u_plane.jpg')
      image_processing_utils.write_image(vimg, f'{file_name}_v_plane.jpg')

      # Run 3A on the device. In this case, just use the entire image as the
      # 3A region, and run each of AWB,AE,AF. Can also change the region and
      # specify independently for each of AE,AWB,AF whether it should run.
      #
      # NOTE: This may fail, if the camera isn't pointed at a reasonable
      # target scene. If it fails, the script will end. The logcat messages
      # can be inspected to see the status of 3A running on the device.
      #
      # If this keeps on failing, try also rebooting the device before
      # running the test.
      sens, exp, gains, xform, focus = cam.do_3a(get_results=True)
      logging.info('AE: sensitivity %d, exposure %dms', sens, exp/1000000.0)
      logging.info('AWB: gains %s', str(gains))
      logging.info('AWB: transform %s', str(xform))
      logging.info('AF: distance %.4f', focus)

      # Grab a new manual frame, using the 3A values, and convert it to RGB
      # and save it to a file too. Note that the 'req' object is just a
      # Python dictionary that is pre-populated by the capture_request_utils
      # functions (in this case a default manual capture), and the key/value
      # pairs in the object can be used to set any field of the capture
      # request. Here, the AWB gains and transform (CCM) are being used.
      # Note that the CCM transform is in a rational format in capture
      # requests, meaning it is an object with integer numerators and
      # denominators. The 3A routine returns simple floats instead, however,
      # so a conversion from float to rational must be performed.
      req = capture_request_utils.manual_capture_request(sens, exp)
      xform_rat = capture_request_utils.float_to_rational(xform)

      req['android.colorCorrection.transform'] = xform_rat
      req['android.colorCorrection.gains'] = gains
      cap = cam.do_capture(req)
      rgbimg = image_processing_utils.convert_capture_to_rgb_image(cap)
      image_processing_utils.write_image(rgbimg, f'{file_name}_rgb_2.jpg')

      # log the actual capture request object that was used.
      logging.debug('req: %s', str(req))

      # Images are numpy arrays. The dimensions are (h,w,3) when indexing,
      # in the case of RGB images. Greyscale images are (h,w,1). Pixels are
      # generally float32 values in the [0,1] range, however some of the
      # helper functions in image_processing_utils deal with the packed YUV420
      # and other formats of images that come from the device (and convert
      # them to float32).
      # Print the dimensions of the image, and the top-left pixel value,
      # which is an array of 3 floats.
      logging.info('RGB image dimensions: %s', str(rgbimg.shape))
      logging.info('RGB image top-left pixel: %s', str(rgbimg[0, 0]))

      # Grab a center tile from the image; this returns a new image. Save
      # this tile image. In this case, the tile is the middle 10% x 10%
      # rectangle.
      tile = image_processing_utils.get_image_patch(
          rgbimg, 0.45, 0.45, 0.1, 0.1)
      image_processing_utils.write_image(tile, f'{file_name}_rgb_2_tile.jpg')

      # Compute the mean values of the center tile image.
      rgb_means = image_processing_utils.compute_image_means(tile)
      logging.info('RGB means: %s', str(rgb_means))

      # Apply a lookup table to the image, and save the new version. The LUT
      # is basically a tonemap, and can be used to implement a gamma curve.
      # In this case, the LUT is used to double the value of each pixel.
      lut = numpy.array([2*i for i in range(65536)])
      rgbimg_lut = image_processing_utils.apply_lut_to_image(rgbimg, lut)
      image_processing_utils.write_image(
          rgbimg_lut, f'{file_name}_rgb_2_lut.jpg')

      # Compute a histogram of the luma image, in 256 buckets.
      yimg, _, _ = image_processing_utils.convert_capture_to_planes(cap)
      hist, _ = numpy.histogram(yimg*255, 256, (0, 256))

      # Plot the histogram using matplotlib, and save as a PNG image.
      pylab.plot(range(256), hist.tolist())
      pylab.xlabel('Luma DN')
      pylab.ylabel('Pixel count')
      pylab.title('Histogram of luma channel of captured image')
      matplotlib.pyplot.savefig(f'{file_name}_histogram.png')

      # Capture a frame to be returned as a JPEG. Load it as an RGB image,
      # then save it back as a JPEG.
      cap = cam.do_capture(req, cam.CAP_JPEG)
      rgbimg = image_processing_utils.convert_capture_to_rgb_image(cap)
      image_processing_utils.write_image(rgbimg, f'{file_name}_jpg.jpg')
      r, _, _ = image_processing_utils.convert_capture_to_planes(cap)
      image_processing_utils.write_image(r, f'{file_name}_r.jpg')

# This is the standard boilerplate in each test that allows the script to both
# be executed directly and imported as a module.
if __name__ == '__main__':
  test_runner.main()

