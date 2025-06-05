# Overview
This document outlines the monitoring setup for the Confluent Kafka deployment.

### Assumptions
A Prometheus and Grafana setup is already available within the Kubernetes environment (e.g., via kube-prometheus-stack or a similar deployment).
Network policies or firewalls allow Prometheus to reach the exposed ports on Kafka component pods.

### JMX Metrics Exposure
All Confluent components are deployed with JMX metrics enabled. These metrics are exposed on each pod via the following endpoints:

- JMX Port: 7203
Standard JMX metrics available for internal tools or direct scraping.

- JMX Prometheus Exporter: 7778
Metrics exposed in Prometheus-compatible format.

### Prometheus Configuration
Refer to ./monitoring/prometheus-configmap.yaml
Prometheus should be configured to scrape metrics as described in the manifest yaml

### Grafana Dashboards
Refer to ./monitoring/confluent-cfk.json

