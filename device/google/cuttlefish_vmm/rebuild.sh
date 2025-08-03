#!/bin/bash

# Common code to build a host image on GCE

# INTERNAL_extra_source may be set to a directory containing the source for
# extra package to build.

# INTERNAL_IP can be set to --internal-ip run on a GCE instance
# The instance will need --scope compute-rw

if [ -z "${ANDROID_BUILD_TOP}" ]; then
  echo "ANDROID_BUILD_TOP is not set, did you forget to lunch?" && exit 1
fi

source "${ANDROID_BUILD_TOP}/external/shflags/shflags"
DIR="${ANDROID_BUILD_TOP}/device/google/cuttlefish_vmm"

# ARM-board options

DEFINE_boolean arm false "Build on an ARM board"
DEFINE_string arm_instance "" "IP address or DNS name of an ARM system to do the secondary build"
DEFINE_string arm_user "vsoc-01" "User to invoke on the ARM system"

# Docker options

DEFINE_boolean docker false "Build inside docker"
DEFINE_boolean docker_persistent true "Build inside a privileged, persistent container (faster for iterative development)"
DEFINE_string docker_arch "$(uname -m)" "Target architectre"
DEFINE_boolean docker_build_image true "When --noreuse is specified, this flag controls building the docker image (else we assume it was built and reuse it)"
DEFINE_string docker_image "docker_vmm" "Name of docker image to build"
DEFINE_string docker_container "docker_vmm" "Name of docker container to create"
DEFINE_string docker_source "" "Path to sources checked out using manifest"
DEFINE_string docker_working "" "Path to working directory"
DEFINE_string docker_output "" "Output directory (when --docker is specified)"
DEFINE_string docker_user "${USER}" "Docker-container user"
DEFINE_string docker_uid "${UID}" "Docker-container user ID"

# GCE options

DEFINE_boolean gce false "Build on a GCE instance"
DEFINE_string gce_arch "$(uname -m)" "Target architecture"
DEFINE_string gce_project "$(gcloud config get-value project)" "Project to use" "p"
DEFINE_string gce_instance "${USER}-build" "Instance name to create for the build" "i"
DEFINE_string gce_user cuttlefish_crosvm_builder "User name to use on GCE when doing the build"
DEFINE_integer gce_vcpus 4 "Instance size (vcpus) to create"
DEFINE_string gce_zone "$(gcloud config get-value compute/zone)" "Zone to use" "z"

# Common options

DEFINE_string manifest "" "Path to custom manifest to use for the build"
DEFINE_boolean reuse false "Set to true to reuse a previously-set-up instance."
DEFINE_boolean reuse_resync false "Reuse a previously-set-up instance, but clean and re-sync the sources. Overrides --reuse if both are specified."

set -e

SSH_FLAGS=(${INTERNAL_IP})

wait_for_instance() {
  alive=""
  while [[ -z "${alive}" ]]; do
    sleep 5
    alive="$(gcloud compute ssh "${SSH_FLAGS[@]}" "$@" -- uptime || true)"
  done
}

