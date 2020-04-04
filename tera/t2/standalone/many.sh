#!/bin/bash

for i in 1 2 4 8 16 32 64 128 256 512 768 1024 ; do

  # run two times
  for j in 1 2; do
    echo "Running $i workers ..."
    ./mem-tera.sh $i 1
#    ./disk-tera.sh $i 1
  done

done
