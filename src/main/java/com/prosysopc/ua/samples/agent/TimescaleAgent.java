package com.prosysopc.ua.samples.agent;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.json.JSONObject;

import java.sql.*;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;


public class TimescaleAgent {

    public static void main(String[] args) {
        String url = "jdbc:postgresql://timescaledb:5432/mydb";
        String user = "admin";
        String password = "admin123";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected to TimescaleDB");

            Properties props = new Properties();
            props.put("bootstrap.servers", "redpanda_broker:9092");
            props.put("group.id", "timescale-agent-group");
            props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            props.put("auto.offset.reset", "latest");
            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Collections.singletonList("machine_data"));
            System.out.println("Subscribed to Kafka topic: machine_data");

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        JSONObject json = new JSONObject(record.value());
                        String machine = json.optString("machine", "unknown");
                        long timestamp = json.optLong("timestamp", System.currentTimeMillis());
                        JSONObject data = json.getJSONObject("data");
                        JSONObject context = json.getJSONObject("context");

                        String status = data.optString("MachineStatus", "unknown");
                        double spindleSpeed = data.optDouble("ActualSpindleSpeed", 0.0);
                        double coolantTemp = data.optDouble("CoolantTemperature", 0.0);
                        double toolLife = data.optDouble("ToolLifeRemaining", 0.0);
                        double progress = data.optDouble("ProductionOrderProgress", 0.0);
                        String plant = context.optString("plant", "unknown");
                        String line = context.optString("line", "unknown");
                        String operator = context.optString("operator", "unknown");
                        String shift = context.optString("shift", "unknown");

                        String sql = """
                            INSERT INTO cnc_machine
                            (time, machine, status, spindle_speed, coolant_temp, tool_life, progress, plant, line, operator, shift)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """;
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setTimestamp(1, new Timestamp(timestamp));
                            ps.setString(2, machine);
                            ps.setString(3, status);
                            ps.setDouble(4, spindleSpeed);
                            ps.setDouble(5, coolantTemp);
                            ps.setDouble(6, toolLife);
                            ps.setDouble(7, progress);
                            ps.setString(8, plant);
                            ps.setString(9, line);
                            ps.setString(10, operator);
                            ps.setString(11, shift);
                            ps.executeUpdate();
                            System.out.println("Inserted data for machine " + machine + " (" + status + ")");
                        }

                    } catch (Exception e) {
                        System.out.println("Error processing record: " + e.getMessage());
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
