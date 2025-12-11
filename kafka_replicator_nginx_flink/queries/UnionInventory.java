package ril.rra.flink.legos;

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

public class UnionInventory {
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
                "ngprdprp034389.bss.jio.com:9093,ngprdprp034390.bss.jio.com:9093,ngprdprp034391.bss.jio.com:9093,ngprdprp034392.bss.jio.com:9093,ngprdprp034393.bss.jio.com:9093,ngprdprp034394.bss.jio.com:9093,ngprdprp034395.bss.jio.com:9093,ngprdprp034396.bss.jio.com:9093,ngprdprp034397.bss.jio.com:9093,ngprdprp034398.bss.jio.com:9093",
                "/newjks/SI_rra.keytab",
                "SI_rra@JIOMARTKAFKAPROD.COM",
                "/newjks/server-keystore.jks",
                "changeit",
                "/newjks/truststore_1.jks",
                "changeit"
        );


        String oggKafkaProduce = KafkaUtils.buildKafkaConfig(
                "confluent_poc_inv_final",
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

        String ddl_conf1_out = "CREATE TABLE MILKBASKET_DEDUPE (\n" +
                "    SITE STRING,\n" +
                "    ARTICLE STRING,\n" +
                "    ORDER_DATE STRING,\n" +
                "    QUANTITY INT,\n" +
                "    RECORD_DATE TIMESTAMP(3),\n" +
                "    LAST_MODIFIED_DATE TIMESTAMP(3),\n" +
                "    PRIMARY KEY (SITE, ARTICLE, ORDER_DATE) NOT ENFORCED\n" +
                ") WITH (\n" +
                "    'connector' = 'upsert-kafka',\n" +
                "    'topic' = 'CONFLUENT_2',\n" +
                oggKafkaUpsert +
                ");";
        tableEnv.executeSql(ddl_conf1_out);


        String ddl_sink = "CREATE TABLE SITE_ARTICLE_SALES (\n" +
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
                " kafka_topic STRING" +

                ") WITH (\n" +
                "    'connector' = 'kafka', " +
                "    'topic' = 'CONFLUENT_SALES_ARTICLE',\n" +
                KafkaUtils.getStartupConfig(cleanStartupFrom, midnightMillis) + "\n" +

                oggKafkaProduce +
                ");";

        tableEnv.executeSql(ddl_sink);


        String ddl_inv_final = "CREATE TABLE INV_FINAL (\n" +
                "  SITE STRING,\n" +
                "  ARTICLE STRING,\n" +
                "  STOCK INT,\n" +
                "  SALE BIGINT,\n" +
                "  SALE_DATE STRING,\n" +
                "  op_ts TIMESTAMP(3),\n" +
                "  MSEG_STOCK INT,\n" +
                "  MSEG_DATE STRING,\n" +
                "  DSALE INT,\n" +
                "  DMSEG_STOCK INT,\n" +
                "  INVENTORY INT,\n" +
                "  STOCK_DATE STRING,\n" +
                "  MIN_OPEN_DAY STRING,\n" +
                "  MAX_OPEN_DAY STRING,\n" +
                "  TOTAL_QTY BIGINT,\n" +
                "  TOTAL_OPEN_QTY BIGINT,\n" +
                "  MB_SALE BIGINT,\n" +
                "  MB_DATE STRING,\n" +
                "  PRIMARY KEY (SITE, ARTICLE) NOT ENFORCED \n" +
                ") WITH (\n" +
                "    'connector' = 'upsert-kafka', " +
                "    'topic' = 'CONFLUENT_INV_FINAL',\n" +
                oggKafkaUpsert +
                ");";
        tableEnv.executeSql(ddl_inv_final);


        String ddl_mard_final = "CREATE TABLE MARD_FINAL (\n" +
                "  MATNR STRING,\n" +
                "  WERKS STRING,\n" +
                "    LABST INT,\n" +
                "  op_ts TIMESTAMP(3)\n" +

                ") WITH (\n" +
                "    'connector' = 'kafka', " +
                "    'topic' = 'CONFLUENT_MARD_FINAL',\n" +
                KafkaUtils.getStartupConfig(cleanStartupFrom, midnightMillis) + "\n" +
                oggKafkaProduce +
                ");";
        tableEnv.executeSql(ddl_mard_final);

        String ddl_expstore_order_smart_status_staging = "CREATE TABLE EXPSTORE_ORDER_SMART_STATUS_STAGING (\n" +
                "    ORDERID STRING,\n" +
                "    SHIPMENT_ID STRING,\n" +
                "    LAND_STATUS STRING,\n" +
                "    op_type STRING,\n" +
                "    op_ts TIMESTAMP(3)\n" +

                ") WITH (\n" +
                "    'connector' = 'kafka', " +
                "    'topic' = 'CONFLUENT_3',\n" +
                KafkaUtils.getStartupConfig(cleanStartupFrom, midnightMillis) + "\n" +
                oggKafkaProduce +
                ");";
        tableEnv.executeSql(ddl_expstore_order_smart_status_staging);

        String ddl_mseg_final = "CREATE TABLE MSEG_FINAL (\n" +
                "  MATNR STRING,\n" +
                "  WERKS STRING,\n" +
                " op_date_str STRING,\n" +
                "  LABST INT,\n" +
                "  op_ts TIMESTAMP(3)\n" +

                ") WITH (\n" +
                "    'connector' = 'kafka', " +
                "    'topic' = 'CONFLUENT_MSEG_FINAL',\n" +
                KafkaUtils.getStartupConfig(cleanStartupFrom, midnightMillis) + "\n" +
                oggKafkaProduce +
                ");";
        tableEnv.executeSql(ddl_mseg_final);


        String ddl_expstore_order_aggregated = "CREATE TABLE EXPSTORE_ORDER_AGGREGATED (\n" +
                "    STORENO STRING,\n" +
                "    ARTICLEID STRING,\n" +
                "    TOTAL_QTY DOUBLE,\n" +
                "    ORDERDATE STRING,\n" +
                "    OPEN_ORDER_QTY DOUBLE,\n" +
                "    op_ts STRING\n" +

                ") WITH (\n" +
                "    'connector' = 'kafka',\n" +
                "    'topic' = 'CONFLUENT_JIOMART_OPEN_ORDERS',\n" +
                KafkaUtils.getStartupConfig(cleanStartupFrom, midnightMillis) + "\n" +
                oggKafkaProduce +
                ");";

        tableEnv.executeSql(ddl_expstore_order_aggregated);


        String ddl_jmt_node_master = "CREATE TABLE JMT_NODE_MASTER (\n" +
                "    SHIP_NODE STRING,\n" +
                "    FORMAT_CD STRING,\n" +
                "    NODE_TYPE STRING,\n" +
                "    QUEUE_NAME STRING,\n" +
                "    FEED_FLAG STRING,\n" +
                "    LAST_MODIFIED_DATE STRING\n" +

//  "    WATERMARK FOR LAST_MODIFIED_DATE AS LAST_MODIFIED_DATE - INTERVAL '1' SECOND\n" +
                ") WITH (\n" +
                "    'connector' = 'kafka',\n" +
                "    'topic' = 'CONFLUENT_S_JMT_SHIPNODE_MASTER',\n" +
                "    'scan.startup.mode' = 'earliest-offset',\n" +
                oggKafkaProduce +

                ");";

        tableEnv.executeSql(ddl_jmt_node_master);

        String ddl_node_master_deduped = "CREATE TABLE NODE_MASTER_DEDUPED (\n" +
                "    STORE_ID STRING,\n" +
                "    FORMAT_CODE STRING,\n" +
                "    NODE_TYPE STRING,\n" +
                "    QUEUE_NAME STRING,\n" +
                "    FEED_FLAG STRING,\n" +
                "    LAST_MODIFIED_DATE STRING,\n" +
                "    PRIMARY KEY (STORE_ID) NOT ENFORCED\n" +
                ") WITH (\n" +
                "    'connector' = 'upsert-kafka',\n" +
                "    'topic' = 'CONFLUENT_S_FNL_NODE_MASTER_DEDUPE',\n" +

                oggKafkaUpsert +
                ");";
        tableEnv.executeSql(ddl_node_master_deduped);

        String sqlnode_4_1_nodededupe = "INSERT INTO NODE_MASTER_DEDUPED\n" +
                "SELECT SHIP_NODE AS STORE_ID, FORMAT_CD AS FORMAT_CODE, NODE_TYPE,QUEUE_NAME, FEED_FLAG, LAST_MODIFIED_DATE\n" +
                "FROM (\n" +
                "    SELECT *, ROW_NUMBER() OVER (PARTITION BY SHIP_NODE ORDER BY LAST_MODIFIED_DATE DESC) AS row_num\n" +
                "    FROM JMT_NODE_MASTER\n" +
                "    WHERE  QUEUE_NAME='GROCERY'AND FEED_FLAG='Y'\n" +
                ")\n" +
                "WHERE row_num = 1";


        String flinkSQLQuery_milkbasket_today =
                "SELECT " +
                        "  SITE, " +
                        "  ARTICLE, " +
                        "  0 AS STOCK, " +
                        "  0 AS SALE, " +
                        "  '' AS SALE_DATE, " +
                        "  LAST_MODIFIED_DATE AS op_ts, " +
                        "  0 AS MSEG_STOCK, " +
                        "  '' AS MSEG_DATE, " +
                        "  CAST(NULL AS STRING) AS MIN_OPEN_DAY, " +
                        "  CAST(NULL AS STRING) AS MAX_OPEN_DAY, " +
                        "  0 AS TOTAL_QTY, " +
                        "  0 AS TOTAL_OPEN_QTY, " +
                        "  '' AS STOCK_DATE, " +
                        "  QUANTITY AS MB_SALE, " +
                        "  ORDER_DATE AS MB_DATE " +
                        "FROM MILKBASKET_DEDUPE " +
                        "WHERE ORDER_DATE = CAST(CURRENT_DATE AS STRING)";

        String sqlrpos_1_2_site_article5d_mod =
                "SELECT " +
                        "  STORENO AS SITE, " +
                        "  ARTICLEID AS ARTICLE, " +
                        "  0 AS STOCK, " +
                        "  0 AS SALE, " +
                        "  '' AS SALE_DATE, " +
                        "  CAST(MAX(op_ts) AS TIMESTAMP(3)) AS op_ts, " +
                        "  0 AS MSEG_STOCK, " +
                        "  '' AS MSEG_DATE, " +
                        "  MIN(ORDERDATE) AS MIN_OPEN_DAY, " +
                        "  MAX(ORDERDATE) AS MAX_OPEN_DAY, " +
                        "  CAST(SUM(TOTAL_QTY) AS BIGINT) AS TOTAL_QTY, " +
                        "  CAST(SUM(OPEN_ORDER_QTY) AS BIGINT) AS TOTAL_OPEN_QTY, " +
                        "  '' AS STOCK_DATE, " +
                        "  0 AS MB_SALE, " +
                        "  '' AS MB_DATE " +
                        "FROM EXPSTORE_ORDER_AGGREGATED " +
                        "WHERE TO_DATE(ORDERDATE) >= TIMESTAMPADD(DAY, -15, CURRENT_DATE) " +
                        "GROUP BY STORENO, ARTICLEID";

        String flinkSQLQuery_4_mod_mb =
                "SELECT " +
                        "  WERKS AS SITE, " +
                        "  LTRIM(MATNR, '0') AS ARTICLE, " +
                        "  LABST AS STOCK, " +
                        "  0 AS SALE, " +
                        "  '' AS SALE_DATE, " +
                        "  op_ts, " +
                        "  0 AS MSEG_STOCK, " +
                        "  '' AS MSEG_DATE, " +
                        "  CAST(NULL AS STRING) AS MIN_OPEN_DAY, " +
                        "  CAST(NULL AS STRING) AS MAX_OPEN_DAY, " +
                        "  0 AS TOTAL_QTY, " +
                        "  0 AS TOTAL_OPEN_QTY, " +
                        "  DATE_FORMAT(op_ts, 'yyyy-MM-dd') as STOCK_DATE, " +
                        "  0 AS MB_SALE, " +
                        "  '' AS MB_DATE " +
                        "FROM MARD_FINAL";

        String flinkSQLQuery_sales_mod_mb =
                "SELECT " +
                        "  SITE, " +
                        "  ARTICLE, " +
                        "  0 AS STOCK, " +
                        "  STOCK AS SALE, " +
                        "  SALE_DATE, " +
                        "  kafka_ts AS op_ts, " +
                        "  0 AS MSEG_STOCK, " +
                        "  '' AS MSEG_DATE, " +
                        "  CAST(NULL AS STRING) AS MIN_OPEN_DAY, " +
                        "  CAST(NULL AS STRING) AS MAX_OPEN_DAY, " +
                        "  0 AS TOTAL_QTY, " +
                        "  0 AS TOTAL_OPEN_QTY, " +
                        "  '' AS STOCK_DATE, " +
                        "  0 AS MB_SALE, " +
                        "  '' AS MB_DATE " +
                        "FROM SITE_ARTICLE_SALES " +
                        "WHERE SALE_DATE = CAST(CURRENT_DATE AS STRING)";

        String flinkSQLQuery_mseg_mod_mb =
                "SELECT " +
                        "  WERKS AS SITE, " +
                        "  LTRIM(MATNR, '0') AS ARTICLE, " +
                        "  0 AS STOCK, " +
                        "  0 AS SALE, " +
                        "  '' AS SALE_DATE, " +
                        "  op_ts, " +
                        "  LABST AS MSEG_STOCK, " +
                        "  op_date_str AS MSEG_DATE, " +
                        "  CAST(NULL AS STRING) AS MIN_OPEN_DAY, " +
                        "  CAST(NULL AS STRING) AS MAX_OPEN_DAY, " +
                        "  0 AS TOTAL_QTY, " +
                        "  0 AS TOTAL_OPEN_QTY, " +
                        "  '' AS STOCK_DATE, " +
                        "  0 AS MB_SALE, " +
                        "  '' AS MB_DATE " +
                        "FROM MSEG_FINAL " +
                        "WHERE op_date_str = CAST(CURRENT_DATE AS STRING)";

        // Final union query with MB_SALE and MB_DATE in all subqueries
        String sqlunion_11_1_inv_union =
                "INSERT INTO INV_FINAL\n" +
                        "SELECT SITE, ARTICLE, " +
                        "SUM(STOCK) as STOCK, " +
                        "SUM(SALE) as SALE, " +
                        "MAX(SALE_DATE) as SALE_DATE, " +
                        "MAX(op_ts) as op_ts, " +
                        "SUM(MSEG_STOCK) as MSEG_STOCK, " +
                        "MAX(MSEG_DATE) as MSEG_DATE, " +
                        "CAST(SUM(CASE WHEN TO_DATE(SALE_DATE) = CURRENT_DATE THEN SALE ELSE 0 END) AS INT) AS DSALE, " +
                        "SUM(CASE WHEN TO_DATE(MSEG_DATE) = CURRENT_DATE THEN MSEG_STOCK ELSE 0 END) AS DMSEG_STOCK, " +
                        "CAST(SUM((CASE WHEN TO_DATE(SALE_DATE) = CURRENT_DATE THEN SALE ELSE 0 END) + " +
                        "(CASE WHEN TO_DATE(MSEG_DATE) = CURRENT_DATE THEN MSEG_STOCK ELSE 0 END) + STOCK) AS INT) AS INVENTORY, " +
                        "MAX(STOCK_DATE) as STOCK_DATE, " +
                        "MIN(MIN_OPEN_DAY) AS MIN_OPEN_DAY, " +
                        "MAX(MAX_OPEN_DAY) AS MAX_OPEN_DAY, " +
                        "SUM(TOTAL_QTY) AS TOTAL_QTY, " +
                        "SUM(TOTAL_OPEN_QTY) AS TOTAL_OPEN_QTY, " +
                        "SUM(MB_SALE) AS MB_SALE, " +
                        "MAX(MB_DATE) AS MB_DATE\n" +
                        "FROM (\n" +
                        "  " + flinkSQLQuery_4_mod_mb + "\n" +
                        "  UNION ALL\n" +
                        "  " + flinkSQLQuery_sales_mod_mb + "\n" +
                        "  UNION ALL\n" +
                        "  " + flinkSQLQuery_mseg_mod_mb + "\n" +
                        "  UNION ALL\n" +
                        "  " + sqlrpos_1_2_site_article5d_mod + "\n" +
                        "  UNION ALL\n" +
                        "  " + flinkSQLQuery_milkbasket_today + "\n" +
                        ") \n" +
                        "GROUP BY SITE, ARTICLE";


        // Build and execute SQL queries

        StatementSet statementSet = tableEnv.createStatementSet();
        statementSet.addInsertSql(sqlnode_4_1_nodededupe);
        statementSet.addInsertSql(sqlunion_11_1_inv_union);


        // Execute the job
        TableResult result = statementSet.execute();
        result.getJobClient().ifPresentOrElse(
                jobClient -> System.out.println("Job submitted with JobID: " + jobClient.getJobID()),
                () -> System.err.println("Job submission failed.")
        );
    }}



