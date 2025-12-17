package com.prosysopc.ua.samples.agent;

import org.eclipse.paho.client.mqttv3.*;
import org.json.JSONObject;

import java.util.Random;

public class MqttOpcUaAgent {

    public static void main(String[] args) {
        try {
            String mqttBroker = System.getenv().getOrDefault("MQTT_BROKER", "tcp://mqtt_broker:1883");
            String topic = "cnc/machine1/data";

            MqttClient client = new MqttClient(mqttBroker, MqttClient.generateClientId());
            client.connect();
            System.out.println("Connected to MQTT Broker: " + mqttBroker);

            Random random = new Random();

            while (true) {
                JSONObject data = new JSONObject();
                data.put("MachineStatus", random.nextBoolean() ? "Running" : "Stopped");
                data.put("ActualSpindleSpeed", 1000 + random.nextInt(500));
                data.put("CoolantTemperature", 20 + random.nextDouble() * 5);
                data.put("ToolLifeRemaining", random.nextDouble() * 100);
                data.put("ProductionOrderProgress", random.nextDouble() * 100);

                JSONObject msg = new JSONObject();
                msg.put("machine", "CNC-01");
                msg.put("timestamp", System.currentTimeMillis());
                msg.put("data", data);

                MqttMessage mqttMessage = new MqttMessage(msg.toString().getBytes());
                mqttMessage.setQos(1);
                client.publish(topic, mqttMessage);

                System.out.println("Sent MQTT message: " + msg);
                Thread.sleep(2000);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
