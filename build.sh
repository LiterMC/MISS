#!/usr/bin/env bash

cd "$(dirname "$0")"

rm -rf fabric/build/libs/* forge/build/libs/*
# rm -rf output

export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew --daemon build

mkdir output
echo "Copying files"
cp fabric/build/libs/* output/
cp forge/build/libs/* output/
echo "File copied, exit code: " $?
