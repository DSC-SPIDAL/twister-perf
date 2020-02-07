#!/bin/bash

jobID=`cat $HOME/.twister2/last-job-id.txt`

# jobID can be provided from command line also
if [ $# -eq "1" ]; then
  jobID=$1
  echo "JobID provided from command line."
fi

echo jobID: $jobID

logsDir=logs-t2

# remove pids.txt file if exist
rm -f pids.txt 2>/dev/null

logsDir=${logsDir}/${jobID}
mkdir $logsDir 2>/dev/null
echo "created logs directory: $logsDir"

label=twister2-job-pods=t2pod-lb-${jobID}
pods=$(kubectl get pods -l $label --output=jsonpath={.items..metadata.name})
readarray -t podArray <<<"$pods"

for pod in $podArray; do
  podLogFile=${logsDir}/${pod}.log
  echo $podLogFile
  kubectl logs --follow $pod &> ${podLogFile} &
  echo $! >> pids.txt
done
