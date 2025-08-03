This is a fork of chromite code used by autotest.
Since autotest is stuck on Python 2 and is holding back chromite,
we've pulled out the code that autotest cares about here.
If/when autotest supports Python 3.6+ only, it can migrate back
to using chromite directly.
