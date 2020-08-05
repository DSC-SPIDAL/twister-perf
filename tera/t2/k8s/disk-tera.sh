#!/bin/bash

if [ $# -ne "2" ]; then
  echo "Please provide following parameters: instances workersPerPod"
  exit 1
fi

# memory bytes limit 200mb
# it will go to disk after 200mb on each worker, we use memory based disk
# 4gb jvm memory, 2gb extra pod memory for ram based disk and mpi
# each worker is sorting 1gb of data

dataSizePerWorkerGB=1
memoryBytesLimitMB=200

./tera.sh $1 $2 $dataSizePerWorkerGB $memoryBytesLimitMB

