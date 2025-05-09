
# Setup Kafka Access via Azure Load Balancer (L4) in AKS

## Architecture Overview

```
Client (VM or service in VNet)
    ↕
Azure Load Balancer (ILB) with IP accessible to client (may be public or private)
    ↕
AKS Nodes via Advertised NodePort
    ↕
Confluent Kafka Pods
```

---

## Prerequisites for reference

This reference assumes deployment in AKS and usage of Azure Layer 4 (TCP) Load Balancer.

- AKS Cluster deployed
  - AKS node pools are deployed using Virtual Machine Scale Sets
- Confluent Kafka deployed on AKS with NodePort services exposed per broker

*Note:* In this reference, we will set up a Load Balancer with a public IP to enable access to clients outside the VM/VNet. 

---

## Step-by-Step Setup

### 1. Create a Public Load Balancer

```bash
az network lb create \
  --resource-group <resource-group-name> \
  --name kafka-public-lb \
  --sku Standard \
  --location <region> \
  --frontend-ip-name kafkaFrontend \
  --backend-pool-name kafkaBackend \
  --public-ip-address kafkaPublicIP
```

### 2. Create Health Probes

```bash
az network lb probe create \
  --resource-group <resource-group-name> \
  --lb-name kafka-public-lb \
  --name kafka-probe-32524 \
  --protocol Tcp \
  --port 32524
```

Repeat for other Confluent Kafka advertised ports (e.g., 32525, 32526, 32527 as needed).

*Note:* These advertised ports are configurable in Confluent Kafka manifest files.

### 3. Create Load Balancing Rules

```bash
az network lb rule create \
  --resource-group <resource-group-name> \
  --lb-name kafka-public-lb \
  --name kafka-rule-32524 \
  --protocol Tcp \
  --frontend-port 32524 \
  --backend-port 32524 \
  --frontend-ip-name kafkaFrontend \
  --backend-pool-name kafkaBackend \
  --probe-name kafka-probe-32524
```

Repeat for Confluent Kafka advertised port (e.g., 32524, 32526, 32527 as needed).

---

## 4. Add AKS Node Pool VMs to Backend Pool

Assuming: AKS node pools are deployed using Virtual Machine Scale Sets
You can associate the VMSS directly with an Azure Load Balancer's backend address pool as follows:
```bash
az vmss update \
  --resource-group <resource-group> \
  --name <vmss-name> \
  --add virtualMachineProfile.networkProfile.networkInterfaceConfigurations[0].ipConfigurations[0].loadBalancerBackendAddressPools \
    "[{'id':'/subscriptions/<subscription-id>/resourceGroups/<resource-group>/providers/Microsoft.Network/loadBalancers/<lb-name>/backendAddressPools/<backend-pool-name>'}]"

```
*Note:* The Load Balancer must be in the same Virtual Network as the VMSS for backend pool association to work.

---

## 6. NSG Configuration 

Allow inbound traffic on ports 32524–32527 to AKS:

| Priority | Name               | Port Range     | Protocol | Source | Action |
|----------|--------------------|----------------|----------|--------|--------|
| 100      | AllowConfluentKafkaPorts| 32524-32527    | TCP      | Any    | Allow  |

---
## Validate connectivity from client:
```
openssl s_client 
  -connect <lb-ip>>:<advertised-port> \
  -cert ./utils/generated/client.pem \
  -key ./utils/generated/client-key.pem \
  -CAfile ./utils/generated/cacerts.pem \
  -servername <lb-ip>>
```
---

## Note: Internal LB Variant

To use a private IP instead:

- Set `--frontend-ip-configs "Private"` and specify `--vnet-name` and `--subnet` during `az network lb create`.
- Keep the rest of the setup same, and ensure the client is in the same or peered VNet.
