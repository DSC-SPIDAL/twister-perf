#!/bin/bash

if [ $# -ne "2" ]; then
  echo "Please provide following parameters: nodesFileName workersPerNode"
  exit 1
fi

fn=$1
workersPerNode=$2
temp1=temp-nodes1
temp2=temp-nodes2

appendText=" slots=${workersPerNode}"

cat $fn | awk '{print $1}' > $temp1

awk -v a="$appendText" '{print $0, a}' $temp1 > $temp2

mv $temp2 $fn
rm $temp1
