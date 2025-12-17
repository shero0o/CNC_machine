package com.prosysopc.ua.samples.agent;

import org.eclipse.paho.client.mqttv3.*;
import org.apache.kafka.clients.producer.*;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;

import java.util.Properties;

public class HydrationAgent {

    public static void main(String[] args) {
        try {
            String mqttBroker = System.getenv().getOrDefault("MQTT_BROKER", "tcp://mqtt_broker:1883");
            String kafkaBroker = System.getenv().getOrDefault("KAFKA_BROKER", "redpanda_broker:9092");
            String redisHost = System.getenv().getOrDefault("REDIS_HOST", "redis_container");
            int redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

            String topicIn = "cnc/machine1/data";
            String topicOut = "machine_data";

            // MQTT Subscriber
            MqttClient mqttClient = new MqttClient(mqttBroker, MqttClient.generateClientId());
            mqttClient.connect();
            System.out.println("HydrationAgent connected to MQTT: " + mqttBroker);

            // Kafka Producer
            Properties kafkaProps = new Properties();
            kafkaProps.put("bootstrap.servers", kafkaBroker);
            kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            Producer<String, String> producer = new KafkaProducer<>(kafkaProps);

            // Redis Connection
            Jedis jedis = new Jedis(redisHost, redisPort);
            System.out.println("Connected to Redis at " + redisHost);

            mqttClient.subscribe(topicIn, (topic, message) -> {
                try {
                    JSONObject json = new JSONObject(new String(message.getPayload()));
                    String contextStr = jedis.get("cnc:machine:context");
                    JSONObject context = (contextStr != null) ? new JSONObject(contextStr) : new JSONObject();

                    json.put("context", context);

                    producer.send(new ProducerRecord<>(topicOut, json.toString()));
                    System.out.println("📤 Enriched + sent to Kafka: " + json);

                } catch (Exception e) {
                    System.err.println("⚠️ Error processing MQTT message: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
