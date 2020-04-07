#!/bin/bash

if [ $# -ne "2" ]; then
  echo "Please provide following parameters: numberOfWorkers dataSizePerWorkerGB"
  exit 1
fi

# twister2 has 1gb of data for each worker
# terasort data will be transferred to disk if over 2GB
# so, no disk will be used

./tera.sh $1 $2 2000

