# Confluent Platform Deployment on Kubernetes with External Access (KRaft Mode)

This guide walks through the steps to deploy Confluent Platform using Confluent for Kubernetes operator, with Kafka and Control Center exposed via external LoadBalancer services. The deployment runs in KRaft mode and assumes you are operating in a k8s environment that supports `LoadBalancer` services.


## Installation Steps

### Step 1: Create Namespaces
```bash
kubectl create namespace confluent
```

### Step 2: Set the Default Namespace
```bash
kubectl config set-context --current --namespace confluent
```

### Step 3: Label Kubernetes Nodes
Label nodes to match the affinity rules in your CRs:
```bash
kubectl label nodes <node-name> nodetype=operator
kubectl label nodes <node-name> nodetype=kraft
kubectl label nodes <node-name> nodetype=controlcenter
```

### Step 4: Add the Confluent Helm Repository
```bash
helm repo add confluentinc https://packages.confluent.io/helm
helm repo update
```

### Step 5: Install Confluent for Kubernetes Operator
```bash
helm upgrade --install confluent-operator confluentinc/confluent-for-kubernetes \ 
  --set namespaced=true \
  --namespace confluent -f values.yaml
```

### Step 6: Verify Operator Installation
```bash
kubectl get pods -n confluent
```

### Step 7: Configure DNS for External Access

Once the LoadBalancer services are provisioned, map your DNS entries to the external IPs assigned to each Kafka broker and Control Center instance.

Example DNS mappings (replace `$DOMAIN` with your actual domain):

Assuming the following externalAccess configuration in the CR:
externalAccess:
  type: loadBalancer
  loadBalancer:
    domain: <your-domain>

Your DNS mappings should be:
kafka.<your-domain> : The EXTERNAL-IP value of kafka-bootstrap-lb service
```
b0.<your-domain> : The EXTERNAL-IP value of kafka-0-lb service
b1.<your-domain> : The EXTERNAL-IP value of kafka-1-lb service
b2.<your-domain> : The EXTERNAL-IP value of kafka-2-lb service
controlcenter.<your-domain> : The EXTERNAL-IP value of controlcenter-bootstrap-lb service
```

Ensure these DNS entries are resolvable by external clients

### Step 8: Create a Reverse DNS Zone 
Create PTR records
( This is essential for Client Authentication via Kerberos )
```
The EXTERNAL-IP value of kafka-bootstrap-lb service : kafka.<your-domain>
The EXTERNAL-IP value of kafka-0-lb service : b0.<your-domain> 
The EXTERNAL-IP value of kafka-1-lb service : b1.<your-domain>
The EXTERNAL-IP value of kafka-2-lb service : b2.<your-domain>
```
### Step 10: Validate External Connectivity

Verify DNS resolution:
```bash
nslookup kafka.<your-domain>
```
---
### Step 11:  Create Keytab secret

**Keytab for Kafka Brokers**
Note: Refer to keytab-setup.readme for reference on how to generate the keytab

```bash
kubectl create secret generic kafka-keytab-secret \
  --from-file=kafka.keytab=/path/to/keytab/kafka.keytab \
  -n confluent
```

<!-- **Keytab for Control Center**
```bash
kubectl create secret generic c3-keytab-secret \
  --from-file=c3.keytab=/path/to/keytab/c3.keytab \
  -n confluent
``` -->

### Step 12: Create Configmaps 

**Create a shared configmap for kafka-jaas-configs**
```bash
kubectl create configmap kafka-jaas-configs \
  --from-file=broker-0-jaas.conf=./jaas/broker-0-jaas.conf \
  --from-file=broker-1-jaas.conf=./jaas/broker-1-jaas.conf \
  --from-file=broker-2-jaas.conf=./jaas/broker-2-jaas.conf \
  -n confluent
```

**Create a configmap for krb5.conf as follows**

```bash
apiVersion: v1
kind: ConfigMap
metadata:
  name: kafka-kerberos-config
  namespace: confluent
data:
  krb5.conf: |
    <contents of krb5.conf file>
```

### Step 13: kubectl create configmap kafka-jaas-configs \

```bash
kubectl create configmap kafka-pod-overlay \
  --from-file=pod-template.yaml=extra-init-container.yaml \
  -n confluent
```

### Step 14: Deploy Confluent Platform Components

**Update all the placeholders in main.yaml**
- \<your-domain>

Apply your platform configuration for KRaft mode:
```bash
kubectl apply -f main.yaml
```