The provided create-topic.yaml may be used as a reference or template for creating Kafka topics.

Custom configurations can be specified under the .spec.config section as key=value pairs.

Managed topics are created as Confluent custom resources. To view them, run:
```
k get kafkatopic.platform.confluent.io
```