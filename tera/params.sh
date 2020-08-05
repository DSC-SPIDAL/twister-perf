# update all three parameters

export SPARK_HOME="${HOME}/spark-perf/spark-3.0.0-preview2-bin-hadoop2.7"

# can get master ip address by following command
# but could not get it work with variable assignment strangely
# master=$(kubectl cluster-info | head -n 1 | awk '{ print $NF }')
#K8S_MASTER=k8s://https://ip:port

export T2_HOME=${HOME}/twister2/twister2-0.8.0-SNAPSHOT
