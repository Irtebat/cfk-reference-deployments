package ril.rra.flink.source.sap.mseg;

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

public class DailyStockMovementValue {
    public static void main(String[] args) throws Exception {
        // Parse command-line arguments
        String cleanStartupFrom = args.length > 0 ? args[0] : "group-offsets";
        String retainState = args.length > 1 ? args[1] : "retain";

        // Set up environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        EnvironmentSettings settings = EnvironmentSettings.newInstance().inStreamingMode().build();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, settings);
        env.setParallelism(1); // Set parallelism to 1 for simplicity
        // Calculate midnight timestamp for today
        LocalDate today = LocalDate.now();
        long midnightMillis = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        // Configure checkpointing
        CheckpointConfig config = env.getCheckpointConfig();
        config.setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        config.setCheckpointInterval(300000); // 500 seconds
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
            "ngprdprp034389.bss.abs.com:9093,ngprdprp034390.bss.abs.com:9093,ngprdprp034391.bss.abs.com:9093,ngprdprp034392.bss.abs.com:9093,ngprdprp034393.bss.abs.com:9093,ngprdprp034394.bss.abs.com:9093,ngprdprp034395.bss.abs.com:9093,ngprdprp034396.bss.abs.com:9093,ngprdprp034397.bss.abs.com:9093,ngprdprp034398.bss.abs.com:9093", 
            "/newjks/SI_rra.keytab", 
            "SI_rra@absMARTKAFKAPROD.COM", 
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


        //MSEG DDL
        String ddl_mseg = "CREATE TABLE MSEG (\n" +
                "    WERKS STRING,\n" +
                "    MATNR STRING,\n" +
                "    BWART STRING,\n" +
                " MBLNR STRING,\n" +
                " MJAHR STRING,\n" +
                " ZEILE STRING,\n" +
                "    MENGE INT,\n" +
                "    BUDAT_MKPF STRING,\n" +
                "    CPUDT_MKPF STRING,\n" +
                "    CPUTM_MKPF STRING,\n" +
                "    op_type STRING,\n" +
                "    op_ts TIMESTAMP(3),\n" +
                "    WATERMARK FOR op_ts AS op_ts - INTERVAL '5' SECOND " +
                ") WITH (\n" +
                "    'connector' = 'kafka',\n" +
                "    'topic' = 'OGG_P51_MSEG',\n" +
                "    'scan.startup.mode' =  'earliest-offset',\n" +
                //"    'scan.startup.mode' = 'timestamp',\n" +
               // "    'scan.startup.timestamp-millis' = '" + midnightMillis + "',\n" +
                centralKafkaConsume +
                "\n);";
        tableEnv.executeSql(ddl_mseg);

        String ddl_mseg_final = "CREATE TABLE MSEG_FINAL (\n" +
                "  MATNR STRING,\n" +
                "  WERKS STRING,\n" +
                " op_date_str STRING,\n" +
                "  LABST INT,\n" +
                "  op_ts TIMESTAMP(3),\n" +
                "    PRIMARY KEY (MATNR, WERKS,op_date_str) NOT ENFORCED \n" +
                ") WITH (\n" +
                "    'connector' = 'upsert-kafka',\n" +
                "    'topic' = 'CONFLUENT_MSEG_FINAL',\n" +
                oggKafkaUpsert +
                ");";
         tableEnv.executeSql(ddl_mseg_final);
         String sqlmseg_3_1_mseg = 
         "INSERT INTO MSEG_FINAL\n" +
         "SELECT\n" +
         "  MATNR,\n" +
         "  WERKS,\n" +
         "  op_date AS op_date_str,\n" +  // op_date is already in desired format (yyyy-MM-dd)
         "  CEIL(SUM(\n" +
         "    CASE\n" +
         "      WHEN BWART = '251' THEN MENGE\n" +
         "      WHEN BWART = '252' THEN MENGE * -1\n" +
         "      ELSE 0\n" +
         "    END\n" +
         "  )) AS LABST,\n" +
         "  MAX(CAST(op_ts AS TIMESTAMP)) AS op_ts\n" +  // Convert op_ts to TIMESTAMP
         "FROM (\n" +
         "  SELECT *\n" +
         "  FROM (\n" +
         "    SELECT *,\n" +
         "           ROW_NUMBER() OVER (\n" +
         "             PARTITION BY MBLNR, MJAHR, ZEILE\n" +
         "             ORDER BY DATE_FORMAT(op_ts, 'yyyy-MM-dd HH:mm:ss') DESC\n" + 
         "           ) AS rn\n" +
         "    FROM (\n" +
         "      SELECT /*+ BROADCAST(N) */ M.MATNR, M.WERKS, CAST(M.op_ts AS TIMESTAMP(3)) AS op_ts, M.BWART, M.MENGE, M.MBLNR, M.MJAHR, M.ZEILE, M.BUDAT_MKPF, M.CPUDT_MKPF, M.CPUTM_MKPF,\n" +
         "             DATE_FORMAT(M.op_ts, 'yyyy-MM-dd') AS op_date\n" +
         "      FROM MSEG M\n" +
         "      INNER JOIN NODE_MASTER_DEDUPED N\n" +
         "      ON M.WERKS = N.STORE_ID\n" +
         "      WHERE BWART IN ('251','252') \n" +
         "        AND TO_DATE(BUDAT_MKPF, 'yyyyMMdd') >= CURRENT_DATE\n" +
         "        AND TO_DATE(CPUDT_MKPF, 'yyyyMMdd') >= CURRENT_DATE\n" +
         "        AND CPUTM_MKPF >= '050000'\n" +
         "    )\n" +
         "  )\n" +
         "    WHERE rn = 1\n" +
         ")\n" +
         "GROUP BY MATNR, WERKS, op_date";
     


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
        statementSet.addInsertSql(sqlmseg_3_1_mseg);


        // Execute the job
        TableResult result = statementSet.execute();
        result.getJobClient().ifPresentOrElse(
            jobClient -> System.out.println("Job submitted with JobID: " + jobClient.getJobID()),
            () -> System.err.println("Job submission failed.")
        );
    }}

   
    
