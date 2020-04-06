#!/bin/bash

if [ $# -ne "2" ]; then
  echo "Please provide following parameters: numberOfWorkers dataSizePerWorkerGB"
  exit 1
fi

# memory bytes limit 200mb
# it will go to disk after 200mb on each worker, we use memory based disk
# 4gb jvm memory, 2gb extra pod memory for ram based disk and mpi

outFile="results.txt"
logsDir=${PWD}/logs
mkdir $logsDir 2>/dev/null

# copy common and kubernetes config filese to t2 conf directory
cp -f conf/common/* ${T2_HOME}/conf/common/
cp -f conf/kubernetes/* ${T2_HOME}/conf/kubernetes/

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
  -instanceMemory 4096 \
  -sources $workers \
  -sinks $workers \
  -memoryBytesLimit 200000000 \
  -fileSizeBytes 100000000 \
  -volatileDisk 1.0

# the pod that end with "-0-0"
jobID=`cat $HOME/.twister2/last-job-id.txt`
firstPod=${jobID}-0-0

logFile=$(find $HOME/.twister2/${jobID} -name "worker0-*")

# get delay and write it to file
delayLine=$(cat $logFile | grep "Total time for all iterations")
trimmedLine=$(echo $delayLine | awk '{$1=$1};1' )
delay=${trimmedLine##* }

echo -e "${jobID}\t${workers}\t${totalData}\t${delay}" >> $outFile
echo -e "${jobID}\t${workers}\t${totalData}\t${delay}"

# no need to kill the job, it is deleted if it completes successfully
# ${T2_HOME}/bin/twister2 kill kubernetes $jobID
