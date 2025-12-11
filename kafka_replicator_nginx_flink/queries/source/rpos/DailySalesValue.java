package ril.rra.flink.source.rpos;

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

public class DailySalesValue {

    //ril.rra.flink.source.rpos.DailySalesValue
    public static void main(String[] args) throws Exception {
        // Parse command-line arguments
        String cleanStartupFrom = args.length > 0 ? args[0] : "group-offsets";
        String retainState = args.length > 1 ? args[1] : "retain";

        // Set up environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        EnvironmentSettings settings = EnvironmentSettings.newInstance().inStreamingMode().build();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, settings);

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
            "ngprdprp034389.bss.xyz.com:9093,ngprdprp034390.bss.xyz.com:9093,ngprdprp034391.bss.xyz.com:9093,ngprdprp034392.bss.xyz.com:9093,ngprdprp034393.bss.xyz.com:9093,ngprdprp034394.bss.xyz.com:9093,ngprdprp034395.bss.xyz.com:9093,ngprdprp034396.bss.xyz.com:9093,ngprdprp034397.bss.xyz.com:9093,ngprdprp034398.bss.xyz.com:9093", 
            "/newjks/SI_rra.keytab", 
            "SI_rra@JIOMARTKAFKAPROD.COM", 
            "/newjks/server-keystore.jks", 
            "changeit", 
            "/newjks/truststore_1.jks", 
            "changeit"
        );


