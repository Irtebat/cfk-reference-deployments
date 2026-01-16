c# Deploy Confluent Platform + Flink using CFK and CMF

This guide provides a comprehensive walkthrough for deploying **Confluent Platform** and **Apache Flink** in an air-gapped Kubernetes environment using **Confluent for Kubernetes (CFK)** and **Confluent Manager for Flink (CMF)**. The reference implementation targets **Azure Kubernetes Service (AKS)**.

## What's Covered

| Topic | Description |
|-------|-------------|
| **Infrastructure Setup** | Provisioning AKS cluster with dedicated node pools for each component |
| **Confluent Platform** | Deploying Kafka (KRaft mode), Schema Registry, Connect, and Control Center |
| **TLS & Security** | Generating self-signed certificates and configuring encrypted communication |
| **Ingress Configuration** | Setting up NGINX ingress with SSL passthrough for external access |
| **Replicator** | Connecting to a Kerberized source Kafka cluster for cross-cluster replication |
| **Flink Deployment** | Installing Flink Kubernetes Operator and CMF for stream processing workloads |
| **Flink Applications** | Packaging and deploying Flink applications via CMF |

## Prerequisites

- Azure CLI with an active subscription
- `kubectl` configured for cluster access
- Helm 3.x installed
- Docker (for building custom images)
- Access to container registry (or internal mirror for air-gapped setup)
- Shared artefacts on a bastion node (Helm Charts)

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
  --kubernetes-version 1.32.9 \
  --nodepool-taints CriticalAddonsOnly=true:NoSchedule \
  --no-wait \
  --enable-private-cluster
```
NOTE: Add the following two flags only if you want to deploy the AKS cluster into a specific VNet and subnet.

```bash
--network-plugin azure \
--vnet-subnet-id /subscriptions/{sub-id}/resourceGroups/{vnet-rg}/providers/Microsoft.Network/virtualNetworks/{vnet-name}/subnets/{subnet-name} \
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

**Create Nginx Ingress node pool:**
```bash 
az aks nodepool add \
  --resource-group {rg-group} \
  --cluster-name {aks-cluster-name}  \
  --name ingress \
  --node-count 1 \
  --node-vm-size Standard_D8as_v5 \
  --enable-cluster-autoscaler \
  --min-count 1 \
  --max-count 3 \
  --labels app-confluent=ingress \
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

## Step 4: Generate and Deploy TLS Secrets for Servers
This deployment uses encryption b/w Confluent Servers (inside the Kubernetes cluster) and Kafka clients (outside the Kubernetes cluster). For the purpose of the POC, we will provision self-signed certificates:

cacerts.pem -- Certificate authority (CA) certificate
privkey.pem -- Private key for Confluent Servers
fullchain.pem -- Certificate for Confluent Servers
client.truststore.p12 -- Truststore for Kafka client

**Generate a private key called rootCAkey.pem for the root certificate authority.**

```bash 
openssl genrsa -out <>/certs/rootCAkey.pem 2048
Generate the CA certificate.
```

```bash 
openssl req -x509  -new -nodes \
  -key <>/certs/rootCAkey.pem \
  -days 3650 \
  -out <>/certs/cacerts.pem \
  -subj "/C=US/ST=CA/L=MVT/O=TestOrg/OU=Cloud/CN=TestCA"
```

**Generate a private key called privkey.pem for Confluent Servers.**
```bash
openssl genrsa -out <>/certs/privkey.pem 2048
```

**Create a certificate signing request (CSR) called server.csr for Confluent Servers.**

```bash
openssl req -new -key $TUTORIAL_HOME/certs/privkey.pem \
  -out <>/certs/server.csr \
  -subj "/C=US/ST=CA/L=MVT/O=TestOrg/OU=Cloud/CN=*.<DOMAIN>"

