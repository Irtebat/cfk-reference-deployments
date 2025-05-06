# Deploy Confluent Platform + Flink using CFK and CMF

This guide walks through the setup of Confluent Platform and Apache Flink. As reference, the deployment is done on Azure Kubernetes Service. It covers infrastructure provisioning, platform deployment, validation, and teardown.

---

## Step 1: Create an Azure Resource Group

Azure resource groups act as containers for resources. Create one in the desired region.

```bash
az login
az group create --name {rg-group} --location {location}
```
Note: Choose a region that supports the required VM SKU

---

## Step 2: Setup k8s Cluster 

This step sets up the AKS cluster with a base system node pool.

```bash
az aks create \
  --resource-group {rg-group} \
  --name {aks-cluster-name} \
  --location {location} \
  --node-count 2 \
  --node-vm-size Standard_D8ds_v5 \
  --generate-ssh-keys \
  --nodepool-name agentpool \
  --enable-cluster-autoscaler \
  --min-count 1 \
  --max-count 2 \
  --kubernetes-version 1.31.6 \
  --nodepool-taints CriticalAddonsOnly=true:NoSchedule \
  --no-wait \
  --enable-private-cluster
```

---

## Step 3: Deploy All Node Pools for Confluent Platform Components

Use the following template to deploy Confluent Platform components
```bash
az aks nodepool add \
  --name {aks-cluster-name} \
  --name <component> \
  --node-count <> \
  --node-vm-size <> \
  --enable-cluster-autoscaler \
  --min-count <> \
  --max-count <> \
  --labels app-confluent=<> \
  --no-wait
```

**About the components:**
- cfkoperator: Hosts CFK Operato
- kraft: Hosts KRaft controllers 
- kafka: Hosts Kafka brokers 
- sr, connect, c3 : Host respective components.
- flinkoperator: Host Flink  operator
- cmfoperator: Host CMF operator
- flink : Host JobManager and TaskManager pods


**CFK Operator node pool:**
```bash
az aks nodepool add \
  --resource-group {rg-group} \
  --cluster-name {aks-cluster-name}  \
  --name cfkoperator \
  --node-count 1 \
  --node-vm-size Standard_D4as_v5 \
  --enable-cluster-autoscaler \
  --min-count 1 \
  --max-count 1 \
  --labels app-confluent=cfkoperator \
  --no-wait
```

**Kraft node pool:**
```bash
az aks nodepool add \
  --resource-group {rg-group} \
  --cluster-name {aks-cluster-name}  \
  --name kraft \
  --node-count 3 \
  --node-vm-size Standard_D8as_v5 \
  --enable-cluster-autoscaler \
  --min-count 3 \
  --max-count 4 \
  --labels app-confluent=kraft \
  --no-wait
```

**Kafka Broker node pool**
```bash
az aks nodepool add \
  --resource-group {rg-group} \
  --cluster-name {aks-cluster-name}  \
  --name kafka \
  --node-count 3 \
  --node-vm-size Standard_E32bds_v5 \
  --enable-cluster-autoscaler \
  --min-count 3 \
  --max-count 4 \
  --labels app-confluent=kafka-broker \
  --no-wait
```
**Schema Registry node pool**
```bash
az aks nodepool add \
  --resource-group {rg-group} \
  --cluster-name {aks-cluster-name}  \
  --name sr \
  --node-count 2 \
  --node-vm-size Standard_D4as_v5 \
  --enable-cluster-autoscaler \
  --min-count 2 \
  --max-count 3 \
  --labels app-confluent=sr \
  --no-wait
```

**Connect node pool:**
```bash 
az aks nodepool add \
  --resource-group {rg-group} \
  --cluster-name {aks-cluster-name}  \
  --name connect \
  --node-count 2 \
  --node-vm-size Standard_D8as_v5 \
  --enable-cluster-autoscaler \
  --min-count 2 \
  --max-count 3 \
  --labels app-confluent=connect \
  --no-wait
```

**Create Control Center node pool:**
```bash 
az aks nodepool add \
  --resource-group {rg-group} \
  --cluster-name {aks-cluster-name}  \
  --name c3 \
  --node-count 1 \
  --node-vm-size Standard_E16as_v5 \
  --enable-cluster-autoscaler \
  --min-count 1 \
  --max-count 2 \
  --labels app-confluent=c3 \
  --no-wait
```

**Flink Kubernetes Operator node pool:**
```bash
az aks nodepool add \
  --resource-group {rg-group} \
  --cluster-name {aks-cluster-name}  \
  --name flinkop \
  --node-count 1 \
  --node-vm-size Standard_D4as_v5 \
  --enable-cluster-autoscaler \
  --min-count 1 \
  --max-count 1 \
  --labels app-confluent=flinkoperator \
  --no-wait
```
**Confluent Manager for Apache Flink Operator node pool**
```bash
az aks nodepool add \
  --resource-group {rg-group} \
  --cluster-name {aks-cluster-name}  \
  --name cmfoperator \
  --node-count 1 \
  --node-vm-size Standard_D4as_v5 \
  --enable-cluster-autoscaler \
  --min-count 1 \
  --max-count 2 \
  --labels app-confluent=cmfoperator \
  --no-wait
```
**Flink TaskManager and JobManager node pool:** 
```bash 
az aks nodepool add \
  --resource-group {rg-group} \
  --cluster-name {aks-cluster-name}  \
  --name taskmanager \
  --node-count 4 \
  --node-vm-size Standard_E32bds_v5 \
  --node-osdisk-type Ephemeral \
  --enable-cluster-autoscaler \
  --min-count 4 \
  --max-count 5 \
  --labels app-confluent=flink \
  --no-wait
```
---