check_common_docker_options() {
  if [[ -z "${FLAGS_docker_image}" ]]; then
    echo Option --docker_image must not be empty 1>&1
    fail=1
  fi
  if [[ -z "${FLAGS_docker_container}" ]]; then
    echo Options --docker_container must not be empty 1>&2
    fail=1
  fi
  if [[ -z "${FLAGS_docker_user}" ]]; then
    echo Options --docker_user must not be empty 1>&2
    fail=1
  fi
  if [[ -z "${FLAGS_docker_uid}" ]]; then
    echo Options --docker_uid must not be empty 1>&2
    fail=1
  fi
  # Volume mapping are specified only when a container is created.  With
  # --reuse, an already-created persistent container is reused, which implies
  # that we cannot change the volume maps.  For non-persistent containers, we
  # use docker run, which creates and runs the continer in one step; in that
  # case, we must pass the same values for --docker_source and --docker_output
  # that we passed when we ran the non-persistent continer the first time.
  if [[ ${_reuse} -eq 1 && ${FLAGS_docker_persistent} -eq ${FLAGS_TRUE} ]]; then
    if [ -n "${FLAGS_docker_source}" ]; then
      echo Option --docker_source may not be specified with --reuse and --docker_persistent 1>&2
      fail=1
    fi
    if [ -n "${FLAGS_docker_working}" ]; then
      echo Option --docker_working may not be specified with --reuse and --docker_persistent 1>&2
      fail=1
    fi
    if [ -n "${FLAGS_docker_output}" ]; then
      echo Option --docker_output may not be specified with --reuse and --docker_persistent 1>&2
      fail=1
    fi
  fi
  if [[ "${fail}" -ne 0 ]]; then
    exit "${fail}"
  fi
}

build_locally_using_docker() {
  check_common_docker_options
  case "${FLAGS_docker_arch}" in
    aarch64) ;;
    x86_64) ;;
    *) echo Invalid value ${FLAGS_docker_arch} for --docker_arch 1>&2
      fail=1
      ;;
  esac
  if [[ "${fail}" -ne 0 ]]; then
    exit "${fail}"
  fi
  local -i _persistent=0
  if [[ ${FLAGS_docker_persistent} -eq ${FLAGS_TRUE} ]]; then
    _persistent=1
  fi

  local -i _build_image=0
  if [[ ${FLAGS_docker_build_image} -eq ${FLAGS_TRUE} ]]; then
    _build_image=1
  fi

  local _docker_output=""
  if [ -z "${FLAGS_docker_output}" ]; then
    _docker_output="${ANDROID_BUILD_TOP}/device/google/cuttlefish_vmm/${FLAGS_docker_arch}-linux-gnu"
  else
    _docker_output="${FLAGS_docker_output}"
  fi

  local _temp="$(mktemp -d)"
  rsync -avR "${relative_source_files[@]/#/${DIR}/./}" "${_temp}"
  if [ -n "${custom_manifest}" ]; then
    cp "${custom_manifest}" "${_temp}"/custom.xml
  else
    touch "${_temp}"/custom.xml
  fi

  ${DIR}/rebuild-docker.sh "${FLAGS_docker_image}" \
                     "${FLAGS_docker_container}" \
                     "${FLAGS_docker_arch}" \
                     "${FLAGS_docker_user}" \
                     "${FLAGS_docker_uid}" \
                     "${_persistent}" \
                     "x${FLAGS_docker_source}" \
                     "x${FLAGS_docker_working}" \
                     "x${_docker_output}" \
                     "${_reuse}" \
                     "${_build_image}" \
                     "${_temp}/Dockerfile" \
                     "${_temp}" \
                     "${#docker_flags[@]}" "${docker_flags[@]}" \
                     "${#_prepare_source[@]}" "${_prepare_source[@]}"

  rm -rf "${_temp}"
}

