#!/bin/sh

echo "=== Flink JobManager Metrics ==="
JM_IP=10.244.15.187
curl -s http://$JM_IP:9249/metrics 

echo "\n=== Flink TaskManager Metrics ==="
TM_IP=10.244.17.168
curl -s http://$TM_IP:9249/metrics | grep -E "checkpointAlignmentTime|numBytes(In|Out)LocalState"
curl -s http://$TM_IP:9249/metrics | grep -E "rocksdb_(block_cache_usage|sst_files|memtable_total)"
curl -s http://$TM_IP:9249/metrics | grep -E "numRecords(In|Out)PerSecond"
curl -s http://$TM_IP:9249/metrics | grep -E "busyTimeMsPerSecond|idleTimeMsPerSecond"
curl -s http://$TM_IP:9249/metrics | grep -E "current(Input|Output)Watermark"
curl -s http://$TM_IP:9249/metrics | grep latency_
curl -s http://$TM_IP:9249/metrics | grep state_backend_state_access_latency

echo "\n=== Flink Operator Metrics ==="
OP_IP=10.244.16.106
curl -s http://$OP_IP:9999/metrics 