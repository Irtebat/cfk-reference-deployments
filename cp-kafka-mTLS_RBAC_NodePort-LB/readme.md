# Security setup overview

In this workflow scenario, you'll set up a Confluent Platform cluster with the following security:
- Full TLS network encryption with user provided certificates
- mTLS authentication on MDS Server for RBAC, along with mTLS on all cp components

## Network setup Overview

```
Client (VM or service in VNet)
    ↕
Azure Load Balancer (ILB) with IP accessible to client (may be public or private)
    ↕
Kafka Pods ( Nodes config'ed via NodePort )
```

## Prerequisites
The scope of this readme assumes exisistence of an L4 Load Balancer for enabling external access. 
The IP of this Loadbalancer will be utilized to advertise kafka address to external clients
For an example reference, read lb-setup-readme.md

## Create confluent Namespace
```
kubectl create namespace xp
```

## Deploy Confluent for Kubernetes

* Set up the Helm Chart:
```
helm repo add confluentinc https://packages.confluent.io/helm
helm repo update
```

* Install Confluent For Kubernetes using Helm:
```
helm upgrade --install operator confluentinc/confluent-for-kubernetes -n xp 
```

* Check that the Confluent for Kubernetes pod comes up and is running:
```
kubectl get pods -n xp
```

## Create TLS certificates

With mTLS, Confluent components and clients use TLS certificates for authentication. The certificate has a CN that identifies the principal name.
Each Confluent component service should have its own TLS certificate.

Refer to ./cert-manager_cert-generation/readme.md or /Cfssl_cert-generation/readme.md to created required artefacts for enabling TLS encryption and mTLS Authentication

## Generate Token Key Pair to enable RBAC 

* You must create a PEM key pair for use by the MDS token service. This key pair is added to your server.properties file in the next section.
For ex:
```
openssl genrsa -out ./utils/mds-tokenkeypair.txt 2048
```

Extract the public key.
```
openssl rsa -in ./utils/mds-tokenkeypair.txt -outform PEM -pubout -out ./utils/mds-publickey.txt
```

* Create a Kubernetes secret object for MDS:
```
kubectl create secret generic mds-token \
--from-file=mdsPublicKey.pem=./utils/mds-publickey.txt \
--from-file=mdsTokenKeyPair.pem=./utils/mds-tokenkeypair.txt \
-n xp
```
## Deploy Confluent Platform

* Deploy Confluent Platform
```
kubectl apply -f ./xp/confluent-platform.yaml
```

* Check Confluent Platform is deployed:

```
kubectl get pods -n xp
```

## Validate

### Validate inter-pod ssl handshake
```
k exec -it kafka-0 -- bash

[appuser@kafka-0 ~]
openssl s_client -connect kafka-0.kafka.xp.svc.cluster.local:8090 \
  -cert /mnt/sslcerts/fullchain.pem \
  -key /mnt/sslcerts/privkey.pem \
  -CAfile /mnt/sslcerts/cacerts.pem
[appuser@kafka-0 ~]
openssl s_client -connect kafka-1.kafka.xp.svc.cluster.local:8090 \
  -cert /mnt/sslcerts/fullchain.pem \
  -key /mnt/sslcerts/privkey.pem \
  -CAfile /mnt/sslcerts/cacerts.pem
[appuser@kafka-0 ~]
openssl s_client -connect kafka-2.kafka.xp.svc.cluster.local:8090 \
  -cert /mnt/sslcerts/fullchain.pem \
  -key /mnt/sslcerts/privkey.pem \
  -CAfile /mnt/sslcerts/cacerts.pem
```
### Setup external Client Access:

* Step 1: Generate certs for external client

  * Optione 1 : with CFSSL 
cfssl gencert -ca=./utils/generated/cacerts.pem \
-ca-key=./utils/generated/rootCAkey.pem \
-config=./cfssl_cert-generation/ca-config.json \
-profile=server ./xp/client/client-domain.json | cfssljson -bare ./xp/client/certs/client

* Option 2: with cert-manager

```
k apply -f ./cert-manager_cert-generation/04-kafka-client-cert
kubectl get secret tls-kafka-client -n xp -o jsonpath='{.data.tls\.crt}' | base64 -d > ./xp/client/certs/client.pem
kubectl get secret tls-kafka-client -n xp -o jsonpath='{.data.tls\.key}' | base64 -d > ./xp/client/certs/client-key.pem
kubectl get secret tls-kafka-client -n xp -o jsonpath='{.data.ca\.crt}' | base64 -d > ./utils/generated/cacerts.pem
```

* Step 2: Create .p12 keystore
```
openssl pkcs12 -export \
  -in ./xp/client/certs/client.pem \
  -inkey ./xp/client/certs/client-key.pem \
  -certfile ./utils/generated/cacerts.pem \
  -out ./xp/client/store/keystore.p12 \
  -name kafka-keystore \
  -passout pass:changeit
```
* Step 3: Create JKS Truststore 
```
keytool -import \
  -alias kafka-ca \
  -file ./utils/generated/cacerts.pem \
  -keystore ./xp/client/store/truststore.jks \
  -storepass changeit \
  -noprompt
```
* Create ConfluentRoleBinding for your client. 
```
k apply -f xp/client/ClusterAdmin-rb.yaml
k apply -f xp/client/ResourceOwner-rb.yaml
k apply -f xp/client/DeveloperRead-cg-rb.yaml
```

### Validate SSL handshake
openssl s_client \
  -connect <LB IP>:32524 \
  -cert ./xp/client/certs/client.pem \
  -key ./xp/client/certs/client-key.pem \
  -CAfile ./utils/generated/cacerts.pem \
  -servername <LB IP>

### Validate Commands:

* List topics
```
kafka-topics   \
  --bootstrap-server 98.70.146.223:32524 \
  --command-config ./xp/client/client.properties \
  --list
```
* Create Topic
```
kafka-topics \
  --bootstrap-server 98.70.146.223:32524 \
  --command-config ./xp/client/client.properties \
  --create \
  --topic test-topic \
  --partitions 3 \
  --replication-factor 3
```
* Produce Messages
```
kafka-console-producer \
  --bootstrap-server 98.70.146.223:32524 \
  --producer.config ./xp/client/client.properties \
  --topic test-topic
```
* Consume Messages
```
kafka-console-consumer \
  --bootstrap-server 98.70.146.223:32524 \
  --consumer.config ./xp/client/client.properties \
  --topic test-topic \
  --from-beginning
```

## Tear down

```
kubectl delete confluentrolebinding --all -n xp
kubectl delete -f ./xp/confluent-platform.yaml -n xp
kubectl delete secret tls-kafka --namespace xp
kubectl delete secret mds-token -n xp
helm delete operator -n xp
```


