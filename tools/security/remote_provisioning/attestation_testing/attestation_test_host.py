from sys import argv
import os
import subprocess
import time

APK_DIR = '${ANDROID_PRODUCT_OUT}/system/app/AttestationTestTool/AttestationTestTool.apk'
FAILURE_TAG = 'AttestationFail'
FAILURE_PREFIX = 'Failure: '
FINISHED_TAG = 'AttestationFinished'
INFO_TAG = 'AttestationFailInfo'
INFO_PREFIX = ' ' * len(FAILURE_PREFIX)
devnull = open(os.devnull, 'wb')

# Clear logcat
subprocess.call('adb logcat -c', shell=True, stdout=devnull)
subprocess.call('adb install -r ' + APK_DIR, shell=True, stdout=devnull)
subprocess.call('adb shell am start -a android.intent.action.MAIN -n com.google.attestationexample/.AttestationActivity',
                shell=True, stdout=devnull)
finished = False
read_retry = 0
failures = 0
while not finished and read_retry < 5:
    time.sleep(1)
    logcat = subprocess.check_output(['adb', 'logcat', '-d'], stderr=subprocess.STDOUT)
    for line in logcat.decode('utf-8').split('\n'):
        if INFO_TAG in line:
            print(INFO_PREFIX + line[line.index('AttestationFailInfo') + len('AttestationFailInfo:'):])
        elif FAILURE_TAG in line:
            failures += 1
            print(FAILURE_PREFIX + line[line.index('AttestationFail') + len('AttestationFail:'):])
        elif FINISHED_TAG in line and not finished:
            print('Finished. Failures: ' + str(failures))
            finished = True
            break
    read_retry += 1
    if read_retry == 5:
        print('Attestation test did not complete, check logcat to determine the source of the error')
subprocess.call('adb uninstall com.google.attestationexample', shell=True, stdout=devnull)
