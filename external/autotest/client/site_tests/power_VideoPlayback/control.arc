AUTHOR = "Chrome OS Team"
NAME = "power_VideoPlayback.arc"
PURPOSE = "Measure video playback power usage."

CRITERIA = "This test is a benchmark."
TIME = "LENGTHY"
TEST_CATEGORY = "Benchmark"
TEST_CLASS = "power"
TEST_TYPE = "client"
PY_VERSION = 3

ATTRIBUTES = ""
DOC = """
Test video decode for
- h264 1080p 30fps
- vp9 1080 30fps
for 10min each with ARC enabled.

This test is called just arc to use it to distinguish between ARC vs no ARC
on the DUT. The details of video encoding and length aren't relevant other
than `arc` and `noarc` should be identical.
"""

args_dict = utils.args_to_dict(args)
pdash_note = args_dict.get('pdash_note', '')
job.run_test('power_VideoPlayback', tag=NAME.split('.')[1],
             videos=[('h264_1080_30fps', ''), ('vp9_1080_30fps','')],
             secs_per_video=600, pdash_note=pdash_note, seconds_period=20,
             run_arc=True)