openssl req -new   -key privkey.pem   -out server.csr   -subj "/C=US/ST=CA/L=MVT/O=TestOrg/OU=Cloud/CN=*.jio.selabs.net" -addext "subjectAltName=DNS:*.jio.com,DNS:*.coeconfluentdev01.jio.com,DNS:*.cluster.local,DNS:*.svc.cluster.local, DNS:*.confluent.svc.cluster.local,DNS:*.kafka.confluent.svc.cluster.local,DNS:*.flink.svc.cluster.local,DNS:*.connect.confluent.svc.cluster.local"

  
```

**Create the fullchain.pem certificate for Confluent Servers**

```bash
openssl x509 -req \
  -in <>/certs/server.csr \
  -extensions server_ext \
  -CA <>/certs/cacerts.pem \
  -CAkey <>/certs/rootCAkey.pem \
  -CAcreateserial \
  -out <>/certs/fullchain.pem \
  -days 365 \
  -extfile \
  <(echo "[server_ext]"; echo "extendedKeyUsage=serverAuth,clientAuth"; echo "subjectAltName=DNS:*.<DOMAIN>")

  openssl x509 -req \
  -in server.csr \
  -extensions server_ext \
  -CA cacerts.pem \
  -CAkey rootCAkey.pem \
  -CAcreateserial \
  -out fullchain.pem \
  -days 365 \
  -extfile \
  <(echo "[server_ext]"; echo "extendedKeyUsage=serverAuth,clientAuth"; echo "subjectAltName=DNS:*.jio.com,DNS:*.coeconfluentdev01.jio.com,DNS:*.cluster.local,DNS:*.svc.cluster.local, DNS:*.confluent.svc.cluster.local,DNS:*.kafka.confluent.svc.cluster.local,DNS:*.flink.svc.cluster.local,DNS:*.connect.confluent.svc.cluster.local")

  
```

**Create a Kubernetes secret using the provided PEM files:**

```bash
kubectl create secret generic tls-group \
  --from-file=fullchain.pem=<>/certs/fullchain.pem \
  --from-file=cacerts.pem=./certs/cacerts.pem \
  --from-file=privkey.pem=<>/certs/privkey.pem
```
---

## Step 5: Deploy Confluent for Kubernetes Operator

```bash
kubectl create namespace confluent

kubectl config set-context --current --namespace confluent

helm repo add confluentinc https://packages.confluent.io/helm

helm upgrade --install confluent-operator confluentinc/confluent-for-kubernetes \
  --version 3.1.0 \
  --namespace confluent \
  -f ./operator_values/cfk-operator_values.yaml \
  --set enableCMFDay2Ops=true \
  --set namespaced=false
```

Check that the operator pod is running:

```bash
kubectl get pods
```

---

## Step 6: Review and Deploy Confluent Platform

Prepare the configuration:

- Replace placeholders in `confluent-platform.yaml`
- Apply required storage class (optional)
- Update manifest files as needed to configure image pulls from Jio's custom Docker registry. For guidance, see: https://docs.confluent.io/operator/current/co-custom-registry.html

```bash
kubectl apply -f storage-class.yaml
kubectl apply -f confluent-platform.yaml
```

Validate deployment:

```bash
kubectl get pods
```
---

## Step 7: Create bootstrap services

When using staticForHostBasedRouting as externalAccess type, the bootstrap endpoint is not configured to access Kafka.


**Create the Kafka bootstrap service to access Kafka:**
```bash
kubectl apply -f ./svc/kafka-bootstrap-service.yaml
```
**ther component bootstrap:**
```bash
kubectl apply -f ./svc/connect-bootstrap-service.yaml
kubectl apply -f ./svc/sr-bootstrap-service.yaml
```
---

## Step 8: Deploy Ingress Controller and Ingress

**Deploy Ingress Controller**

Add the Kubernetes Nginx Helm repo and update the repo.

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
```

**Install the Ngix controller:**

```bash
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --set controller.extraArgs.enable-ssl-passthrough="true" \
  --namespace confluent
  -f ./operator_values/nginx_values.yaml
```

Note:
If required, we need to specify specific annotations to create an internal LoadBalancer.
```bash
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --set controller.extraArgs.enable-ssl-passthrough="true" \
  --set controller.service.annotations."service\.beta\.kubernetes\.io/azure-load-balancer-internal"="true" \
  --set controller.service.loadBalancerIP="10.x.x.x"
```

**Create the Ingress resources (ssl-passthrough is enabled for all resources)**
```bash
kubectl apply -f ./ingress-resource.yaml
```
---

## Step 9 : Add DNS records
Create DNS records for Kafka brokers using the ingress controller load balancer IP.

Add DNS records for the Kafka brokers, Connect, Schema Registry, Control Center using the IP address.