function build_on_gce() {
  check_common_docker_options
  if [[ "${FLAGS_gce_arch}" != "${FLAGS_docker_arch}" ]]; then
    echo Docker arch must match gce arch 1>&2
    fail=1
  fi
  local _image_family=""
  local _machine_type=""
  case "${FLAGS_gce_arch}" in
    aarch64)
      _image_family=debian-11-arm64
      _machine_type=t2a-standard
      ;;
    x86_64)
      _image_family=debian-11
      _machine_type=n1-standard
      ;;
    *) echo Invalid value ${FLAGS_gce_arch} for --gce_arch 1>&2
      fail=1
      ;;
  esac
  if [[ -z "${FLAGS_gce_instance}" ]]; then
    echo Must specify instance 1>&2
    fail=1
  fi
  if [[ -z "${FLAGS_gce_project}" ]]; then
    echo Must specify project 1>&2
    fail=1
  fi
  if [[ -z "${FLAGS_gce_zone}" ]]; then
    echo Must specify zone 1>&2
    fail=1
  fi
  if [[ "${fail}" -ne 0 ]]; then
    exit "${fail}"
  fi
  project_zone_flags=(--project="${FLAGS_gce_project}" --zone="${FLAGS_gce_zone}")
  if [ ${_reuse} -eq 0 ]; then
    delete_instances=("${FLAGS_gce_instance}")
    gcloud compute instances delete -q \
      "${delete_instances[@]}" \
      "${project_zone_flags[@]}" || \
        echo Instance does not exist
    gcloud compute images delete -q \
      "${delete_instances[@]/%/-image}" \
      --project "${FLAGS_gce_project}" || \
        echo Image does not exist
    gcloud compute disks delete -q \
      "${delete_instances[@]/%/-disk}" \
      "${project_zone_flags[@]}" || \
        echo Disk does not exist

    gcloud compute disks create \
      "${delete_instances[@]/%/-disk}" \
      "${project_zone_flags[@]}" \
      --image-project="debian-cloud" \
      --image-family="${_image_family}"
    gcloud compute images create \
      "${delete_instances[@]/%/-image}" \
      --source-disk "${delete_instances[@]/%/-disk}" \
      --project "${FLAGS_gce_project}" --source-disk-zone "${FLAGS_gce_zone}"
    gcloud compute instances create \
      "${delete_instances[@]}" \
      "${project_zone_flags[@]}" \
      --image "${delete_instances[@]/%/-image}" \
      --boot-disk-size=200GB \
      --machine-type="${_machine_type}-${FLAGS_gce_vcpus}" \
      --network-interface=nic-type=GVNIC

    wait_for_instance "${FLAGS_gce_instance}" "${project_zone_flags[@]}"

    # install docker
    gcloud beta compute ssh "${SSH_FLAGS[@]}" \
        "${project_zone_flags[@]}" \
        "${FLAGS_gce_user}@${FLAGS_gce_instance}" -- \
        'curl --retry 10 --retry-all-errors -fsSL https://get.docker.com | /bin/bash'
    gcloud beta compute ssh "${SSH_FLAGS[@]}" \
        "${project_zone_flags[@]}" \
        "${FLAGS_gce_user}@${FLAGS_gce_instance}" -- \
      sudo usermod -aG docker "${FLAGS_gce_user}"

    # beta for the --internal-ip flag that may be passed via SSH_FLAGS

    gcloud beta compute ssh "${SSH_FLAGS[@]}" \
        "${project_zone_flags[@]}" \
        "${FLAGS_gce_user}@${FLAGS_gce_instance}" -- \
        mkdir -p '$PWD/docker $PWD/docker/source $PWD/docker/working $PWD/docker/output'

    tar czv -C "${DIR}" -f - "${relative_source_files[@]}" | \
      gcloud beta compute ssh "${SSH_FLAGS[@]}" \
          "${project_zone_flags[@]}" \
          "${FLAGS_gce_user}@${FLAGS_gce_instance}" -- \
          'tar xzv -C ~/docker -f -'
    if [ -n "${custom_manifest}" ]; then
      gcloud beta compute scp "${SSH_FLAGS[@]}" \
        "${project_zone_flags[@]}" \
        "${custom_manifest}" \
        "${FLAGS_gce_user}@${FLAGS_gce_instance}:~/docker/custom.xml"
    else
      gcloud beta compute ssh "${SSH_FLAGS[@]}" \
        "${project_zone_flags[@]}" \
        "${FLAGS_gce_user}@${FLAGS_gce_instance}" -- \
        "touch ~/docker/custom.xml"
    fi
  fi

  local _status=$(gcloud compute instances list \
                  --project="${FLAGS_gce_project}" \
                  --zones="${FLAGS_gce_zone}" \
                  --filter="name=('${FLAGS_gce_instance}')" \
                  --format=flattened | awk '/status:/ {print $2}')
  if [ "${_status}" != "RUNNING" ] ; then
    echo "Instance ${FLAGS_gce_instance} is not running."
    exit 1;
  fi

  local -i _persistent=0
  if [[ ${FLAGS_docker_persistent} -eq ${FLAGS_TRUE} ]]; then
    _persistent=1
  fi
  local -i _build_image=0
  if [[ ${FLAGS_docker_build_image} -eq ${FLAGS_TRUE} ]]; then
    _build_image=1
  fi
  gcloud beta compute ssh "${SSH_FLAGS[@]}" \
      "${project_zone_flags[@]}" \
      "${FLAGS_gce_user}@${FLAGS_gce_instance}" -- \
      ./docker/rebuild-docker.sh "${FLAGS_docker_image}" \
                       "${FLAGS_docker_container}" \
                       "${FLAGS_docker_arch}" \
                       '${USER}' \
                       '${UID}' \
                       "${_persistent}" \
                       'x$PWD/docker/source' \
                       'x$PWD/docker/working' \
                       'x$PWD/docker/output' \
                       "${_reuse}" \
                       "${_build_image}" \
                       '~/docker/Dockerfile' \
                       '~/docker/' \
                       "${#docker_flags[@]}" "${docker_flags[@]}" \
                       "${#_prepare_source[@]}" "${_prepare_source[@]}"

  gcloud beta compute ssh "${SSH_FLAGS[@]}" \
      "${project_zone_flags[@]}" \
      "${FLAGS_gce_user}@${FLAGS_gce_instance}" --command \
      'tar czv -C $PWD/docker/output -f - $(find $PWD/docker/output -printf "%P\n")' | \
    tar xzv -C ${DIR}/${FLAGS_docker_arch}-linux-gnu -f -

  gcloud compute disks describe \
    "${project_zone_flags[@]}" "${FLAGS_gce_instance}" | \
      grep ^sourceImage: > "${DIR}"/x86_64-linux-gnu/builder_image.txt
}

