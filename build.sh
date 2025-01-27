#!/usr/bin/env bash

cd "$(dirname "$0")"

rm fabric/build/libs/* forge/build/libs/*
rm -rf output

./gradlew --daemon build

mkdir output
cp fabric/build/libs/* output/
cp forge/build/libs/* output/
