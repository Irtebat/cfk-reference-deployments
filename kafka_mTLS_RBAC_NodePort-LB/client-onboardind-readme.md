# Overview
This guide helps onboard external Kafka clients to a secured Confluent Platform cluster deployed via Confluent for Kubernetes. It walks through:
1. 	Generate certs with cert-manager
2. 	Create keystore & truststore
3. 	Apply appropriate RBAC role bindings
4.	Configure client properties and validate SSL and Kafka commands

*Assumption* The Confluent Platform is already deployed with:
- Authentication: TLS + mTLS enabled
- Authorization: RBAC
- Client Network AccessL Access exposed via NodePort behind a LoadBalancer

### Generate Client TLS Certificate (via cert-manager)
Use as reference , the client certificate manifest:
```
kubectl apply -f ./cert-manager_cert-generation/04-kafka-client-cert.yaml
```

##### Export the generated certificate and key:

```
kubectl get secret tls-kafka-client -n xp -o jsonpath='{.data.tls\.crt}' | base64 -d > ./deployment/client/certs/client.pem
kubectl get secret tls-kafka-client -n xp -o jsonpath='{.data.tls\.key}' | base64 -d > ./deployment/client/certs/client-key.pem
```

##### Export the cluster CA used to sign the client:

```
kubectl get secret tls-kafka-client -n xp -o jsonpath='{.data.ca\.crt}' | base64 -d > ./utils/generated/cacerts.pem
```
Note: All Confluent components and client certificates must be signed by the same CA.

### Create Keystore & Truststore
Keystore (.p12)
```
openssl pkcs12 -export \
  -in ./deployment/client/certs/client.pem \
  -inkey ./deployment/client/certs/client-key.pem \
  -certfile ./utils/generated/cacerts.pem \
  -out ./deployment/client/store/keystore.p12 \
  -name kafka-keystore \
  -passout pass:changeit
```
Truststore (JKS)
```
keytool -importcert \
  -alias kafka-ca \
  -file ./utils/generated/cacerts.pem \
  -keystore ./deployment/client/store/truststore.jks \
  -storepass changeit \
  -noprompt
```

### Set Up RBAC for the Client
Apply one or more role bindings depending on intended access.

1. For Cluster Admin 
```
kubectl apply -f ./deployment/client/ClusterAdmin-rb.yaml
```
2. For Resource Owner (access to specific topic/group)
```
kubectl apply -f ./deployment/client/ResourceOwner-rb.yaml
```
3. To Create or list topics:
```
kubectl apply -f ./topic-manager/DeveloperManager-topic.yaml
```
4. To Write to a topic:
```
kubectl apply -f ./write-to-topic/DeveloperWrite-topic.yaml
```
5. To Read from a topic:
```
kubectl apply -f ./write-from-topic/DeveloperRead-topic.yaml
kubectl apply -f ./write-from-topic/DeveloperRead-consumer_group.yaml
```

#### Connectivity Validation
Ensure your Kafka client properties (e.g., client.properties) are configured with the generated certificates:
```
security.protocol=SSL
ssl.truststore.location=./deployment/client/store/truststore.jks
ssl.truststore.password=changeit
ssl.keystore.type=PKCS12
ssl.keystore.location=./deployment/client/store/keystore.p12
ssl.keystore.password=changeit
ssl.key.password=changeit
```
Validate SSL Handshake
```
openssl s_client \
  -connect <LB_IP>:<Advertised_Port> \
  -cert ./deployment/client/certs/client.pem \
  -key ./deployment/client/certs/client-key.pem \
  -CAfile ./utils/generated/cacerts.pem
```
Kafka Commands
Replace <LB_IP>:<PORT> with the Kafka advertised listener.

List Topics
```
kafka-topics \
  --bootstrap-server <LB_IP>:<PORT> \
  --command-config ./deployment/client/client.properties \
  --list
```
Create Topic
```
kafka-topics \
  --bootstrap-server <LB_IP>:<PORT> \
  --command-config ./deployment/client/client.properties \
  --create --topic my-topic --partitions 3 --replication-factor 3
```
Produce Messages
```
kafka-console-producer \
  --bootstrap-server <LB_IP>:<PORT> \
  --producer.config ./deployment/client/client.properties \
  --topic my-topic
```
Consume Messages
```
kafka-console-consumer \
  --bootstrap-server <LB_IP>:<PORT> \
  --consumer.config ./deployment/client/client.properties \
  --topic my-topic --from-beginning
``