```
kafka.<DOMAIN> : The EXTERNAL-IP value of the ingress load balancer service
b0.<DOMAIN> : The EXTERNAL-IP value of the ingress load balancer service
b1.<DOMAIN> : The EXTERNAL-IP value of the ingress load balancer service
b2.<DOMAIN> : The EXTERNAL-IP value of the ingress load balancer service
connect.<DOMAIN> : The EXTERNAL-IP value of the ingress load balancer service
schemaregistry.<DOMAIN> : The EXTERNAL-IP value of the ingress load balancer service
controlcenter.<DOMAIN> : The EXTERNAL-IP value of the ingress load balancer service
```
---

## Step 10: Generate Kafka Client Truststore

Create the Kafka client's truststore from the CA. This truststore allows the client to trust the broker's certificate, which is necessary for transport encryption.

```bash
keytool -import -trustcacerts -noprompt \
  -alias rootCA \
  -file <>/certs/cacerts.pem \
  -keystore <>/client/client.truststore.p12 \
  -deststorepass mystorepassword \
  -deststoretype pkcs12
```

## Step 11: Validate Using Producer and Consumer CLI

From an Azure VM:

```bash
# Install CLI tools and dependencies
curl -O https://packages.confluent.io/archive/7.9/confluent-7.9.0.tar.gz
tar xzf confluent-7.9.0.tar.gz
export CONFLUENT_HOME=~/confluent-7.9.0
export PATH=$PATH:$CONFLUENT_HOME/bin
sudo apt install default-jre

# Create topic and test
kafka-topics --producer.config ./kafka.properties --create  ...
...
kafka-console-producer --producer.config ./kafka.properties ...
kafka-console-consumer --producer.config ./kafka.properties ...
```

Access Control Center UI:
```bash
kubectl port-forward controlcenter-0 9021:9021
```

Browse: [http://localhost:9021](http://localhost:9021)

---

## Step 12: Deploy Replicator

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
## Step 13: Deploy Flink Operators

Create a namespace for flink workloads:
```
kubectl create namespace flink
```
**Set up cert-manager and install Flink kubernetes operator**
```bash
helm repo add jetstack https://charts.jetstack.io
helm repo update

helm pull jetstack/cert-manager --version 1.18.2 --untar 

helm upgrade --install cert-manager cert-manager/. --namespace cert-manager --create-namespace --version v1.18.2 -f cert-manager_values.yaml

helm upgrade --install cp-flink-kubernetes-operator confluentinc/flink-kubernetes-operator \
  --version 1.120.1 \
  -n confluent \
  -f ./operator_values/flink-operator_values.yaml
```
SKip This Step:
```
##kubectl create -f https://github.com/jetstack/cert-manager/releases/download/v1.8.2/cert-manager.yaml
```
***Customizing Flink CMF Helm Chart with Node Placement Rules***
This customization introduces targeted scheduling rules for the Confluent Manager for Flink by modifying the Helm chart.

1. Pull and extract the helm chart for confluent-manager-for-apache-flink:
```bash
helm pull confluentinc/confluent-manager-for-apache-flink --version 2.2.0
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
helm upgrade --install cmf confluentinc/confluent-manager-for-apache-flink \
  --version ~2.2.0 \
  --namespace confluent \
  -f ./operator_values/cmf-operator_values.yaml
```
Verify the operator pods are running
```bash
kubectl get pods -n confluent
```
---

## Step 13: Deploy Flink Applications

Update the Flink application configuration with Azure blob storage access keys.

```bash
kubectl apply -f ./cmf-flink-application-deployment/cmfrestclass.yaml

kubectl apply -f ./cmf-flink-application-deployment/flink-env.yaml

kubectl apply -f ./cmf-flink-application-deployment/flink-app.yaml
```

Access Flink Web UI:
```bash
kubectl port-forward svc/flink-app-rest 8081:8081 -n confluent

```
Note: service name is constructed as <flinkApplication.metadata.name>-rest

You can also utilize ./cmf-flink-application-deployment/flink-rest-svc.yaml to deploy an LB service to access Flink Web UI

```bash
curl -X GET localhost:8081/overview
```
---

## Step 14: Tear Down

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
## Misc

**Validate metrics**

Create a pod for querying the exposed metrics**
```bash
kubectl run test-shell   --rm -i -t   --image=praqma/network-multitool   --restart=Never   --namespace=flink   --command -- sh
```

Run the script "./validate-metrics.sh"
