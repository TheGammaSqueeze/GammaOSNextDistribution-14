#!/bin/bash

python3 -m pip show virtualenv > /dev/null

if [ $? -ne 0 ]; then
  echo "The package 'virtualenv' must be installed to run the upload tests." >&2
  exit 1
fi

virtualenv='/tmp/acts_preupload_virtualenv'

echo "preparing virtual env" > "${virtualenv}.log"
python3 -m virtualenv -p python3 $virtualenv >> "${virtualenv}.log" 2>&1
cp -r acts/framework $virtualenv/
cd $virtualenv/framework
echo "installing acts in virtual env" >> "${virtualenv}.log"
$virtualenv/bin/python3 setup.py develop >> "${virtualenv}.log" 2>&1
cd - > /dev/null
echo "done" >> "${virtualenv}.log"
