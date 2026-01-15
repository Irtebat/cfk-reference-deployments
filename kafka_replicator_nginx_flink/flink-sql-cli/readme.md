# Deploy CMF SQL CLI

This guide provides a comprehensive walkthrough for deploying **Confluent Platform** and **Apache Flink** in an air-gapped Kubernetes environment using **Confluent for Kubernetes (CFK)** and **Confluent Manager for Flink (CMF)**. The reference implementation targets **Azure Kubernetes Service (AKS)**.


## Step 1: Deply CMF Operator:

```bash
helm upgrade --install cmf --version "~2.2.0" \
confluentinc/confluent-manager-for-apache-flink \
--namespace confluent \
-f ./operator_values/cmf-operator_values.yaml
```
---

## Step 2: Deply Deploy CMF Rest Class

```bash
kubectl apply -f ./cmf-flink-application-deployment/cmfrestclass.yaml
```

---

## Step 3: Create Flink Environment

```bash
kubectl apply -f ./cmf-flink-application-deployment/flink-env.yaml
```

---

## Step 4:Export CMF URL

```bash
export CONFLUENT_CMF_URL=http://<host>:<port>

```

---

## Step 5: Deploy Flink compute pool.

```bash
confluent --environment flink-env flink compute-pool create compute-pool.json
```

---

## Step 6: Create catalog:

```bash
curl -v -H "Content-Type: application/json" \
-X POST http://cmf:8080/cmf/api/v1/catalogs/kafka \
-d@/catalog.json
```

---

## Step 7: Create Kafka Database connection.

```bash
curl -v -H "Content-Type: application/json" \
-X POST http://cmf:8080/cmf/api/v1/catalogs/kafka/kcat/databases \
-d@database.json
```

---

## Step 8: Run Flink SQL Shell.

```bash
confluent  flink --compute-pool flink-pool  shell --environment flink-env 
```

---

## Step 9: Run Flink query.

```bash
SHOW DATABASES;
SHOW CATALOG;
USE CATALOG kcat;
SHOW TABLES;
SELECT * FROM <table>;
```
---

## Step 10: Tear Down

Clean up all deployed components:

```bash
#Delete all active query First
confluent flink statement list --environment flink-env
confluent flink statement --environment flink-env delete <statement-name>


curl -v -H "Content-Type: application/json" \
-X DELETE http://cmf:8080/cmf/api/v1/catalogs/kafka/kcat/databases/kafka-db 

curl -v -H "Content-Type: application/json" \
-X DELETE http://cmf:8080/cmf/api/v1/catalogs/kafka/kcat 

confluent --environment flink-env flink compute-pool delete flink-pool

kubectl delete secret cmf-env-key-flink-env -n confluent  

kubectl delete -f flink-env.yaml
kubectl delete -f cmfrestclass.yaml
helm delete cmf -n confluent
helm delete cp-flink-kubernetes-operator -n confluent
```
