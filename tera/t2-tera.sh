#!/bin/bash

if [ $# -ne "2" ]; then
  echo "Please provide following parameters: numberOfWorkers dataSizePerWorkerGB"
  exit 1
fi

outFile="results-twister2.txt"
logsDir=${PWD}/logs-t2
mkdir $logsDir 2>/dev/null

# copy network.yaml file to t2 conf directory
cp -f conf/network.yaml ${T2_HOME}/conf/common/

# total data size for all workers in GB
workers=$1
dataSizePerWorker=$2
totalData=$( echo $dataSizePerWorker $workers | awk '{print $1 * $2}')

${T2_HOME}/bin/twister2 submit kubernetes jar ${T2_HOME}/examples/libexamples-java.jar \
  edu.iu.dsc.tws.examples.batch.terasort.TeraSort \
  -size $totalData \
  -valueSize 90 \
  -keySize 10 \
  -instances $workers \
  -instanceCPUs 1 \
  -instanceMemory 6144 \
  -sources $workers \
  -sinks $workers \
  -memoryBytesLimit 200000000 \
  -fileSizeBytes 100000000 \
  -volatileDisk 4.0

# the pod that end with "-0-0"
# firstPod=$(kubectl get pods --output=jsonpath={.items..metadata.name} | grep -o "[^[:space:]]*-0-0")
jobID=`cat $HOME/.twister2/last-job-id.txt`
firstPod=${jobID}-0-0

# wait for the first pod to become Running
until kubectl get pod $firstPod 2> /dev/null | grep Running; do
  sleep 0.5;
  echo waiting pod to start;
done

########################################
# wait until sorting finished
logFile=${logsDir}/${firstPod}.log
# if unbuffer exists, use it
if hash unbuffer 2>/dev/null; then
  unbuffer kubectl logs --follow $firstPod 2>&1 | tee ${logFile}
else
#  kubectl logs --follow $firstPod 2>&1 | tee ${logFile}
  echo Getting $firstPod logs to $logFile
  echo waiting it to complete...
  kubectl logs --follow $firstPod > ${logFile}
fi

echo saved the log file to: ${logFile}

# get delay and write it to file
delayLine=$(cat $logFile | grep "Total time for all iterations")
trimmedLine=$(echo $delayLine | awk '{$1=$1};1' )
delay=${trimmedLine##* }

echo -e "${jobID}\t${workers}\t${totalData}\t${delay}" >> $outFile
echo -e "${jobID}\t${workers}\t${totalData}\t${delay}"

# kill the job
${T2_HOME}/bin/twister2 kill kubernetes $jobID
