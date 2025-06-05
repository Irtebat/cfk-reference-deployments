# Overview
This document outlines the monitoring setup for the Confluent Kafka deployment.

### Prerequisites
A Prometheus and Grafana stack (e.g., via kube-prometheus-stack) is already deployed in the cluster.
Prometheus is allowed network access to the exposed metric ports on Confluent component pods.

### JMX Metrics Exposure
All Confluent components are deployed with JMX metrics enabled. These metrics are exposed on each pod via the following endpoints:

Port 7203: Native JMX metrics (for internal tools or direct collection).
Port 7778: Prometheus-compatible metrics via JMX Exporter.

### Prometheus Configuration
Prometheus Configuration
Metrics scraping configuration is defined in:
./monitoring/prometheus-configmap.yaml
Ensure Prometheus is set to scrape the relevant ports as specified.

### Grafana Dashboards
Pre-built dashboard JSON for Grafana is available at:
./monitoring/confluent-cfk.json
