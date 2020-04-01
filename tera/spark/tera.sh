#!/bin/bash

if [ $# -ne "2" ]; then
  echo "Please provide following parameters: numberOfWorkers dataSizePerWorkerGB"
  exit 1
fi

outFile="results-spark.txt"
logsDir=${PWD}/logs
mkdir $logsDir 2>/dev/null

workers=$1
dataSizePerWorkerGB=$2

jobName=j1
workerCPU=1.0
mem=6g

#uploadMethod=s3
uploadMethod=pod

if [ $uploadMethod == 's3' ]; then
  pf=spark-perf-1.0.jar
  aws s3 cp ../target/${pf} s3://twister2-scalability-tests/
  url=$(aws s3 presign s3://twister2-scalability-tests/${pf})
elif [ $uploadMethod == 'pod' ]; then
  # uplaod the jar to uploader web server
  kubectl cp ../target/twister-perf-0.1.0-SNAPSHOT.jar twister2-uploader-0:/usr/share/nginx/html/
  url='http://twister2-uploader/twister-perf-0.1.0-SNAPSHOT.jar'
else
  echo uploadMethod is $uploadMethod It must be either of s3 or pod.
  exit 1
fi

echo Job Package URL: $url

sparkv="3.0.0-preview2"
#sparkv="2.4.4"

parallel=$workers
keySize=10
dataSize=90
writeToFile=false
totalData=$(echo $dataSizePerWorkerGB $workers | awk '{print $1 * $2}' )

${SPARK_HOME}/bin/spark-submit \
    --master "${K8S_MASTER}" \
    --deploy-mode cluster \
    --name "${jobName}" \
    --class org.twister2.perf.shuffle.spark.tera.TeraSortJob \
    --conf spark.driver.memory=2g \
    --conf spark.executor.memory=${mem} \
    --conf spark.executor.instances=${workers} \
    --conf spark.kubernetes.container.image=auyar/spark:v${sparkv} \
    --conf spark.kubernetes.authenticate.driver.serviceAccountName=spark \
    --conf spark.kubernetes.executor.request.cores=${workerCPU} \
    --conf spark.kubernetes.executor.limit.cores=${workerCPU} \
    --conf spark.kubernetes.allocation.batch.size=${workers} \
    $url \
    $dataSizePerWorkerGB $parallel $keySize $dataSize $writeToFile

#    --conf spark.memory.fraction=0.3 \

if [ "$?" -ne "0" ]; then
  echo Spark job failed.
  exit 1
else
  echo Spark job succeeded.
fi

# get spark drivers
driver=$(kubectl get pods --selector=spark-role=driver --output=jsonpath={.items..metadata.name} | grep -o "${jobName}[^[:space:]]*")
echo "driver: $driver"

logFile=${logsDir}/${driver}.log

echo timestamp JobSubmitTime: $submitTime > $logFile

kubectl logs ${driver} >> ${logFile}

echo written logs to: ${logFile}
delayLine=$(grep SortingDelay ${logFile})
delay=${delayLine##* }
echo -e "${driver}\t${mem}\t${workers}\t${totalData}\t${delay}" >> $outFile
echo -e "${driver}\t${mem}\t${workers}\t${totalData}\t${delay}"

echo deleting driver pod and the associated service
./kill.sh $driver
