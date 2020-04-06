#!/bin/bash

if [ "$1" = "mem" ]; then
  # run only in memory without using disk
  memDataSize=2000
elif [ "$1" = "disk" ]; then
  # run using disk for more than 200MB of data
  memDataSize=200
else
  echo "Please provide either: mem/disk"
  exit 1
fi

echo memDataSize: $memDataSize MB

for i in 1 2 4 8 16 32 64 128 256 512 ; do

  # run two times
  for j in 1 2; do
    ./tera.sh $i 1 $memDataSize
  done

done
