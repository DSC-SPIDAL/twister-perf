#!/bin/bash

# twister2 uses 4gb of memory
# each workers keeps 200MB of terasort data in memory
# it saves extra data to disk.
# the disk is configured as /dev/shm, it is also memory based 

if [ $# -ne "2" ]; then
  echo "Please provide following parameters: numberOfWorkers dataSizePerWorkerGB"
  exit 1
fi

./tera $1 $2 200

