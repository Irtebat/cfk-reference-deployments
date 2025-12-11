# Daily Sales Value Processing Application

## Overview

The `DailySalesValue` application is a Flink-based system designed to process daily sales data from Kafka topics. It reads transactions, filters them based on specific conditions, and aggregates the results before writing them back to Kafka for further analysis or storage.

## Key Components

1. **Kafka Consumer Configuration**: The application configures Kafka consumers to read from two main topics: `OGG_VALUE_TXNHEADER` and `OGG_VALUE_TXNITEM`. It uses secure Kafka configurations with keytabs and truststores.

2. **Flink SQL Tables**: 
   - **TXNHEADER**: Represents transaction header data, including transaction ID, device ID, start time, status, type, and timestamps.
   - **TXNITEM**: Represents item details within a transaction, including transaction ID, device ID, sequence ID, start time, status, EAN or SKU, quantity, and timestamps.

3. **Intermediate Kafka Topics**:
   - **HEADER_ITEM_JOIN**: Stores the joined data along with relevant metadata from `TXNHEADER` and `TXNITEM`.
   - **SITE_ARTICLE_SALES_Delta**: Aggregates sales data by site and article, storing intermediate stock levels, delta dates, and other relevant information for validation.

4. **Data Processing**:
   - The application processes the data by filtering completed sales transactions and creating a join between `TXNHEADER` and `TXNITEM`.
   - It calculates the stock change for each item based on the transaction type (SALE or REFUND).

5. **Kafka Upsert Tables**: 
   - **SITE_ARTICLE_SALES**: Stores the aggregated sales data by site and article, reflecting the latest stock levels.

## Usage

To run this application, you need to have Flink installed and properly configured. Below are the steps to set it up:

1. **Build the Project**:
   ```sh
   mvn clean package
   ```

2. **Run the Application**:
   You can run the application from the command line or use a tool like `flink` to submit the job.
   ```sh
   flink run -c abc.rra.flink.source.rpos.DailySalesValue target/your-application.jar --cleanStartupFrom group-offsets --retainState retain
   ```

## Configuration

The application can be configured via command-line arguments:
- `--cleanStartupFrom`: Specifies the startup mode for Kafka consumers (`group-offsets`, `earliest-offset`, etc.).
- `--retainState`: Controls whether to retain externalized checkpoints (`retain` or `delete`).

## Data Validation

To validate the processed data, you can query the HANA table `RR_ANALYST.SITE_ARTICLE_SALES_HANA`. Below is an example SQL query that checks for discrepancies:

```sql
SELECT * FROM (
    SELECT
        LEFT(B.DEVICEID, 4) AS SITE,
        cast(A.TXNSTARTTIME AS DATE) SALE_DATE,
        ENTEREANORSKU AS ARTICLE,
        1000 AS SLOC,
        -1 * SUM(CAST(B.QUANTITY AS FLOAT)) AS STOCK,
        MAX(A.OP_TS) HEADER_TS,
        MIN(B.OP_TS) ITEM_TS,
        MAX(CASE WHEN A.OP_TYPE = 'I' THEN A.OP_TS ELSE NULL END) M_HEADER_TS,
        MAX(CASE WHEN B.OP_TYPE = 'I' THEN B.OP_TS ELSE NULL END) M_ITEM_TS
    FROM "HADMIN"."S_ROS_VALUE_TXNHEADER" A
    INNER JOIN "HADMIN"."S_ROS_VALUE_TXNITEM" B 
        ON A.TXNID = B.TXNID
        AND A.DEVICEID = B.DEVICEID
        AND A.TXNSTARTTIME = B.TXNSTARTTIME
    WHERE 
        A.TXNSTATUS = 'COMPLETED' AND
        A.TRANSACTIONTYPE IN ('SALE', 'REFUND')
        AND B.STATUS IS NULL
        AND A.op_ts >= CAST(CURRENT_DATE AS TIMESTAMP)
        -- AND ENTEREANORSKU = '490001545'
        --AND LEFT(B.DEVICEID, 4) IN ('U168')  --AND OP_TYPE<>'I'
        --site='T9GL' AND article='493024562'
        AND B.TXNSTARTTIME >= '2025-06-15 00:00:00.000'    AND B.op_ts >= CAST(CURRENT_DATE AS TIMESTAMP)
        AND A.TXNSTARTTIME >= '2025-06-15 00:00:00.000'
        --AND LEFT(B.DEVICEID, 4) in ('FRDD')
    GROUP BY
        LEFT(B.DEVICEID, 4), cast(A.TXNSTARTTIME AS DATE),
        ENTEREANORSKU
) A 
INNER JOIN RR_ANALYST.SITE_ARTICLE_SALES_HANA B 
ON A.SITE = B.SITE AND A.ARTICLE = B.Article AND A.SALE_DATE = B.SALE_DATE 
WHERE A.STOCK <> B.STOCK 
AND A.ITEM_TS = B.ITEM_DELTA_DATE 
AND A.HEADER_TS = B.HEADER_DELTA_DATE;
```

This query compares the intermediate stock levels stored in `SITE_ARTICLE_SALES_Delta` with the final aggregated data in `RR_ANALYST.SITE_ARTICLE_SALES_HANA`. Any discrepancies will be returned.

## Contributing

Contributions are welcome! Please follow the guidelines outlined in the [CONTRIBUTING.md](CONTRIBUTING.md) file.