#!/bin/bash

# twister2 uses 4gb of memory
# each workers keeps 200MB of terasort data in memory
# it saves extra data to disk.
# the disk is configured as /dev/shm, it is also memory based 

if [ $# -ne "2" ]; then
  echo "Please provide following parameters: nodes workersPerNode"
  exit 1
fi

nodes=$1
workersPerNode=$2
workers=$((nodes * workersPerNode))

dataSizePerWorkerGB=1
memoryBytesLimitMB=200

./tera.sh $workers $dataSizePerWorkerGB $memoryBytesLimitMB

