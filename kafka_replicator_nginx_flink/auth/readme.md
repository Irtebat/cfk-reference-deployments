# Overview
This guide provides the steps to deploy Confluent Platform using the Confluent for Kubernetes Operator with the following configuration profile:

##### Security profile

Confluent Platform cluster is setup with the following security:
- TLS1.2 network encryption with user provided certificates
- Authentication 
  - mTLS : for inter-cp-component communication
  - mtlS : kafka client to Broker communication
- Authorization : RBAC

## Create TLS certificates

With mTLS, Confluent components and clients use TLS certificates for authentication. The certificate has a CN that identifies the principal name.
Each Confluent component service should have its own TLS certificate.

Refer to ./cert-manager_cert-generation/readme.md or /Cfssl_cert-generation/readme.md to created required artefacts for enabling TLS encryption and mTLS Authentication

## Generate Token Key Pair to enable RBAC 

* You must create a PEM key pair for use by the MDS token service. 
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
-n confluent
```
## Deploy Confluent Platform

* Deploy Confluent Platform
```
kubectl apply -f ./deployment/confluent-platform.yaml
```

* Check Confluent Platform is deployed:

```
kubectl get pods -n confluent
```

## Validate

### Validate inter-pod ssl handshake
```
k exec -it kafka-0 -- bash

[appuser@kafka-0 ~]
openssl s_client -connect kafka-0.kafka.confluent.svc.cluster.local:8090 \
  -cert /mnt/sslcerts/fullchain.pem \
  -key /mnt/sslcerts/privkey.pem \
  -CAfile /mnt/sslcerts/cacerts.pem
[appuser@kafka-0 ~]
openssl s_client -connect kafka-1.kafka.confluent.svc.cluster.local:8090 \
  -cert /mnt/sslcerts/fullchain.pem \
  -key /mnt/sslcerts/privkey.pem \
  -CAfile /mnt/sslcerts/cacerts.pem
[appuser@kafka-0 ~]
openssl s_client -connect kafka-2.kafka.confluent.svc.cluster.local:8090 \
  -cert /mnt/sslcerts/fullchain.pem \
  -key /mnt/sslcerts/privkey.pem \
  -CAfile /mnt/sslcerts/cacerts.pem
```
### Setup external Client Access:

* Step 1: Generate certs for external client

  Option 1 : with CFSSL 
  
  ```
  cfssl gencert -ca=./utils/generated/cacerts.pem \
  -ca-key=./utils/generated/rootCAkey.pem \
  -config=./cfssl_cert-generation/ca-config.json \
  -profile=server ./deployment/client/client-domain.json | cfssljson -bare ./deployment/client/certs/client
  ```
Note: Confluent Platform components and client certificates should be signed by the same Certificate Authority. 

* Step 2: Create .p12 keystore

```
openssl pkcs12 -export \
  -in ./deployment/client/certs/client.pem \
  -inkey ./deployment/client/certs/client-key.pem \
  -certfile ./utils/generated/cacerts.pem \
  -out ./deployment/client/store/keystore.p12 \
  -name kafka-keystore \
  -passout pass:changeit
```
* Step 3: Create JKS Truststore 
```
keytool -delete \
  -alias kafka-ca \
  -keystore ./deployment/client/store/truststore.jks \
  -storepass changeit \
  -noprompt 2>/dev/null || true
  
keytool -importcert \
  -alias kafka-ca \
  -file ./utils/generated/cacerts.pem \
  -keystore ./deployment/client/store/truststore.jks \
  -storepass changeit \
  -noprompt
```
* Create ConfluentRoleBinding for your client. 
```
k apply -f deployment/client/ClusterAdmin-rb.yaml
k apply -f deployment/client/ResourceOwner-rb.yaml
k apply -f deployment/client/DeveloperRead-cg-rb.yaml
```

### Validate SSL handshake

```
openssl s_client \
  -connect <LB_IP>:<Advertised_Port> \
  -cert ./deployment/client/certs/client.pem \
  -key ./deployment/client/certs/client-key.pem \
  -CAfile ./utils/generated/cacerts.pem \
  -servername <LB_IP>
```

### Validate Commands:

Note: Replace kafka.DOMAIN:443 below with LB IP and Advertised kafka Port

* List topics
```
kafka-topics   \
  --bootstrap-server kafka.DOMAIN:443 \
  --command-config ./deployment/client/client.properties \
  --list
```
* Create Topic
```
kafka-topics \
  --bootstrap-server kafka.DOMAIN:443 \
  --command-config ./deployment/client/client.properties \
  --create \
  --topic test-topic \
  --partitions 3 \
  --replication-factor 3
```
* Produce Messages
```
kafka-console-producer \
  --bootstrap-server kafka.DOMAIN:443 \
  --producer.config ./deployment/client/client.properties \
  --topic test-topic
```
* Consume Messages
```
kafka-console-consumer \
  --bootstrap-server kafka.DOMAIN:443 \
  --consumer.config ./deployment/client/client.properties \
  --topic test-topic \
  --from-beginning
```

## Tear down

```
kubectl delete confluentrolebinding --all -n confluent
kubectl delete -f ./deployment/confluent-platform-kraft.yaml -n confluent
kubectl delete secret mds-token root-ca-secret tls-kafka tls-kafka-client --namespace confluent
helm uninstall operator -n confluent
```
