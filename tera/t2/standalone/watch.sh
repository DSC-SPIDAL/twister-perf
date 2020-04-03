#!/bin/bash

while true; do 
  sinfo -N | grep -c compute
  sleep 5
done

