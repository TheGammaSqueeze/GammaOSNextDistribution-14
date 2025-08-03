#!/bin/bash -r

function container_exists() {
  [[ $(docker ps -a --filter "name=^/$1$" --format '{{.Names}}') == $1 ]] && echo $1;
}

declare -A map_uname_to_docker_builder_arch=( [aarch64]=linux/arm64 [x86_64]=linux/amd64 )

# inputs
# $1 = image name
# $2 = container name
# $3 = architecture (x86_64 or aarch64)
# $4 = user name
# $5 = user ID
# $6 = persistent?
# $7 = path to sources dir
# $8 = path to working dir
# $9 = path to output dir
# $10 = reuse image/container? (0: no reuse; 1: reuse image; 2: reuse container)
# $11 = build image (when reuse = 0)
# $12 = path to Dockerfile
# $13 = path to docker context dir
# $14 = docker_flags_len
# $15 = (docker_flags)
# $16 = _prepare_source_len
# $17 = (_prepare_source)
function build_with_docker() {
  set -o errexit
  set -x

  local -a _docker_target=( ${1} )
  local _container_name=${2}
  local _arch=${3}
  local _docker_image=${1}_${_arch}
  local _user=${4}
  local _uid=${5}
  local _persistent=${6}

  if [[ ${_persistent} -eq 1 ]]; then
    _docker_image=${1}_${_arch}_persistent
  fi
  local _docker_source=
  if [ "${7}" != 'x' ]; then
    _docker_source="-v ${7#x}:/source:rw"
  fi
  local _docker_working=
  if [ "${8}" != 'x' ]; then
    _docker_working="-v ${8#x}:/working:rw"
  fi
  local _docker_output=
  if [ "${9}" != 'x' ]; then
    _docker_output="-v ${9#x}:/output:rw"
  fi
  local _reuse=${10}
  local _build_image=${11}
  local _dockerfile=${12}
  local _docker_context=${13}
  shift 13
  local -a _args=("$@")
  local -i _docker_flags_len=${_args[0]}
  local -a _docker_flags=("${_args[@]:1:$_docker_flags_len}")
  local -i _prepare_source_len=${_args[(_docker_flags_len+1)]}
  local -a _prepare_source=("${_args[@]:(_docker_flags_len+2):_prepare_source_len}")

  local _build_or_retry=${_arch}_retry

  if [[ ${_reuse} -ne 1 ]]; then
    _build_or_retry=${_arch}_build
    if [[ ${_persistent} -eq 1 ]]; then
      _docker_target+=("${_docker_target[0]}_persistent")
    fi
    if [[ ${_build_image} -eq 1 ]]; then
      if [[ ${_arch} != $(uname -m) ]]; then
        export DOCKER_CLI_EXPERIMENTAL=enabled
        # from
        # https://community.arm.com/developer/tools-software/tools/b/tools-software-ides-blog/posts/getting-started-with-docker-for-arm-on-linux
        docker run --rm --privileged docker/binfmt:820fdd95a9972a5308930a2bdfb8573dd4447ad3
        docker buildx create \
          --name docker_vmm_${_arch}_builder \
          --platform ${map_uname_to_docker_builder_arch[${_arch}]} \
          --use
        docker buildx inspect --bootstrap
        for _target in ${_docker_target[@]}; do
          docker buildx build \
            --progress plain \
            --platform ${map_uname_to_docker_builder_arch[${_arch}]} \
            --target ${_target} \
            -f ${_dockerfile} \
            -t ${_docker_image}:latest \
            ${_docker_context} \
            --build-arg USER=${_user} \
            --build-arg UID=${_uid} --load
        done
        docker buildx rm docker_vmm_${_arch}_builder
        unset DOCKER_CLI_EXPERIMENTAL
      else
        for _target in ${_docker_target[@]}; do
          docker build \
            -f ${_dockerfile} \
            --target ${_target} \
            -t ${_docker_image}:latest \
            ${_docker_context} \
            --build-arg USER=${_user} \
            --build-arg UID=${_uid}
        done
      fi
    fi
    if [[ ${_persistent} -eq 1 ]]; then
      if [[ -n "$(container_exists ${_container_name})" ]]; then
        docker rm -f ${_container_name}
      fi
      docker run \
        -d \
        --platform ${map_uname_to_docker_builder_arch[${_arch}]} \
        --name ${_container_name} \
        -h ${_container_name} \
        ${_docker_source} \
        ${_docker_working} \
        ${_docker_output} \
        ${_docker_image}:latest
    fi
#  else
#    # If we are reusing the docker image, then we cannot change the target
#    # architecture (though we can change the persistence) of the container.
#    echo TODO
  fi

  if [[ ${_persistent} -eq 1 ]]; then
    if [[ "$(docker inspect --format='{{.State.Status}}' ${_container_name})" == "paused" ]]; then
      docker unpause ${_container_name}
    fi
    docker exec -it \
      --user ${_user} \
      ${_docker_flags[@]} \
      ${_container_name} \
      /static/rebuild-internal.sh ${_prepare_source[@]} ${_build_or_retry}
    docker pause ${_container_name}
  else
    $docker_run -it --rm \
      --user ${_user} \
      ${_docker_flags[@]} \
      ${_docker_source} \
      ${_docker_working} \
      ${_docker_output} \
      ${_docker_image}:latest \
      /static/rebuild-internal.sh ${_prepare_source[@]} ${_build_or_retry}
  fi
}

build_with_docker $@
