package ril.rra.flink.source.sap.mard;

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

public class StockValue {
    public static void main(String[] args) throws Exception {
        // Parse command-line arguments
        String cleanStartupFrom = args.length > 0 ? args[0] : "group-offsets";
        String retainState = args.length > 1 ? args[1] : "retain";

        // Set up environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        EnvironmentSettings settings = EnvironmentSettings.newInstance().inStreamingMode().build();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, settings);
env.setParallelism(6); // Set parallelism to 1 for simplicity
        // Calculate midnight timestamp for today
        LocalDate today = LocalDate.now();
        long midnightMillis = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        TM - 2
                3 TM re
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
            "ngprdprp034389.bss.abc.com:9093,ngprdprp034390.bss.abc.com:9093,ngprdprp034391.bss.abc.com:9093,ngprdprp034392.bss.abc.com:9093,ngprdprp034393.bss.abc.com:9093,ngprdprp034394.bss.abc.com:9093,ngprdprp034395.bss.abc.com:9093,ngprdprp034396.bss.abc.com:9093,ngprdprp034397.bss.abc.com:9093,ngprdprp034398.bss.abc.com:9093", 
            "/newjks/SI_rra.keytab", 
            "SI_rra@abcMARTKAFKAPROD.COM", 
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


        String ddl_mard = "CREATE TABLE MARD (\n" +
                "    MATNR STRING,\n" +
                "    WERKS STRING,\n" +
                "    LGORT STRING,\n" +
                "    LABST INT,\n" +
                "   op_type STRING, " +
                "    op_ts TIMESTAMP(3),\n" +
                "    WATERMARK FOR op_ts AS op_ts - INTERVAL '5' SECOND " +
                ") WITH (\n" +
                "    'connector' = 'kafka',\n" +
                "    'topic' = 'OGG_P51_MARD',\n" +
                "    'scan.startup.mode' = 'earliest-offset',\n" +
                centralKafkaConsume +
                ");";

        // Register the Kafka source table
        tableEnv.executeSql(ddl_mard);
        String ddl_mard_full = "CREATE TABLE MARD_FULL (\n" +
                "    MATNR STRING,\n" +
                "    WERKS STRING,\n" +
                "    LGORT STRING,\n" +
                "    LABST INT,\n" +
                "    op_ts TIMESTAMP(3),\n" +
                "    WATERMARK FOR op_ts AS op_ts - INTERVAL '1' HOUR " +
                ") WITH (\n" +
                "    'connector' = 'kafka',\n" +
                "    'topic' = 'CONFLUENT_P19_MARD_FULL_SYNC',\n" +
                "    'scan.startup.mode' = 'earliest-offset',\n" +

                oggKafkaProduce +
                ");";
        tableEnv.executeSql(ddl_mard_full);

        String ddl_mard_final = "CREATE TABLE MARD_FINAL (\n" +
                "  MATNR STRING,\n" +
                "  WERKS STRING,\n" +
                "    LABST INT,\n" +
                "  op_ts TIMESTAMP(3),\n" +
                "    PRIMARY KEY (MATNR, WERKS) NOT ENFORCED \n" +
                ") WITH (\n" +
                "    'connector' = 'upsert-kafka',\n" +
                "    'topic' = 'CONFLUENT_MARD_FINAL',\n" +
                oggKafkaUpsert +
                ");";
        tableEnv.executeSql(ddl_mard_final);




        String sqlmard_2_1_mard = "INSERT INTO MARD_FINAL\n" +
                "SELECT MATNR, WERKS, LABST, op_ts \n" +
                "FROM (\n" +
                "  SELECT *, ROW_NUMBER() OVER (PARTITION BY MATNR, WERKS ORDER BY op_ts DESC) AS row_num\n" +
                "  FROM (\n" +
                "    SELECT /*+ BROADCAST(N) */ M.MATNR, M.WERKS, M.LABST, CAST(M.op_ts AS TIMESTAMP(3)) AS op_ts\n" +
                "    FROM MARD M\n" +
                "    INNER JOIN NODE_MASTER_DEDUPED N\n" +
                "    ON M.WERKS = N.STORE_ID\n" +
                "    WHERE M.LGORT = '1000'\n" +
                "    UNION ALL\n" +
                "    SELECT MATNR, WERKS, LABST, op_ts FROM MARD_FULL\n" +
                "  )\n" +
                ")\n" +
                "WHERE row_num = 1";


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
            "    'topic' = 'CONFLUENT_S_FNL_NODE_MASTER_DEDUPE',\n" +
            "    'scan.startup.mode' = 'earliest-offset',\n" +
            oggKafkaProduce + "\n" +
            ");";
        tableEnv.executeSql(ddl_node_master_deduped);

        // Build and execute SQL queries
           
        StatementSet statementSet = tableEnv.createStatementSet();
        statementSet.addInsertSql(sqlmard_2_1_mard);


        // Execute the job
        TableResult result = statementSet.execute();
        result.getJobClient().ifPresentOrElse(
            jobClient -> System.out.println("Job submitted with JobID: " + jobClient.getJobID()),
            () -> System.err.println("Job submission failed.")
        );
    }}

   
    