function build_on_arm_board() {
  check_common_docker_options
  if [[ "${FLAGS_docker_arch}" != "aarch64" ]]; then
    echo ARM board supports building only aarch64 1>&2
    fail=1
  fi
  if [[ -z "${FLAGS_arm_instance}" ]]; then
    echo Must specify IP address of ARM board 1>&2
    fail=1
  fi
  if [[ -z "${FLAGS_arm_user}" ]]; then
    echo Must specify a user account on ARM board 1>&2
    fail=1
  fi
  if [[ "${fail}" -ne 0 ]]; then
    exit "${fail}"
  fi
  if [[ "${_reuse}" -eq 0 ]]; then
    ssh -t "${FLAGS_arm_user}@${FLAGS_arm_instance}" -- \
      rm -rf '$PWD/docker'
  fi
  rsync -avR -e ssh \
    "${relative_source_files[@]/#/${DIR}/./}" \
    "${FLAGS_arm_user}@${FLAGS_arm_instance}:~/docker/"

  if [ -n "${custom_manifest}" ]; then
    scp "${custom_manifest}" "${FLAGS_arm_user}@${FLAGS_arm_instance}":~/docker/custom.xml
  else
    ssh -t "${FLAGS_arm_user}@${FLAGS_arm_instance}" -- \
      "touch ~/docker/custom.xml"
  fi

  local -i _persistent=0
  if [[ ${FLAGS_docker_persistent} -eq ${FLAGS_TRUE} ]]; then
    _persistent=1
  fi
  local -i _build_image=0
  if [[ ${FLAGS_docker_build_image} -eq ${FLAGS_TRUE} ]]; then
    _build_image=1
  fi
  ssh -t "${FLAGS_arm_user}@${FLAGS_arm_instance}" -- \
    mkdir -p '$PWD/docker/source' '$PWD/docker/working' '$PWD/docker/output'
  ssh -t "${FLAGS_arm_user}@${FLAGS_arm_instance}" -- \
    ./docker/rebuild-docker.sh "${FLAGS_docker_image}" \
                     "${FLAGS_docker_container}" \
                     "${FLAGS_docker_arch}" \
                     '${USER}' \
                     '${UID}' \
                     "${_persistent}" \
                     'x$PWD/docker/source' \
                     'x$PWD/docker/working' \
                     'x$PWD/docker/output' \
                     "${_reuse}" \
                     "${_build_image}" \
                     '~/docker/Dockerfile' \
                     '~/docker/' \
                     "${#docker_flags[@]}" "${docker_flags[@]}" \
                     "${#_prepare_source[@]}" "${_prepare_source[@]}"

  rsync -avR -e ssh "${FLAGS_arm_user}@${FLAGS_arm_instance}":docker/output/./ \
    "${ANDROID_BUILD_TOP}/device/google/cuttlefish_vmm/${FLAGS_docker_arch}-linux-gnu"
}

