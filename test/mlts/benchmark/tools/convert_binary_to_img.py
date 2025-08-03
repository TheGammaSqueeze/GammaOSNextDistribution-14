#!/usr/bin/python3
""" Convert binary back to image. Use float32 as default.

usage: convert_binary_to_img.py [-h] -i INPUT -s height width depth

optional arguments:
  -h, --help            show this help message and exit
  -i INPUT, --input INPUT
                        Path to input binary file. File extension needs to be .input.
  -s height width depth, --shape height width depth
                        Output image shape. e.g. 224 224 3
Example usage:
python3 convert_binary_to_img.py -i image_file.input -s 224 224 3
"""

import argparse
import os
import sys

import numpy as np
from PIL import Image

def convert_file(filename: str, h: int, w: int, d: int):
    """Converts the input binary file back to image with shape following the input parameters.

    Parameters
    ----------
    h : int, height
    w : int, width
    d : int, depth
    """
    with open(filename, 'rb') as f:
        arr = np.frombuffer(f.read(), dtype=np.float32)
    print(f'Reshape buffer from {arr.shape} to {(h, w, d)}.')
    arr = arr.reshape((h, w, d))
    arr = (arr + 1) * 128
    im = Image.fromarray(arr.astype(np.uint8))
    destination = filename.replace('input', 'jpg')
    print(f'Image generated to {destination}.')
    im.save(destination)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('-i','--input', type=str, required=True, help='Path to input binary file. File extension needs to be .input.')
    parser.add_argument('-s','--shape', type=int, required=True, nargs=3, help='Output image shape. e.g. 224 224 3', metavar=('height', 'width', 'depth'))
    args = parser.parse_args()

    convert_file(args.input, *args.shape)
