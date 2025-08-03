"""Repacks GKI boot images with the given kernel images."""
import argparse
import json
import os
import shutil
import tempfile

from treble.fetcher import fetcher_lib
from treble.gki import repack_gki_lib


def main():
  parser = argparse.ArgumentParser(
      description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--json_keyfile',
      help='JSON keyfile containing credentials. '
      '(Default: Use default credential file)')
  parser.add_argument(
      '--bootimg_build_id', help='Download from the specified build.')
  parser.add_argument(
      '--ramdisk_build_id', help='DEPRECATED. Use --bootimg_build_id instead.')
  parser.add_argument(
      '--bootimg_target',
      help='Name of the bootimg target from the bootimg branch.')
  parser.add_argument(
      '--ramdisk_target', help='DEPRECATED. Use --bootimg_target instead.')
  parser.add_argument(
      '--kernel_build_id',
      required=True,
      help='Download from the specified build.')
  parser.add_argument(
      '--kernel_target',
      required=True,
      help='Name of the kernel target from the kernel branch.')
  parser.add_argument(
      '--kernel_debug_target',
      required=True,
      help='Name of the kernel debug target from the kernel branch.')
  parser.add_argument(
      '--kernel_version',
      required=True,
      help='The Kernel version to use when repacking.')
  parser.add_argument(
      '--out_dir', required=True, help='Save output to this directory.')

  args = parser.parse_args()
  client = fetcher_lib.create_client_from_json_keyfile(
      json_keyfile_name=args.json_keyfile)

  if not os.path.exists(args.out_dir):
    os.makedirs(args.out_dir)

  bootimg_build_id = args.bootimg_build_id or args.ramdisk_build_id
  bootimg_target = args.bootimg_target or args.ramdisk_target

  with tempfile.TemporaryDirectory() as tmp_bootimg_dir, \
      tempfile.TemporaryDirectory() as tmp_kernel_dir:
    # Fetch boot images.
    repack_gki_lib.fetch_bootimg(
        client=client,
        out_dir=tmp_bootimg_dir,
        build_id=bootimg_build_id,
        kernel_version=args.kernel_version,
        target=bootimg_target,
    )

    # Fetch kernel artifacts.
    kernel_dir, kernel_debug_dir = repack_gki_lib.fetch_kernel(
        client=client,
        out_dir=tmp_kernel_dir,
        build_id=args.kernel_build_id,
        kernel_target=args.kernel_target,
        kernel_debug_target=args.kernel_debug_target,
    )

    # Save kernel artifacts to the out dir.
    kernel_out_dir = os.path.join(args.out_dir, 'kernel', args.kernel_version)
    if not os.path.exists(kernel_out_dir):
      os.makedirs(kernel_out_dir)

    def copy_kernel_file(in_dir, filename, outname=None):
      if not outname:
        outname = filename
      shutil.copy(
          os.path.join(in_dir, filename), os.path.join(kernel_out_dir, outname))

    copy_kernel_file(kernel_dir, 'System.map')
    copy_kernel_file(kernel_dir, 'abi_symbollist')
    copy_kernel_file(kernel_dir, 'vmlinux')
    copy_kernel_file(kernel_dir, 'vmlinux.symvers')
    copy_kernel_file(kernel_dir, 'Image',
                     'kernel-{}'.format(args.kernel_version))
    copy_kernel_file(kernel_dir, 'Image.lz4',
                     'kernel-{}-lz4'.format(args.kernel_version))
    copy_kernel_file(kernel_dir, 'Image.gz',
                     'kernel-{}-gz'.format(args.kernel_version))
    copy_kernel_file(kernel_debug_dir, 'System.map', 'System.map-allsyms')
    copy_kernel_file(kernel_debug_dir, 'abi-generated.xml')
    copy_kernel_file(kernel_debug_dir, 'abi-full-generated.xml')
    copy_kernel_file(kernel_debug_dir, 'Image',
                     'kernel-{}-allsyms'.format(args.kernel_version))
    copy_kernel_file(kernel_debug_dir, 'Image.lz4',
                     'kernel-{}-lz4-allsyms'.format(args.kernel_version))
    copy_kernel_file(kernel_debug_dir, 'Image.gz',
                     'kernel-{}-gz-allsyms'.format(args.kernel_version))
    copy_kernel_file(kernel_debug_dir, 'vmlinux', 'vmlinux-allsyms')
    copy_kernel_file(kernel_debug_dir, 'vmlinux.symvers', 'vmlinux.symvers-allsyms')

    # Repack individual boot images using the fetched kernel artifacts,
    # then save to the out dir.
    repack_gki_lib.repack_bootimgs(tmp_bootimg_dir, kernel_dir,
                                   kernel_debug_dir)
    shutil.copytree(tmp_bootimg_dir, args.out_dir, dirs_exist_ok=True)

    # Repack boot images inside the img.zip and save to the out dir.
    img_zip_name = [f for f in os.listdir(tmp_bootimg_dir) if '-img-' in f][0]
    img_zip_path = os.path.join(tmp_bootimg_dir, img_zip_name)
    repack_gki_lib.repack_img_zip(img_zip_path, kernel_dir, kernel_debug_dir,
                                  args.kernel_version)
    shutil.copy(img_zip_path, args.out_dir)

    target_files_zip_name = [
        f for f in os.listdir(tmp_bootimg_dir) if '-target_files-' in f
    ][0]
    target_files_zip_path = os.path.join(tmp_bootimg_dir, target_files_zip_name)
    repack_gki_lib.replace_target_files_zip_kernels(target_files_zip_path,
                                                    kernel_out_dir,
                                                    args.kernel_version)
    shutil.copy(target_files_zip_path, args.out_dir)

    # Copy otatools.zip from the bootimg build, used for GKI signing.
    shutil.copy(os.path.join(tmp_bootimg_dir, 'otatools.zip'), args.out_dir)

    # Write prebuilt-info.txt using the prebuilt artifact build IDs.
    data = {
        'kernel-build-id': int(args.kernel_build_id),
    }
    with open(os.path.join(kernel_out_dir, 'prebuilt-info.txt'), 'w') as f:
      json.dump(data, f, indent=4)


if __name__ == '__main__':
  main()
