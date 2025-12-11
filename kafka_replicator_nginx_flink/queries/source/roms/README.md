"""
Flink Job to Process Open Orders from Jiomart Data Feed
=====================================================

This Flink job processes open orders data from Jiomart Kafka topic and outputs aggregated results to Confluent Kafka topics.

Configuration Options
--------------------

* `cleanStartupFrom`: specifies the startup mode for Flink (either "group-offsets" or a timestamp)
* `retainState`: determines whether to retain checkpoint state on cancellation ("retain") or delete it ("delete")

Job Execution
-------------

1. Set up Flink environment and configure Kafka connector settings.
2. Create tables for EXPSTORE_ORDER, EXPSTORE_ORDER_ITEM, and ORDER_STATE using Kafka connectors.
3. Build SQL queries to insert data into staging tables (EXPSTORE_ORDER_SMART_STATUS_STAGING) and broadcast-join with NODE_MASTER_DEDUPED table.
4. Execute SQL queries to output aggregated results to Confluent Kafka topics.

"""


SELECT store,ARTICLE_CODE,A.ORDERDATE,SELL_QTY,B.* FROM (
SELECT
B.STORENO STORE,to_date(ORDERDATE) ORDERDATE,
'NA' STORE_FORMAT,
'NA' NODE_TYPE, 
B.ARTICLE_CODE, 
'NA' EAN,
-1*(SUM(QTY)) SELL_QTY,
0 NET_QTY,

TO_NVARCHAR(MAX(B.O_RECORD_DATE),'yyyymmddhh24miss') DELTA_DATE,
TO_NVARCHAR(MAX(B.O_RECORD_DATE),'yyyymmddhh24miss') SALE_DELTA_DATE, 
'NA' STORE_RECORD_DATE,
'NA' STORE_LM_DATE
FROM "RR_JMTOMS"."EXPSTORE_ORDER_SMART_STATUS" A
INNER JOIN 
 (SELECT A.ORDERID, B.ARTICLEID ARTICLE_CODE,ORDERDATE,
MAX(B.RECORD_DATE) O_RECORD_DATE,
FIRST_VALUE(B.SHIPMENT_ID ORDER BY SHIPMENT_ID DESC)SHIPMENT_ID,
FIRST_VALUE(B.STORENO ORDER BY SHIPMENT_ID DESC)STORENO,
FIRST_VALUE(TO_DECIMAL(QTY) ORDER BY SHIPMENT_ID DESC)QTY
FROM RR_JMTOMS.EXPSTORE_ORDER A 
INNER JOIN RR_JMTOMS.EXPSTORE_ORDERITEM B ON (A.ORDERID=B.ORDERID)
WHERE TO_DECIMAL(TOT_ORDER_AMT) > 0  AND 
B.STORENO='2946' --AND B.ARTICLEID='493180055'
AND TO_DECIMAL(QTY)>0 
AND TO_DATE(A.ORDERDATE)>=ADD_DAYS(CURRENT_DATE,-1)
GROUP BY A.ORDERID, B.ARTICLEID,ORDERDATE) B ON A.ORDERID=B.ORDERID AND A.SHIPMENT_ID=B.SHIPMENT_ID
WHERE A.LAND_STATUS='OPEN'
GROUP BY B.STORENO, B.ARTICLE_CODE,to_date(ORDERDATE))
A
INNER JOIN (SELECT * FROM RR_ANALYST.EXPSTORE_ORDER_AGGREGATED WHERE OPEN_ORDER_QTY>0) B ON A.STORE=B.STORENO AND A.ARTICLE_CODE=B.ARTICLEID AND A.ORDERDATE=B.ORDERDATE