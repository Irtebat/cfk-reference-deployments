# Overview
This guide provides the steps to deploy Confluent Platform using the Confluent for Kubernetes Operator with the following configuration profile:

##### Platform Profile
- CP version : 7.9
- CFK version : 2.11.0
- Deployment runs in KRaft mode
- Components :
  - Kafka Brokers
  - KRaft Controllers
- Workload Scheduling: 
  - This reference uses NodeAffinity and oneReplicaPerNode. Refer <a href="https://docs.confluent.io/operator/current/co-schedule-workloads.html#:~:text=Pod%20topology%20constraints%20only%20apply%20to%20pods%20within%20the%20same%20namespace.&text=You%20can%20configure%20CFK%20to,running%20on%20the%20same%20node.">here</a> for details on Pod scheduling in CFK and other available options.
  - The configured values in manifest files, lead to co-location of component. Update the scheudling to match your requirement.

##### Security profile

Confluent Platform cluster is setup with the following security:
- TLS1.2 network encryption with user provided certificates
- Authentication 
  - PLAINTEXT : for inter-cp-component communication
  - Kerberos : kafka client to Broker communication
- Authorization : None

##### Network Profile
```
Client (VM or service in VNet)
    ↕
Azure Load Balancer (ILB) with IP accessible to client
    ↕
Kafka Pods ( Access configured via LoadBalancer Service )
```
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