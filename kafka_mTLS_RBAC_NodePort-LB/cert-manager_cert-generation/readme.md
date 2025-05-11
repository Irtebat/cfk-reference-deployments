# TLS Certificate Management for Confluent Platform: with cert-manager

This document outline generation of required TLS certificates used for mTLS authentication in Confluent Platform components us
---
### Setup cert-manager

Install cert-manager via Helm:
```bash
helm repo add jetstack https://charts.jetstack.io
helm repo update

helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  --set installCRDs=true
```

Verify installation:
```bash
kubectl get pods -n cert-manager
```
---
### Step-by-Step

#### 1. Create a Self-Signed Root CA

```yaml
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: root-ca
  namespace: xp
spec:
  isCA: true
  commonName: TestCA
  secretName: root-ca-secret
  duration: 43800h
  privateKey:
    algorithm: RSA
    size: 2048
  issuerRef:
    name: selfsigning-issuer
    kind: Issuer
---
apiVersion: cert-manager.io/v1
kind: Issuer
metadata:
  name: selfsigning-issuer
  namespace: xp
spec:
  selfSigned: {}
```

#### 2. Create a CA-Based Issuer

```yaml
apiVersion: cert-manager.io/v1
kind: Issuer
metadata:
  name: kafka-ca-issuer
  namespace: xp
spec:
  ca:
    secretName: root-ca-secret
```
This issuer will be used to sign component certificates (Kafka, Control Center, etc.)
Note: If you already have a CA in place, point to its secret in secretName in the below Certificate cert-manager.io/v1 CRD

#### 3. Create Kafka Certificate

```yaml
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: kafka-server-cert
  namespace: xp
spec:
  secretName: tls-kafka
  duration: 43800h
  renewBefore: 720h
  commonName: kafka
  dnsNames:
    - <LB IP> 
    - kafka
    - kafka.xp.svc.cluster.local
    - kafka-0.kafka.xp.svc.cluster.local
    - kafka-1.kafka.xp.svc.cluster.local
    - kafka-2.kafka.xp.svc.cluster.local
    - kraftcontroller.xp.svc.cluster.local
    - kraftcontroller-0.kraftcontroller.xp.svc.cluster.local
    - kraftcontroller-1.kraftcontroller.xp.svc.cluster.local
    - kraftcontroller-2.kraftcontroller.xp.svc.cluster.local
  privateKey:
    algorithm: RSA
    size: 2048
  usages:
    - signing
    - key encipherment
    - server auth
    - client auth
  issuerRef:
    name: kafka-ca-issuer
    kind: Issuer
```
*Note:* If your broker is exposed via a LoadBalancer, ensure its  IP is included in the SANs list in kafka-server-domain.json to avoid TLS hostname verification errors.

This will result in a Kubernetes TLS secret (tls-kafka) containing:
- tls.crt: server certificate
- tls.key: private key
- ca.crt: CA chain
*Note:* CFK supports cert-manager's above convention
---
