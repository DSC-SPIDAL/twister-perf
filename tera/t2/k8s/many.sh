#!/bin/bash

instances=16

for i in 1 3 5 10 15 20 25 30 ; do

  # run three times
  for j in 1 2 3; do
    ./disk-tera.sh $instances $i
  done

done
