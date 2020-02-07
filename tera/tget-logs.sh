#!/bin/bash

if [ $# -ne "2" ]; then
  echo "Please provide following parameters: numberOfWorkers"
  exit 1
fi

workers=$1
logsDir=logs-t2

# remove pids.txt file if exist
rm -f pids.txt 2>/dev/null

jobID=`cat $HOME/.twister2/last-job-id.txt`
logsDir=${logsDir}/jobID
mkdir $logsDir 2>/dev/null
echo "created logs directory: $logsDir"

jm=${jobID}-jm-0
kubectl logs --follow ${jm} &> ${logsDir}/${jm}.log &
echo $! >> pids.txt

for (( i=0; i<workers; i++)); do
  pod=${jobID}-0-${i}
  echo $pod
  kubectl logs --follow $pod &> ${logsDir}/${pod}.log &
  echo $! >> pids.txt
done
