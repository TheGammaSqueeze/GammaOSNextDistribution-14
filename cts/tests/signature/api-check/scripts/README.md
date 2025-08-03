Transform expected failures is a tool to automate the process of transforming
the error reported to the expected failure format.

e.g. if the error is something like this: mismatch_method:
android.service.euicc.EuiccService#onDownloadSubscription(int,
android.telephony.euicc.DownloadableSubscription, boolean, boolean) Error:
Non-compatible method found when looking for public abstract int
onDownloadSubscription(int, android.telephony.euicc.DownloadableSubscription,
boolean, boolean) - because modifier mismatch - description (public abstract),
method (public)

Then the expected failure would look something like this:
"mismatch_method:android.service.euicc.EuiccService#onDownloadSubscription(int,
android.telephony.euicc.DownloadableSubscription, boolean, boolean)",

Example: ./cts/tests/signature/api-check/scripts/transform_expected_failures.py
"`cat /path/to/failures.txt`"

This will print the expected failure format It will look for these list of
annotations "extra_field|missing_method|extra_class|missing_annotation"

Optionally you can specify the list of expected failures types annotation
separated by "|" Example:
./cts/tests/signature/api-check/scripts/transform_expected_failures.py "`cat
/path/to/failures.txt`" "missing_method|missing_annotation"
