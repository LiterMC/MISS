#!/usr/bin/env bash

cd "$(dirname "$0")"

rm fabric/build/libs/* neoforge/build/libs/*
# rm -rf output

./gradlew --daemon build

mkdir output
echo "Copying files"
cp fabric/build/libs/* output/
cp neoforge/build/libs/* output/
echo "File copied, exit code: " $?
