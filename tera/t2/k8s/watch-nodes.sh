#!/bin/bash

while true; do
  kubectl get nodes | grep -c node
  sleep 10
done
