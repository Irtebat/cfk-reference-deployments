package ril.rra.flink.source.roms;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.StatementSet;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import ril.rra.flink.utils.KafkaUtils;

public class OpenOrdersJiomart {
    public static void main(String[] args) throws Exception {
        // Parse command-line arguments
        String cleanStartupFrom = args.length > 0 ? args[0] : "group-offsets";
        String retainState = args.length > 1 ? args[1] : "retain";

        // Set up environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        EnvironmentSettings settings = EnvironmentSettings.newInstance().inStreamingMode().build();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, settings);
        env.setParallelism(3);
        // Calculate midnight timestamp for today
        LocalDate today = LocalDate.now();
        long midnightMillis = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        // Configure checkpointing
        CheckpointConfig config = env.getCheckpointConfig();
        config.setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        config.setCheckpointInterval(3000000); // 500 seconds
        config.setExternalizedCheckpointCleanup(
            retainState.equalsIgnoreCase("retain") 
                ? CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION 
                : CheckpointConfig.ExternalizedCheckpointCleanup.DELETE_ON_CANCELLATION
        );

        // Set up Flink SQL configurations
        Configuration tableConfig = tableEnv.getConfig().getConfiguration();
        tableConfig.setString("table.exec.mini-batch.enabled", "true");
        tableConfig.setString("table.exec.mini-batch.allow-latency", "5s");
        tableConfig.setString("table.exec.mini-batch.size", "5000");

        // Define Kafka configurations for each table
        String centralKafkaConsume = KafkaUtils.buildKafkaConfig(
            "flink-confluent", 
            "ngprdprp034389.bss.xyz.com:9093,ngprdprp034390.bss.xyz.com:9093,ngprdprp034391.bss.xyz.com:9093,ngprdprp034392.bss.xyz.com:9093,ngprdprp034393.bss.xyz.com:9093,ngprdprp034394.bss.xyz.com:9093,ngprdprp034395.bss.xyz.com:9093,ngprdprp034396.bss.xyz.com:9093,ngprdprp034397.bss.xyz.com:9093,ngprdprp034398.bss.xyz.com:9093", 
            "/newjks/SI_rra.keytab", 
            "SI_rra@JIOMARTKAFKAPROD.COM", 
            "/newjks/server-keystore.jks", 
            "changeit", 
            "/newjks/truststore_1.jks", 
            "changeit"
        );


        String oggKafkaProduce = KafkaUtils.buildKafkaConfig(
            "confluent_poc_fp", 
            "sidcprdrrakfk01.ril.com:6667,sidcprdrrakfk02.ril.com:6667,sidcprdrrakfk03.ril.com:6667,sidcprdrrakfk04.ril.com:6667,sidcprdrrakfk05.ril.com:6667,sidcprdrrakfk06.ril.com:6667,sidcprdrrakfk07.ril.com:6667", 
            null, 
            null, 
            "/newjks/truststore_ogg.jks", 
            "7ecETGlHjzs", 
            "/newjks/truststore_ogg.jks", 
            "7ecETGlHjzs"
        );

            String oggKafkaUpsert = KafkaUtils.buildKafkaConfig(
            "", 
            "sidcprdrrakfk01.ril.com:6667,sidcprdrrakfk02.ril.com:6667,sidcprdrrakfk03.ril.com:6667,sidcprdrrakfk04.ril.com:6667,sidcprdrrakfk05.ril.com:6667,sidcprdrrakfk06.ril.com:6667,sidcprdrrakfk07.ril.com:6667", 
            null, 
            null, 
            "/newjks/truststore_ogg.jks", 
            "7ecETGlHjzs", 
            "/newjks/truststore_ogg.jks", 
            "7ecETGlHjzs"
        );

        // Create Kafka tables with column-specific DDLs
        String ddl_expstore_order = "CREATE TABLE EXPSTORE_ORDER (\n" +
            "    ORDERID STRING,\n" +
            "    TOT_ORDER_AMT DOUBLE,\n" +
            "    ORDERDATE TIMESTAMP(3),\n" +
            "    op_type STRING,\n" +
            "    op_ts TIMESTAMP(3),\n" +
            "    WATERMARK FOR op_ts AS op_ts - INTERVAL '10' MINUTES\n" +
            ") WITH (\n" +
            "    'connector' = 'kafka',\n" +
            "    'topic' = 'OGG_JMART_EXPSTORE_ORDER',\n" +
            KafkaUtils.getStartupConfig(cleanStartupFrom, midnightMillis) + "\n" +
            centralKafkaConsume + "\n" +
            ");";
        tableEnv.executeSql(ddl_expstore_order);

        String ddl_expstore_order_item = "CREATE TABLE EXPSTORE_ORDER_ITEM (\n" +
            "    STORENO STRING,\n" +
            "    ARTICLEID STRING,\n" +
            "    ORDERID STRING,\n" +
            "    SHIPMENT_ID STRING,\n" +
            "    QTY DOUBLE,\n" +
            "    RECORD_DATE TIMESTAMP(3),\n" +
            "    op_type STRING,\n" +
            "    op_ts TIMESTAMP(3),\n" +
            "    WATERMARK FOR op_ts AS op_ts - INTERVAL '10' MINUTES\n" +
            ") WITH (\n" +
            "    'connector' = 'kafka',\n" +
            "    'topic' = 'OGG_JMART_EXPSTORE_ORDERITEM',\n" +
            KafkaUtils.getStartupConfig(cleanStartupFrom, midnightMillis) + "\n" +
            centralKafkaConsume + "\n" +
            ");";
        tableEnv.executeSql(ddl_expstore_order_item);

        String ddl_expstore_order_smart_status = "CREATE TABLE EXPSTORE_ORDER_SMART_STATUS (\n" +
            "    ORDERID STRING,\n" +
            "    SHIPMENT_ID STRING,\n" +
            "    LAND_STATUS STRING,\n" +
            "    op_type STRING,\n" +
            "    op_ts TIMESTAMP(3),\n" +
            "    WATERMARK FOR op_ts AS op_ts - INTERVAL '10' MINUTES\n" +
            ") WITH (\n" +
            "    'connector' = 'kafka',\n" +
            "    'topic' = 'OGG_JMART_EXPSTORE_ORDER_SMART_STATUS',\n" +
            KafkaUtils.getStartupConfig(cleanStartupFrom, midnightMillis) + "\n" +
            centralKafkaConsume + "\n" +
            ");";
        tableEnv.executeSql(ddl_expstore_order_smart_status);

        // Create upsert Kafka tables
        String ddl_order_state = "CREATE TABLE ORDER_STATE (\n" +
            "    STORENO STRING,\n" +
            "    ARTICLEID STRING,\n" +
            "    ORDERID STRING,\n" +
            "    SHIPMENT_ID STRING,\n" +
            "    QTY DOUBLE,\n" +
            "    ORDERDATE STRING,\n" +
            "    PRIMARY KEY (STORENO, ARTICLEID, ORDERID, SHIPMENT_ID) NOT ENFORCED\n" +
            ") WITH (\n" +
            "    'connector' = 'upsert-kafka',\n" +
            "    'topic' = 'CONFLUENT_JIOMART_ORDER_STATUS',\n" +
               oggKafkaUpsert+
            ");";
        tableEnv.executeSql(ddl_order_state);

        String ddl_expstore_order_aggregated = "CREATE TABLE EXPSTORE_ORDER_AGGREGATED (\n" +
            "    STORENO STRING,\n" +
            "    ARTICLEID STRING,\n" +
            "    TOTAL_QTY DOUBLE,\n" +
            "    ORDERDATE STRING,\n" +
            "    OPEN_ORDER_QTY DOUBLE,\n" +
            "    op_ts STRING,\n" +
            "    PRIMARY KEY (STORENO, ARTICLEID, ORDERDATE) NOT ENFORCED\n" +
            ") WITH (\n" +
            "    'connector' = 'upsert-kafka',\n" +
            "    'topic' = 'CONFLUENT_JIOMART_OPEN_ORDERS',\n" +
            oggKafkaUpsert+
            ");";
        tableEnv.executeSql(ddl_expstore_order_aggregated);

        // Create staging table for EXPSTORE_ORDER_SMART_STATUS
        String ddl_expstore_order_smart_status_staging = "CREATE TABLE EXPSTORE_ORDER_SMART_STATUS_STAGING (\n" +
            "    ORDERID STRING,\n" +
            "    SHIPMENT_ID STRING,\n" +
            "    LAND_STATUS STRING,\n" +
            "    op_type STRING,\n" +
            "    op_ts TIMESTAMP(3),\n" +
            "    PRIMARY KEY (ORDERID, SHIPMENT_ID) NOT ENFORCED\n" +
            ") WITH (\n" +
            "    'connector' = 'upsert-kafka',\n" +
            "    'topic' = 'CONFLUENT_3',\n" +
              oggKafkaUpsert+
            ");";
        tableEnv.executeSql(ddl_expstore_order_smart_status_staging);

        // Create NODE_MASTER_DEDUPED table
        String ddl_node_master_deduped = "CREATE TABLE NODE_MASTER_DEDUPED (\n" +
            "    STORE_ID STRING,\n" +
            "    FORMAT_CODE STRING,\n" +
            "    NODE_TYPE STRING,\n" +
            "    QUEUE_NAME STRING,\n" +
            "    FEED_FLAG STRING,\n" +
            "    LAST_MODIFIED_DATE STRING\n" +
            ") WITH (\n" +
            "    'connector' = 'kafka',\n" +
            " 'properties.group.id'='confluent_poc_fp_roms',\n"+
            "    'topic' = 'CONFLUENT_S_FNL_NODE_MASTER_DEDUPE',\n" +
            "    'scan.startup.mode' = 'earliest-offset',\n" +
            oggKafkaProduce + "\n" +
            ");";
        tableEnv.executeSql(ddl_node_master_deduped);

        // Build and execute SQL queries
        StatementSet statementSet = tableEnv.createStatementSet();

        // Insert into staging table for latest status
        String sqlopen61 = "INSERT INTO EXPSTORE_ORDER_SMART_STATUS_STAGING\n" +
            "SELECT ORDERID, SHIPMENT_ID, LAND_STATUS, op_type, op_ts\n" +
            "FROM EXPSTORE_ORDER_SMART_STATUS";
        statementSet.addInsertSql(sqlopen61);
            

        

        // Aggregated open orders
        String sqlopen62 = "INSERT INTO EXPSTORE_ORDER_AGGREGATED\n" +
            "SELECT /*+ STATE_TTL('os'='1d','sm'='1d') */\n" +
            "    os.STORENO,\n" +
            "    os.ARTICLEID,\n" +
            "    SUM(os.QTY) AS TOTAL_QTY,\n" +
            "    os.ORDERDATE,\n" +
            "    SUM(CASE WHEN sm.LAND_STATUS = 'OPEN' THEN os.QTY ELSE 0 END) AS OPEN_ORDER_QTY,\n" +
       "    MAX(os.ORDERDATE) AS op_ts\n" +
            "FROM ORDER_STATE os\n" +
            "JOIN EXPSTORE_ORDER_SMART_STATUS_STAGING sm\n" +
            "  ON os.ORDERID = sm.ORDERID AND os.SHIPMENT_ID = sm.SHIPMENT_ID\n" +
            "GROUP BY os.STORENO, os.ARTICLEID, os.ORDERDATE";
        statementSet.addInsertSql(sqlopen62);



            // Insert into ORDER_STATE (broadcast join example)
        String sqlopen63 = "INSERT INTO ORDER_STATE\n" +
        "SELECT\n" +
        " /*+ BROADCAST(n) */ "+
        "    s.STORENO,\n" +
        "    s.ARTICLEID,\n" +
        "    s.ORDERID,\n" +
        "    s.SHIPMENT_ID,\n" +
        "    s.QTY,\n" +
        "    DATE_FORMAT(o.ORDERDATE , 'yyyy-MM-dd') AS ORDERDATE\n" +
        "FROM EXPSTORE_ORDER o\n" +
        "INNER JOIN EXPSTORE_ORDER_ITEM s\n" +
        "  ON o.ORDERID = s.ORDERID \n" +
        "INNER JOIN  NODE_MASTER_DEDUPED n\n" +
        "  ON s.STORENO = n.STORE_ID\n" +
        "WHERE (s.QTY) > 0 AND (o.TOT_ORDER_AMT) > 0\n" +
        "  AND (o.ORDERDATE) >= TIMESTAMPADD(DAY, -1, CAST(CURRENT_TIMESTAMP AS TIMESTAMP(3)))\n" +
        "  AND o.op_type = 'I'\n" +
        "  AND s.op_type = 'I'";
        statementSet.addInsertSql(sqlopen63);

        // Execute the job
        TableResult result = statementSet.execute();
        result.getJobClient().ifPresentOrElse(
            jobClient -> System.out.println("Job submitted with JobID: " + jobClient.getJobID()),
            () -> System.err.println("Job submission failed.")
        );
    }}

   
    
