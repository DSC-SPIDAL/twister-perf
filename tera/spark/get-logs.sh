#!/bin/bash

logsDir=logs-spark

# remove pids.txt file if exist
rm -f pids.txt 2>/dev/null

driver=$(kubectl get pods --selector=spark-role=driver --output=jsonpath={.items..metadata.name})

logsDir=${logsDir}/${driver}
mkdir $logsDir
echo created logs directory: $logsDir

kubectl logs --follow $driver &> ${logsDir}/${driver}.log &
echo $! >> pids.txt

executors=$(kubectl get pods --selector=spark-role=executor --output=jsonpath={.items..metadata.name})
readarray -t pods <<<"$executors"
for pod in $pods; do
  podLogFile=${logsDir}/${pod}.log
  echo $podLogFile
  kubectl logs --follow $pod &> ${podLogFile} &
  echo $! >> pids.txt
done
