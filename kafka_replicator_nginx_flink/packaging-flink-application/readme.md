# Packaging a flink Application

## Step 1 : Package your Flink application
Refer to the pom.xml file in this directory as a reference. It is configured for developing Flink applications using the Table API and SQL, and supports local testing within IntelliJ IDEA.

## Step 2 : Build and Push the Docker image
Use the provided Dockerfile as reference to containerize your application.
- Update the cp-flink image as required
- Update the jar name as required

```bash
docker build . -t irtebat/cp-flink-app-sandbox:DatagenToKafkaTransformationJob --platform=linux/amd64
```

Push the image to your container registry. For Ex:
```bash
docker push irtebat/cp-flink-app-sandbox:DatagenToKafkaTransformationJob
```

## Step 3 : Deploy your application
Update your FlinkApplication Custom Resource Definition to reference the pushed image.
Ensure the spec.image field in the CRD points to the image tag used above.

