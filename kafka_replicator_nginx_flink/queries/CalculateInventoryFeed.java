package ril.rra.flink.legos;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.AbstractDeserializationSchema;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.CheckpointConfig.ExternalizedCheckpointCleanup;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Expressions;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ril.rra.flink.legos.CalculateInventoryFeed.MardTSDeserializationSchema;

public class CalculateInventoryFeed {
        // POJO for InvFinalRecord
        

        public static void main(String[] args) throws Exception {
                // === 1. Calculate Midnight Timestamp ===
                LocalDate today = LocalDate.now();
                Instant midnight = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
                long midnightMillis = midnight.toEpochMilli();

                // === 2. Parse Startup Arguments ===
                String cleanStartupFrom = args.length > 0 ? args[0] : "group-offsets";
                String retainState = args.length > 1 ? args[1] : "retain";
                ExternalizedCheckpointCleanup retainPolicy = CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION;
                switch (retainState.toLowerCase()) {
                        case "retain":
                                retainPolicy = CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION;
                                break;
                        case "delete":
                                retainPolicy = CheckpointConfig.ExternalizedCheckpointCleanup.DELETE_ON_CANCELLATION;
                                break;
                        default:
                                retainPolicy = CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION;
                                break;
                }

                // === 3. Determine Kafka Scan Startup Mode ===
                String scanStartupMode = "'scan.startup.mode' = 'group-offsets',";
                switch (cleanStartupFrom.toLowerCase()) {
                        case "earliest":
                                break;
                        case "latest":
                                scanStartupMode = "'scan.startup.mode' = 'latest-offset',";
                                break;
                        case "group-offsets":
                                scanStartupMode = "'scan.startup.mode' = 'group-offsets',";
                                break;
                        case "timestamp":
                                scanStartupMode = "'scan.startup.mode' = 'timestamp',\n     'scan.startup.timestamp-millis' = '"
                                                + midnightMillis + "', ";
                                break;
                        default:
                                System.out.println("Invalid argument provided. Using 'group-offsets' by default.");
                                scanStartupMode = "'scan.startup.mode' = 'group-offsets',";
                                break;
                }

                // === 4. Kafka Connection Properties String ===
                String ogg_kafka_oggjson = "    'format' = 'ogg-json',\n" +
                                "   'ogg-json.ignore-parse-errors' = 'true',\n" +
                                "    'ogg-json.map-null-key.mode' = 'DROP',\n" +
                                "    'properties.group.id' = 'confluent_poc_fp',\n" +
                                "    'properties.bootstrap.servers' = 'sidcprdrrakfk01.ril.com:6667,sidcprdrrakfk02.ril.com:6667,sidcprdrrakfk03.ril.com:6667,sidcprdrrakfk04.ril.com:6667,sidcprdrrakfk05.ril.com:6667,sidcprdrrakfk06.ril.com:6667,sidcprdrrakfk07.ril.com:6667',\n"
                                +
                                "    'properties.security.protocol' = 'SASL_SSL',\n" +
                                "    'properties.sasl.mechanism' = 'SCRAM-SHA-512',\n" +
                                "    'properties.ssl.truststore.location' = '/newjks/truststore_ogg.jks',\n" +
                                "    'properties.ssl.truststore.password' = '7ecETGlHjzs',\n" +
                                "    'properties.auto.offset.reset' = 'earliest',\n" +
                                "    'properties.sasl.jaas.config' = 'org.apache.kafka.common.security.scram.ScramLoginModule required username=\"confluent_poc\" password=\"confluent_poc#1234\";'\n";

                // === 5. Flink Environment Setup ===
                EnvironmentSettings settings = EnvironmentSettings.newInstance().inStreamingMode().build();
                StreamExecutionEnvironment env1 = StreamExecutionEnvironment.getExecutionEnvironment();
                StreamTableEnvironment tableEnv1 = StreamTableEnvironment.create(env1, settings);

                env1.enableCheckpointing(300000); // 10 seconds
                env1.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
                CheckpointConfig config1 = env1.getCheckpointConfig();
                config1.setExternalizedCheckpointCleanup(retainPolicy);


                KafkaSource<MARDTS> source7 = KafkaSource.<MARDTS>builder().setBootstrapServers("sidcprdrrakfk01.ril.com:6667,sidcprdrrakfk02.ril.com:6667,sidcprdrrakfk03.ril.com:6667,sidcprdrrakfk04.ril.com:6667,sidcprdrrakfk05.ril.com:6667,sidcprdrrakfk06.ril.com:6667,sidcprdrrakfk07.ril.com:6667")
                                                .setProperty("partition.discovery.interval.ms", "1000").setTopics("OGG_P52_MARD_INV")
                                                .setGroupId("flink_p52ogg_tes1").setStartingOffsets(OffsetsInitializer.earliest())
                                                .setValueOnlyDeserializer(new MardTSDeserializationSchema()).setProperty("security.protocol", "SASL_SSL")
                                                .setProperty("sasl.mechanism", "SCRAM-SHA-512")
                                                .setProperty("ssl.truststore.location", "/newjks/truststore_ogg.jks")
                                                .setProperty("sasl.jaas.config",
                                                                "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"flink\" password=\"A46LUu2CGR2T9g\";")
                                                .setProperty("flink.partition-discovery.interval-millis", "6000").build();

                DataStreamSource<MARDTS> parsedMardTsrStream = env1.fromSource(
                source7,
                // Watermark strategy based on event time extracted from RosHeader objects
                //WatermarkStrategy.<MARDTS>forBoundedOutOfOrderness(Duration.ofMinutes(2))
                        //.withTimestampAssigner((event, timestamp) -> event.getCurrentTs().getTime()),
                                WatermarkStrategy.noWatermarks(),
                "kafka-MARDTS"
                );

                DataStream<MARDTS> filterMardTsStream=parsedMardTsrStream.filter(item->item.OP_TYPE!="D");

                DataStream<Tuple3<String, String, String>> flattenedStream = filterMardTsStream.map(new MapFunction<MARDTS, Tuple3<String,  String, String>>() {
                @Override
                public Tuple3<String, String, String> map(MARDTS item) {
                        return Tuple3.of(
                        item.after.SITE,
                        item.after.ARTICLE,
                                        Integer.toString(item.after.STOCK)
                        );
                }
                });

                Table table = tableEnv1.fromDataStream(flattenedStream,
                Expressions.$("f0").as("after_SITE"),
                Expressions.$("f1").as("after_ARTICLE"),
                Expressions.$("f2").as("after_STOCK")
                );

                DataStream<Row> finalStream = tableEnv1.toChangelogStream(table);
                // === 7. Convert Kafka Table to DataStream for InvFinalRecord ===
                //Table table_to_hana = tableEnv1.from("INV_FINAL_RECORD_VIEW");
                //DataStream<InvFinalRecord> parsedItemStream = tableEnv1.toDataStream(table_to_hana, InvFinalRecord.class);


                
                
                // 8.1 INV_FINAL_RECORD to RR_ANALYST.INV_FINAL_RECORD_HANA
                finalStream.addSink(
                                JdbcSink.sink(
                                                "UPSERT RR_ANALYST.INV_FINAL_RECORD_HANA " +
                                                "(SITE, ARTICLE, INITSTOCK) " +
                                                "VALUES (?, ?, ?) WITH PRIMARY KEY",
                                                (ps, record) -> {
                                                        ps.setString(1, record.getFieldAs(0));
                                                        ps.setString(2, record.getFieldAs(1));
                                                        ps.setString(3, record.getFieldAs(2));
                                                },
                                                JdbcExecutionOptions.builder()
                                                                .withBatchSize(1000)
                                                                .withBatchIntervalMs(20)
                                                                .withMaxRetries(3)
                                                                .build(),
                                                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                                                                .withUrl("jdbc:sap://10.129.176.34:30015")
                                                                .withDriverName("com.sap.db.jdbc.Driver")
                                                                .withUsername("RR_ANALYST")
                                                                .withPassword("RHP_Han_01")
                                                                .build()
                                )
                );

                // === 9. Execute Flink Job ===
                env1.execute("DB - Inventory Calculation - Stage2");
        }



public static class MARDTS {
  
    public String OP_TYPE;
 
    @JsonIgnoreProperties(ignoreUnknown = true)
    public After before;
    public After after;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public  static class After {
       public String SITE;
    public String ARTICLE;
    public String INITSTOCK;
    public int STOCK;
    public String STOCK_DATE;
    public int SALE;
    public String SALE_DATE;
    public int MSEG_STOCK;
    public String MSEG_DATE;
    public String MIN_OPEN_DAY;
    public String MAX_OPEN_DAY;
    public int TOTAL_QTY;
    public int TOTAL_OPEN_QTY;
    public int MB_SALE;
    public String MB_DATE;
    public String OP_TS;
    }
    
}


	public static class MardTSDeserializationSchema extends AbstractDeserializationSchema<MARDTS> {
		private static final long serialVersionUID = 1L;
		private transient ObjectMapper objectMapper;

		@Override
		public void open(InitializationContext context) {
			objectMapper = JsonMapper.builder().build().registerModule(new JavaTimeModule());
		}

		@Override
		public MARDTS deserialize(byte[] message) throws IOException {
			return objectMapper.readValue(message, MARDTS.class);
		}
	}
}
