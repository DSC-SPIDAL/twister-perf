#!/bin/bash

if [ $# -ne "1" ]; then
  echo "Please provide following parameters: numberOfWorkers"
  exit 1
fi

if [ -z "$T2_HOME" ]; then
  echo T2_HOME is not set.
  exit 1
fi

logsDir=${PWD}/logs
mkdir $logsDir 2>/dev/null

# copy common and kubernetes config filese to t2 conf directory
cp -f conf/common/* ${T2_HOME}/conf/common/
cp -f conf/kubernetes/* ${T2_HOME}/conf/kubernetes/

# total data size for all workers in GB
workers=$1

${T2_HOME}/bin/twister2 submit kubernetes jar ${T2_HOME}/examples/libexamples-java.jar \
  edu.iu.dsc.tws.examples.basic.HelloWorld  $workers

# the pod that end with "-0-0"
jobID=`cat $HOME/.twister2/last-job-id.txt`
firstPod=${jobID}-0-0

# wait for the first pod to become Running
until kubectl get pod $firstPod 2> /dev/null | grep Running; do
  sleep 1
  echo waiting pod to start
done

echo $firstPod started.

########################################
# wait until sorting finished
logFile=${logsDir}/${firstPod}.log
echo logFile: $logFile

# if unbuffer exists, use it
if hash unbuffer 2>/dev/null; then
  echo running with unbuffer
  unbuffer kubectl logs --follow $firstPod 2>&1 | tee ${logFile}
else
  echo running without unbuffer
  kubectl logs --follow $firstPod 2>&1 | tee ${logFile}
#  kubectl logs --follow $firstPod > ${logFile}
fi

echo saved the log file to: ${logFile}

# no need to kill the job, it is deleted if it completes successfully
# ${T2_HOME}/bin/twister2 kill kubernetes $jobID