## Step 4: Deploy Confluent for Kubernetes Operator

```bash
kubectl create namespace confluent

kubectl config set-context --current --namespace confluent

helm repo add confluentinc https://packages.confluent.io/helm

helm upgrade --install confluent-operator confluentinc/confluent-for-kubernetes --namespace confluent -f ./operator_values/cfk-operator_values.yaml --set enableCMFDay2Ops=true --set namespaced=false
```

Check that the operator pod is running:

```bash
kubectl get pods
```

---

## Step 5: Review and Deploy Confluent Platform

Prepare the configuration:

- Replace placeholders in `confluent-platform.yaml`
- Apply required storage class (optional)

```bash
kubectl apply -f storage-class.yaml
kubectl apply -f confluent-platform.yaml
```

Validate deployment:

```bash
kubectl get pods
```

---

## Step 6: Validate Using Producer and Consumer CLI

From an Azure VM:

```bash
# Install CLI tools and dependencies
curl -O https://packages.confluent.io/archive/7.9/confluent-7.9.0.tar.gz
tar xzf confluent-7.9.0.tar.gz
export CONFLUENT_HOME=~/confluent-7.9.0
export PATH=$PATH:$CONFLUENT_HOME/bin
sudo apt install default-jre

# Create topic and test
kafka-topics --create ...
kafka-console-producer ...
kafka-console-consumer ...
```

Access Control Center UI:
```bash
kubectl port-forward controlcenter-0 9021:9021
```

Browse: [http://localhost:9021](http://localhost:9021)

---

## Step 7: Deploy Flink Operators

Create a namespace for flink workloads:
```
kubectl create namespace flink
```
**Set up cert-manager and install Flink kubernetes operator**
```bash
kubectl create -f https://github.com/jetstack/cert-manager/releases/download/v1.8.2/cert-manager.yaml

helm upgrade --install cp-flink-kubernetes-operator confluentinc/flink-kubernetes-operator -n flink -f ./operator_values/flink-operator_values.yaml
```
***Customizing Flink CMF Helm Chart with Node Placement Rules***
This customization introduces targeted scheduling rules for the Confluent Manager for Flink by modifying the Helm chart.

1. Pull and extract the helm chart for confluent-manager-for-apache-flink:
```bash
helm pull confluentinc/confluent-manager-for-apache-flink
tar xvf ...
```

2. In the Helm chart’s templates/deployment.yaml, the following was injected spec.template.spec:
```bash
      {{- if .Values.operatorPod.nodeSelector }}
      nodeSelector: {{ toYaml .Values.operatorPod.nodeSelector | nindent 8 }}
      {{- end }}
      {{- if .Values.operatorPod.affinity }}
      affinity: {{ toYaml .Values.operatorPod.affinity | nindent 8 }}
      {{- end }}
      {{- with .Values.operatorPod.tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
```
3. Install the operator
```bash
helm upgrade --install cmf confluentinc/confluent-manager-for-apache-flink --namespace flink -f ./operator_values/cmf-operator_values.yaml
```
Verify the operator pods are running
```bash
kubectl get pods -n flink
```
---

## Step 8: Deploy Flink Applications

Update the Flink application configuration with Azure blob storage access keys.

```bash
kubectl apply -f cmfrestclass.yaml

kubectl apply -f flink-env.yaml

kubectl apply -f flink-app.yaml
```

Access Flink Web UI:
```bash
kubectl port-forward svc/flink-app-rest 8081:8081 -n flink

```
Note: service name is constructed as <flinkApplication.metadata.name>-rest
```bash
curl -X GET localhost:8081/overview
```
---
## Step 9: Deploy Replicator

**Generate replicator principal and keytab:**  
Example:
```
kadmin.local -q "addprinc -randkey replicator.example.com@example.com"
kadmin.local -q "ktadd -k /root/krb/kafka-broker-0.keytab -e aes256-cts-hmac-sha1-96:normal replicator.example.com@example.com"
```

**Create a replicator keytab secret:**
```bash
kubectl create secret generic replicator-keytab-secret \
  --from-file=replicator.keytab=/path/to/replicator.keytab \
  -n confluent
```

**Verify the secret:**

```bash
kubectl get secret replicator-keytab -n confluent -o yaml
```

**Create a configmap for krb5.conf as follows:**

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
**Build a custom connect image and reference it:**
Build a custom image based on `cp-server-connect` that includes the **Replicator plugin** pre-installed.
```bash
docker build . -t <repo>/<custom-image-name>  -f ./replicator/Dockerfile --platform=linux/amd64
```
Push to an internal registry appropriately 

Update ./replicator/connect.yaml to refer to this updated image

**Deploy Connect Cluster and Start Replicator**

```bash
kubectl apply -f ./replicator/connect.yaml
kubectl apply -f ./replicator/replicator.yaml
```

## Step 10: Tear Down

Clean up all deployed components:

```bash
kubectl delete -f flink-app.yaml
kubectl delete -f flink-env.yaml
kubectl delete -f cmfrestclass.yaml
helm delete cmf -n confluent
helm delete cp-flink-kubernetes-operator -n confluent
kubectl delete -f confluent-platform.yaml
kubectl delete -f storage-class.yaml
helm uninstall confluent-operator -n confluent
```