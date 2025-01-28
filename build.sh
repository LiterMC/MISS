#!/usr/bin/env bash

cd "$(dirname "$0")"

rm fabric/build/libs/* neoforge/build/libs/*
# rm -rf output

./gradlew --daemon build

mkdir output
cp fabric/build/libs/* output/
cp neoforge/build/libs/* output/
