#!/bin/bash

if [ $# -ne "2" ]; then
  echo "Please provide following parameters: numberOfWorkers dataSizePerWorkerGB"
  exit 1
fi

outFile="results.txt"
logsDir=${PWD}/logs
mkdir $logsDir 2>/dev/null
logFile1=${logsDir}/current.log

# copy common and standalone config filese to t2 conf directory
cp -f conf/common/* ${T2_HOME}/conf/common/
cp -f conf/standalone/* ${T2_HOME}/conf/standalone/

# total data size for all workers in GB
workers=$1
dataSizePerWorker=$2
totalData=$( echo $dataSizePerWorker $workers | awk '{print $1 * $2}')

${T2_HOME}/bin/twister2 submit standalone jar ${T2_HOME}/examples/libexamples-java.jar \
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
  -volatileDisk 4.0 \
  2>&1 | tee ${logFile1}

# the pod that end with "-0-0"
jobID=`cat $HOME/.twister2/last-job-id.txt`
logFile2=${logsDir}/${jobID}.log
mv $logFile1 $logFile2
echo logFile: $logFile2

# get delay and write it to file
delayLine=$(cat $logFile2 | grep "Total time for all iterations")
trimmedLine=$(echo $delayLine | awk '{$1=$1};1' )
delay=${trimmedLine##* }

echo -e "${jobID}\t${workers}\t${totalData}\t${delay}" >> $outFile
echo -e "${jobID}\t${workers}\t${totalData}\t${delay}"

# no need to kill the job, it is deleted if it completes successfully
# ${T2_HOME}/bin/twister2 kill kubernetes $jobID
