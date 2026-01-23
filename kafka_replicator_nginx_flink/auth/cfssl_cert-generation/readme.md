# TLS Certificate Management for Confluent Platform: with CFSSL 

This document outline generation of required TLS certificates used for mTLS authentication in Confluent Platform components using **CFSSL**: A CLI-based certificate signing tool.
---
### Setup CFSSL CLI

Install the `cfssl` and `cfssljson` tools:
```bash
wget -O cfssl https://github.com/cloudflare/cfssl/releases/latest/download/cfssl-linux-amd64
wget -O cfssljson https://github.com/cloudflare/cfssl/releases/latest/download/cfssljson-linux-amd64
chmod +x cfssl cfssljson
sudo mv cfssl cfssljson /usr/local/bin/
```
---
### Step-by-Step
#### 1. Generate a Root CA

```bash
mkdir -p ./utils/generated
openssl genrsa -out ./utils/generated/rootCAkey.pem 2048

openssl req -x509 -new -nodes -key ./utils/generated/rootCAkey.pem \
  -days 3650 -out ./utils/generated/cacerts.pem \
  -subj "/C=US/ST=CA/L=MVT/O=TestOrg/OU=Cloud/CN=TestCA"
```

*Note*: If you already have a root CA from your organization, you can skip this and use your CA certificate and key with the next step.

#### 2. Create Kafka Component Certificate

*Note:* If your broker is exposed via a LoadBalancer, ensure its  IP is included in the SANs list in ./cfssl_cert-generation/kafka-server-domain.json to avoid TLS hostname verification errors.

```bash
cfssl gencert -ca=./utils/generated/cacerts.pem \
  -ca-key=./utils/generated/rootCAkey.pem \
  -config=./cfssl_cert-generation/ca-config.json \
  -profile=server ./cfssl_cert-generation/kafka-server-domain.json | cfssljson -bare ./utils/generated/kafka-server
```

#### 3. Create Kubernetes TLS Secret

```bash
kubectl create secret generic tls-kafka \
  --from-file=fullchain.pem=./utils/generated/kafka-server.pem \
  --from-file=cacerts.pem=./utils/generated/cacerts.pem \
  --from-file=privkey.pem=./utils/generated/kafka-server-key.pem \
  -n xp
```
