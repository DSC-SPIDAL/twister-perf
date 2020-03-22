#!/bin/bash


for workers in 25; do
#  ./t2-tera.sh $workers 1
#  ./t2-tera.sh $workers 1
  ./spark-tera.sh $workers 1
  ./spark-tera.sh $workers 1
done

