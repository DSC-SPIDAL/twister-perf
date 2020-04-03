#!/bin/bash

for i in 128 64 32 16 8 4 1 ; do
  ./disk-tera.sh $i 1
  ./disk-tera.sh $i 1
done