main() {
  set -o errexit
  set -x
  fail=0
  relative_source_files=("rebuild-docker.sh"
     "rebuild-internal.sh"
     "Dockerfile"
     "manifest.xml"
     ".dockerignore")
  # These must match the definitions in the Dockerfile
  docker_flags=("-eSOURCE_DIR=/source" "-eWORKING_DIR=/working" "-eOUTPUT_DIR=/output" "-eTOOLS_DIR=/static/tools")

  if [[ $(( $((${FLAGS_gce}==${FLAGS_TRUE})) + $((${FLAGS_arm}==${FLAGS_TRUE})) + $((${FLAGS_docker}==${FLAGS_TRUE})) )) > 1 ]]; then
    echo You may specify only one of --gce, --docker, or --arm 1>&2
    exit 2
  fi

  if [[ -n "${FLAGS_manifest}" ]]; then
    if [[ ! -f "${FLAGS_manifest}" ]]; then
      echo custom manifest not found: ${FLAGS_manifest} 1>&1
      exit 2
    fi
    custom_manifest="${FLAGS_manifest}"
    docker_flags+=("-eCUSTOM_MANIFEST=/static/custom.xml")
  else
    custom_manifest="${DIR}/manifest.xml"
    docker_flags+=("-eCUSTOM_MANIFEST=/static/manifest.xml")
  fi
  local -a _prepare_source=(setup_env fetch_source);
  local -i _reuse=0
  if [[ ${FLAGS_reuse} -eq ${FLAGS_TRUE} ]]; then
    # neither install packages, nor sync sources; skip to building them
    _prepare_source=(setup_env)
    # unless you're setting up a non-persistent container and --docker_source is
    # the empty string; in this case, --reuse implies --reuse_resync
    if [[ "${FLAGS_docker_persistent}" -eq ${FLAGS_FALSE} && \
          -z "${FLAGS_docker_source}" ]]; then
      _prepare_source+=(resync_source)
    fi
    _reuse=1
  fi
  if [[ ${FLAGS_reuse_resync} -eq ${FLAGS_TRUE} ]]; then
    # do not install packages but clean and sync sources afresh
    _prepare_source=(setup_env resync_source);
    _reuse=1
  fi
  if [[ ${FLAGS_gce} -eq ${FLAGS_TRUE} ]]; then
    build_on_gce
    exit 0
    gcloud compute instances delete -q \
      "${project_zone_flags[@]}" \
      "${FLAGS_gce_instance}"
  fi
  if [[ ${FLAGS_arm} -eq ${FLAGS_TRUE} ]]; then
    build_on_arm_board
    exit 0
  fi
  if [[ ${FLAGS_docker} -eq ${FLAGS_TRUE} ]]; then
    build_locally_using_docker
    exit 0
  fi
}

FLAGS "$@" || exit 1
main "${FLAGS_ARGV[@]}"
