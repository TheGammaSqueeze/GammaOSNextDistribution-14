# Lint as: python2, python3

from __future__ import division
from __future__ import print_function
from mock_demo import E

def do_create_stuff():
    obj = E(val=7)
    print(obj.method1())
