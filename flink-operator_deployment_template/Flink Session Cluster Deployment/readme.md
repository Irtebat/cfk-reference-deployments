Steps to run flink sql client shell on flink session mode deployment:

Step 1: Deploy Flink jobmanager and taskmanager running in session mode on k8s.

    A. Deploy flink operator:
        helm upgrade --install cp-flink-kubernetes-operator confluentinc/flink-kubernetes-operator 
        
    B. Deploy yamls:
        kubectl create -f flink-configuration-configmap.yaml
        kubectl create -f jobmanager-service.yaml
        #Create the deployments for the cluster
        kubectl create -f jobmanager-session-deployment-non-ha.yaml
        kubectl create -f taskmanager-session-deployment.yaml

Step 2. Download Flink tar
    wget https://dlcdn.apache.org/flink/flink-1.19.0/flink-1.19.0-bin-scala_2.12.tgz
    tar xf flink-1.19.0-bin-scala_2.12.tgz

Step 3. Move to Flink directory
    cd flink-1.19.0

Step 4. Download all flink kafka connectors here: 
    mkdir sql-lib
    cd sql-lib
    wget https://repo.maven.apache.org/maven2/org/apache/flink/flink-sql-connector-kafka/3.1.0-1.18/flink-sql-connector-kafka-3.1.0-1.18.jar

Step 5. Update jobmanager config conf/config.yaml
    jobmanager.rpc.address: <jobmanager-url>
    rest.address: <jobmanager-url>

Step 6. Run Flink sql-client.
    ./flink-1.19.0/bin/sql-client.sh --library ./flink-1.19.0/sql-lib

Step 7. Test query to create tables and test applications.

    CREATE TABLE flinkInput (
    `raw` STRING,
    `ts` TIMESTAMP(3) METADATA FROM 'timestamp'
    ) WITH (
    'connector' = 'kafka',
    'topic' = 'flink-input',
    'properties.bootstrap.servers' = 'kafka:9092',
    'properties.group.id' = 'testGroup',
    'scan.startup.mode' = 'earliest-offset',
    'format' = 'raw'
    );

    SELECT * FROM flinkInput;
