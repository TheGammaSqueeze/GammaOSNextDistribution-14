"""Fetches artifacts from Android Build."""
import argparse
import os

from treble.fetcher import fetcher_lib


def main():
  parser = argparse.ArgumentParser(
      description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--json_keyfile',
      help='JSON keyfile containing credentials. '
      '(Default: Use default credential file)')
  parser.add_argument(
      '--target', required=True, help='The target name to download from.')
  parser.add_argument(
      '--artifact',
      action='append',
      default=[],
      help='The name of the artifact to download. '
      'Can be specified multiple times.')
  parser.add_argument(
      '--regex',
      action='append',
      default=[],
      help='A regex pattern to compare to the names of the artifact to '
      'download. Can be specified multiple times.')

  parser.add_argument(
      '--out_dir',
      default='out/artifacts/',
      help='Path to store fetched artifact to.')

  group = parser.add_mutually_exclusive_group(required=True)
  group.add_argument(
      '--branch', help='Download from the latest build of this branch.')
  group.add_argument('--build_id', help='Download from the specified build.')

  args = parser.parse_args()
  client = fetcher_lib.create_client_from_json_keyfile(
      json_keyfile_name=args.json_keyfile)

  build_id = fetcher_lib.get_latest_build_id(
      client=client, branch=args.branch,
      target=args.target) if args.branch else args.build_id

  for artifact in args.artifact:
    fetcher_lib.fetch_artifact(
        client=client,
        build_id=build_id,
        target=args.target,
        resource_id=artifact,
        dest=os.path.join(args.out_dir, artifact))

  for re in args.regex:
    fetcher_lib.fetch_artifacts(
        client=client,
        build_id=build_id,
        target=args.target,
        pattern=re,
        out_dir=args.out_dir)


if __name__ == '__main__':
  main()