        String oggKafkaProduce = KafkaUtils.buildKafkaConfig(
            "confluent_poc_dailysales", 
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

        
        String createHeaderSQL = "CREATE TABLE TXNHEADER (" +
                "    TXNID STRING, " +
                "    DEVICEID STRING, " +
                "    TXNSTARTTIME TIMESTAMP(0), " +
                "    TXNSTATUS STRING, " +
                "    TRANSACTIONTYPE STRING, " +
                "    op_ts TIMESTAMP(3),\n" +
                "   op_type STRING, " +
                "    WATERMARK FOR op_ts AS op_ts -  INTERVAL '60' SECOND \n" +
                ") WITH (" +
                "    'connector' = 'kafka', " +
                "    'topic' = 'OGG_VALUE_TXNHEADER', " +
              // "    'topic' = 'OGG_FTPRINT_TXNHEADER', " +
             KafkaUtils.getStartupConfig(cleanStartupFrom, midnightMillis) + "\n" +
            centralKafkaConsume + "\n" +
                ");";

        // Execute the Flink SQL to create the table
        tableEnv.executeSql(createHeaderSQL);

        String createItemSQL = "CREATE TABLE TXNITEM (" +
                "   TXNID   STRING, " +
                "   DEVICEID   STRING, " +
                "  SEQUENCEID STRING, " +
                "   TXNSTARTTIME  TIMESTAMP(0), " +
                "   STATUS STRING, " +
                "   ENTEREANORSKU STRING, " +
                "   QUANTITY INT, " +
                "    op_ts TIMESTAMP(3),\n" +
                "   op_type STRING, " +
                "    WATERMARK FOR op_ts AS op_ts - INTERVAL '60' SECOND  " +
                ") WITH (" +
                "    'connector' = 'kafka', " +
                "    'topic' = 'OGG_VALUE_TXNITEM', " +
             //  "    'topic' = 'OGG_FTPRINT_TXNITEM', " +
                //" 'scan.startup.mode' =  'group-offsets', \n " +
              KafkaUtils.getStartupConfig(cleanStartupFrom, midnightMillis) + "\n" +
            centralKafkaConsume + "\n" +
                ");";

        // Execute the Flink SQL to create the table
        tableEnv.executeSql(createItemSQL);


        
        String ddl_sink_1 = "CREATE TABLE CONFLUENT_SITE_ARTICLE_TICKET (\n" +
                "  SITE STRING, " +
                "  ARTICLE STRING, " +
                "   TXNID STRING, " +
                "  DEVICEID STRING, " +
                "  SEQUENCEID STRING, " +
                "   TXNSTARTTIME TIMESTAMP(0), " +
                " SALE_DATE STRING," +
                "  SLOC INT, " +
                "  STOCK BIGINT, " +
                "  QUANTITY_I BIGINT, " +
                "  QUANTITY_U BIGINT, " +
                "  A_SALE_DELTA_DATE STRING, " +
                "  B_SALE_DELTA_DATE STRING ," +
                " kafka_ts TIMESTAMP(3) METADATA FROM 'timestamp' VIRTUAL, " +
                " kafka_topic STRING METADATA FROM 'topic' VIRTUAL," +
                "    PRIMARY KEY (TXNID, DEVICEID,TXNSTARTTIME,ARTICLE,SEQUENCEID) NOT ENFORCED \n" +
                ") WITH (\n" +
                "    'connector' = 'upsert-kafka',\n" +
                "    'topic' = 'CONFLUENT_SITE_ARTICLE_TICKET',\n" +
                 oggKafkaUpsert+
                ");";

        tableEnv.executeSql(ddl_sink_1);

        String ddl_sink = "CREATE TABLE CONFLUENT_SALES_ARTICLE (\n" +
                "  SITE STRING, " +
                "  ARTICLE STRING, " +
                "  SLOC INT, " +
                "  STOCK BIGINT, " +
                "  SALE_DATE STRING, " +
                "  MARD_DELTA_DATE STRING, " +
                "  HEADER_DELTA_DATE STRING, " +
                "  ITEM_DELTA_DATE STRING, " +
                "  SOURCE_TYPE STRING, " +
                " kafka_ts TIMESTAMP(3), " +
                " kafka_topic STRING," +
                "    PRIMARY KEY (SALE_DATE,SITE, ARTICLE) NOT ENFORCED \n" +
                ") WITH (\n" +
                "    'connector' = 'upsert-kafka',\n" +
                "    'topic' = 'CONFLUENT_SALES_ARTICLE',\n" +
   oggKafkaUpsert+
                ");";

        tableEnv.executeSql(ddl_sink);

         String sqlrpos_1_1_header_item = "INSERT INTO CONFLUENT_SITE_ARTICLE_TICKET \n" +       
        "WITH Filtered_TXNHEADER AS (\n" +
                                "         SELECT *\n" +
                                "         FROM TXNHEADER\n" +
                                "         WHERE TXNSTATUS = 'COMPLETED'\n" +
                                "            AND TRANSACTIONTYPE IN ('SALE', 'REFUND')\n" +
                                "            AND TXNSTARTTIME >= CAST(CURRENT_DATE AS TIMESTAMP)\n" +
                                "            AND op_ts >= CAST(CURRENT_DATE AS TIMESTAMP)\n" +
                                 "            AND op_type = 'I'\n" +

                                "),\n" +
                                "    Filtered_TXNITEM AS (\n" +
                                "         SELECT *\n" +
                                "         FROM TXNITEM\n" +
                                "         WHERE STATUS IS NULL\n" +
                                "            AND TXNSTARTTIME >= CAST(CURRENT_DATE AS TIMESTAMP)\n" +
                                "            AND op_ts >= CAST(CURRENT_DATE AS TIMESTAMP)\n" +
                                 "            AND op_type = 'I'\n" +

                                ")\n" +
        "SELECT /*+ STATE_TTL('A'='1d', 'B'='1d') */\n" +
                "    LEFT(B.DEVICEID, 4) AS SITE,\n" +
                "    ENTEREANORSKU AS ARTICLE,\n" +
                "    B.TXNID AS TXNID,\n" +
                "    B.DEVICEID AS DEVICEID,\n" +
                "    B.SEQUENCEID AS SEQUENCEID,\n" +
                "    B.TXNSTARTTIME AS TXNSTARTTIME,\n" +
                "    DATE_FORMAT(B.TXNSTARTTIME, 'yyyy-MM-dd') AS SALE_DATE,\n" +
                "    1000 AS SLOC,\n" +
                "    -1 * (B.QUANTITY) AS STOCK,\n" +
                "    CASE WHEN B.op_type = 'I' THEN B.QUANTITY ELSE 0 END AS QUANTITY_I,\n" +
                "    CASE WHEN B.op_type = 'U' THEN B.QUANTITY ELSE 0 END AS QUANTITY_U,\n" +
                "    DATE_FORMAT(A.op_ts, 'yyyyMMddHHmmss') AS A_SALE_DELTA_DATE,\n" +
                "    DATE_FORMAT(B.op_ts, 'yyyyMMddHHmmss') AS B_SALE_DELTA_DATE\n" + // Removed the extra comma here
                "FROM Filtered_TXNHEADER A\n" +
                "INNER JOIN Filtered_TXNITEM B\n" +
                "ON A.TXNID = B.TXNID\n" +
                "AND A.DEVICEID = B.DEVICEID\n" +
                "AND A.TXNSTARTTIME = B.TXNSTARTTIME\n" +
                "AND A.op_ts BETWEEN B.op_ts - INTERVAL '24' HOUR AND B.op_ts + INTERVAL '24' HOUR";

                String flinksql_upsetr = " SELECT  SITE, ARTICLE, 1000 AS SLOC,SUM(STOCK) AS STOCK,SALE_DATE, MAX(CAST(NULL AS STRING)) AS MARD_DELTA_DATE, MAX(A_SALE_DELTA_DATE) AS HEADER_DELTA_DATE, MAX(B_SALE_DELTA_DATE) AS ITEM_DELTA_DATE, 'TXN' AS SOURCE_TYPE, MAX(kafka_ts) AS kafka_ts, MAX(kafka_topic) AS kafka_topic FROM CONFLUENT_SITE_ARTICLE_TICKET WHERE SALE_DATE IS NOT NULL AND STOCK IS NOT NULL AND TO_TIMESTAMP(A_SALE_DELTA_DATE, 'yyyyMMddHHmmss') >= CAST(CURRENT_DATE AS TIMESTAMP) GROUP BY SALE_DATE, SITE, ARTICLE";

                        String sqlrpos_1_2_site_article = "INSERT INTO CONFLUENT_SALES_ARTICLE " + flinksql_upsetr; 

              


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
        statementSet.addInsertSql(sqlrpos_1_1_header_item);
        statementSet.addInsertSql(sqlrpos_1_2_site_article);

        Configuration globalConfiguration = new Configuration();
        globalConfiguration.setString("jobName", "DailySalesValueJob");
        env.getConfig().setGlobalJobParameters(globalConfiguration);
        // Execute the job
        TableResult result = statementSet.execute();
        result.getJobClient().ifPresentOrElse(
            jobClient -> System.out.println("Job submitted with JobID: " + jobClient.getJobID()),
            () -> System.err.println("Job submission failed.")
        );
    }}

   
    
