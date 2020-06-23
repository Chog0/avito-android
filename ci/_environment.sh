#!/usr/bin/env bash

ANDROID_BUILDER_TAG=57bdf7686e

if [[ -z "${DOCKER_REGISTRY+x}" ]]; then
    # using dockerhub for public availability
    IMAGE_ANDROID_BUILDER=avitotech/android-builder:$ANDROID_BUILDER_TAG
else
    # using in-house proxy for performance
    IMAGE_ANDROID_BUILDER=${DOCKER_REGISTRY}/android/builder:$ANDROID_BUILDER_TAG
fi

IMAGE_DOCKER_IN_DOCKER=${DOCKER_REGISTRY}/android/docker-in-docker-image:c2ecce3a3e
