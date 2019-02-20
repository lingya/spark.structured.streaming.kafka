package streaming

import org.apache.spark.sql.streaming.OutputMode
import org.scalatest.{BeforeAndAfterAll, FunSuite}

import SparkInstance._
import streaming.KeyValue._

class KafkaSparkStructuredStreamingTest extends FunSuite with BeforeAndAfterAll {
  val (kafkaBootstrapServers, urls) = ("kafka.bootstrap.servers", "localhost:9092")
  val sourceTopic = "source-topic"
  val sinkTopic = "sink-topic"
  val query = sparkSession
    .readStream
    .format("kafka")
    .option(kafkaBootstrapServers, urls)
    .option("subscribe", s"$sourceTopic,$sinkTopic")
    .load
    .writeStream
    .outputMode(OutputMode.Append)
    .format("console")
    .start

  override protected def afterAll(): Unit = {
    query.awaitTermination(3000L)
    ()
  }

  test("json > source topic") {
    sparkSession
      .readStream
      .option("basePath", "./data/keyvalue")
      .schema(keyValueStructType)
      .json("./data/keyvalue")
      .selectExpr("CAST(key AS STRING) AS key", "to_json(struct(*)) AS value")
      .writeStream
      .format("kafka")
      .option(kafkaBootstrapServers, urls)
      .option("topic", sourceTopic)
      .option("checkpointLocation", "./target/source-topic")
      .start
      .awaitTermination(3000L)
  }

  test("source topic > sink topic") {
    sparkSession
      .readStream
      .format("kafka")
      .option(kafkaBootstrapServers, urls)
      .option("subscribe", sourceTopic)
      .load
      .selectExpr("CAST(key AS STRING) AS key", "to_json(struct(*)) AS value")
      .writeStream
      .format("kafka")
      .option(kafkaBootstrapServers, urls)
      .option("topic", sinkTopic)
      .option("checkpointLocation", "./target/sink-topic")
      .start
      .awaitTermination(3000L)
  }
}