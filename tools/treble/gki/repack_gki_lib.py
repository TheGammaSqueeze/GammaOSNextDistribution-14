"""Helper library for repacking GKI boot images."""
import os
import shutil
import subprocess
import tempfile

from treble.fetcher import fetcher_lib


def fetch_bootimg(client, out_dir, build_id, kernel_version, target):
  """Fetches boot.img artifacts from a given build ID."""
  fetcher_lib.fetch_artifacts(
      client=client,
      build_id=build_id,
      target=target,
      pattern=r'(gki_.*-img-.*\.zip|gki_.*-target_files-.*\.zip|otatools.zip)'
      .format(version=kernel_version),
      out_dir=out_dir)


def fetch_kernel(client, out_dir, build_id, kernel_target, kernel_debug_target):
  """Fetches kernel artifacts from a given build ID."""
  kernel_dir = os.path.join(out_dir, 'kernel')
  kernel_debug_dir = os.path.join(out_dir, 'kernel_debug')
  os.makedirs(kernel_dir)
  os.makedirs(kernel_debug_dir)

  fetcher_lib.fetch_artifacts(
      client=client,
      build_id=build_id,
      target=kernel_target,
      pattern=r'(Image|Image.lz4|System\.map|abi_symbollist|vmlinux|vmlinux.symvers)',
      out_dir=kernel_dir)
  fetcher_lib.fetch_artifacts(
      client=client,
      build_id=build_id,
      target=kernel_debug_target,
      pattern=r'(Image|Image.lz4|System\.map|abi-generated.xml|abi-full-generated.xml|vmlinux|vymlinx.symvers)',
      out_dir=kernel_debug_dir)

  print('Compressing kernels')

  def compress_kernel(kernel_path):
    zipped_kernel_path = os.path.join(os.path.dirname(kernel_path), 'Image.gz')
    with open(zipped_kernel_path, 'wb') as zipped_kernel:
      cmd = [
          'gzip',
          '-nc',
          kernel_path,
      ]
      print(' '.join(cmd))
      subprocess.check_call(cmd, stdout=zipped_kernel)

  compress_kernel(os.path.join(kernel_dir, 'Image'))
  compress_kernel(os.path.join(kernel_debug_dir, 'Image'))

  return kernel_dir, kernel_debug_dir


def _replace_kernel(bootimg_path, kernel_path):
  """Unpacks a boot.img, replaces the kernel, then repacks."""
  with tempfile.TemporaryDirectory() as unpack_dir:
    print('Unpacking bootimg %s' % bootimg_path)
    cmd = [
        'out/host/linux-x86/bin/unpack_bootimg',
        '--boot_img',
        bootimg_path,
        '--out',
        unpack_dir,
        '--format',
        'mkbootimg',
    ]
    print(' '.join(cmd))
    mkbootimg_args = subprocess.check_output(cmd).decode('utf-8').split(' ')
    print('Copying kernel %s' % kernel_path)
    shutil.copy(kernel_path, os.path.join(unpack_dir, 'kernel'))
    print('Repacking with mkbootimg')
    cmd = [
        'out/host/linux-x86/bin/mkbootimg',
        '--output',
        bootimg_path,
    ] + mkbootimg_args
    print(' '.join(cmd))
    subprocess.check_call(cmd)


def repack_bootimgs(bootimg_dir, kernel_dir, kernel_debug_dir):
  """Repacks all boot images in a given dir using the provided kernels."""
  for bootimg_path in os.listdir(bootimg_dir):
    bootimg_path = os.path.join(bootimg_dir, bootimg_path)
    if not bootimg_path.endswith('.img'):
      continue

    kernel_name = 'Image'
    if '-gz' in bootimg_path:
      kernel_name = 'Image.gz'
    elif '-lz4' in bootimg_path:
      kernel_name = 'Image.lz4'

    kernel_path = os.path.join(kernel_dir, kernel_name)
    if bootimg_path.endswith('-allsyms.img'):
      kernel_path = os.path.join(kernel_debug_dir, kernel_name)

    _replace_kernel(bootimg_path, kernel_path)


def repack_img_zip(img_zip_path, kernel_dir, kernel_debug_dir, kernel_version):
  """Repacks boot images within an img.zip archive."""
  with tempfile.TemporaryDirectory() as unzip_dir:
    pattern = 'boot-{}*'.format(kernel_version)
    print('Unzipping %s to repack bootimgs' % img_zip_path)
    cmd = [
        'unzip',
        '-d',
        unzip_dir,
        img_zip_path,
        pattern,
    ]
    print(' '.join(cmd))
    subprocess.check_call(cmd)
    repack_bootimgs(unzip_dir, kernel_dir, kernel_debug_dir)
    cmd = [
        'zip',
        img_zip_path,
        pattern,
    ]
    print(' '.join(cmd))
    subprocess.check_call(cmd, cwd=unzip_dir)


def replace_target_files_zip_kernels(target_files_zip_path, kernel_out_dir,
                                     kernel_version):
  """Replaces the BOOT/kernel-* kernels within a target_files.zip archive."""
  with tempfile.TemporaryDirectory() as unzip_dir:
    pattern = 'BOOT/kernel-{}*'.format(kernel_version)
    print(
        'Unzipping %s to replace kernels in preparation for signing' %
        target_files_zip_path,)
    cmd = [
        'unzip',
        '-d',
        unzip_dir,
        target_files_zip_path,
        pattern,
    ]
    print(' '.join(cmd))
    subprocess.check_call(cmd)
    for kernel in os.listdir(kernel_out_dir):
      if kernel.startswith('kernel-{}'.format(kernel_version)):
        print('Copying %s' % kernel)
        shutil.copy(
            os.path.join(kernel_out_dir, kernel),
            os.path.join(unzip_dir, 'BOOT'))
    cmd = [
        'zip',
        target_files_zip_path,
        pattern,
    ]
    print(' '.join(cmd))
    subprocess.check_call(cmd, cwd=unzip_dir)
