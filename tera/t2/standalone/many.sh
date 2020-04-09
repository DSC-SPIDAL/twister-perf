#!/bin/bash


nodes=16

for workersPerNode in 1 3 5 10 15 20 25 30 ; do

  # update nodes file with $i slots
  ./update-nodes.sh conf/standalone/nodes $workersPerNode

  # run three times
  for j in 1 2 3; do
    echo "Running with $workersPerNode workers per node..."
    ./disk-tera.sh $nodes $workersPerNode
    
  done

done
