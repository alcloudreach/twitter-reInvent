package com.cloudreach.hackathon.message.handlers;

import com.amazonaws.services.iot.client.AWSIotDevice;
import com.amazonaws.services.iot.client.AWSIotException;
import com.amazonaws.services.iot.client.AWSIotMqttClient;
import com.cloudreach.connect.Datasource;
import com.cloudreach.connect.UT;
import com.cloudreach.connect.api.Result;
import com.cloudreach.connect.api.batch.BatchProcessor;
import com.cloudreach.connect.api.context.PluginContext;
import com.cloudreach.connect.api.persistence.PersistenceException;
import com.cloudreach.connect.api.persistence.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class IotJob implements BatchProcessor {
    private ScheduledExecutorService EXEC = Executors.newScheduledThreadPool(1);
    private static final String CLIENT_ID = UUID.randomUUID().toString();

    private AWSIotDevice device;

    public IotJob() {
    }

    @Override
    public int getFrequency() {
        return 180;
    }

    @Override
    public UnitOfTime getUnitOfTime() {
        return UnitOfTime.SECONDS;
    }

    @Override
    public Result implement(PluginContext context) throws Exception {
        initialiseIot(context);
        
        ScheduledFuture<?> scheduledFuture = EXEC.scheduleWithFixedDelay(() -> process(context),
                0, 30,
                TimeUnit.SECONDS);
        try {
            scheduledFuture.get(getFrequency() -20, TimeUnit.SECONDS);
        } catch (Throwable e) {

        }
        context.getLogService().info("job finished, will resume shortly");
        return null;
    }

    /**
     *         map.put("consumer_key", "");
     *         map.put("consumer_secret", "");
     *         map.put("oit_endpoint", "");
     *         map.put("oit_thing_name", "");
     */

    private synchronized void initialiseIot(PluginContext context) throws PersistenceException {
        if (device == null) {
            return;
        }

        Map<String, String> config = new HashMap<>();
        context.getPersistence().runTransaction(dbcon -> {

                try (PreparedStatement pstm = dbcon.prepareStatement("select data from console.access_token where key='aws_iot_twitter' and active=true limit 1")) {
                    try (ResultSet rs = pstm.executeQuery()) {
                        String data = rs.getString("data");
                        @SuppressWarnings("unchecked") HashMap<String, String> keys = UT.fromJSON(HashMap.class, data);
                        config.putAll(keys);
                    }
                }
        });

        AWSIotMqttClient client = new AWSIotMqttClient(config.get("oit_endpoint"),
                CLIENT_ID,  config.get("consumer_key"), config.get("consumer_secret"));

        device = new AWSIotDevice(config.get("oit_thing_name"));

        try {
            client.attach(device);
            client.connect();
        } catch (AWSIotException e) {
            context.getLogService().error(e.getMessage());
        }
    }

    /**
     * TODO: Refactor
     * @param context
     */
    private void process(PluginContext context) {
        try {
            context.getLogService().info("oit service called");

            Map<Double, String> map = new HashMap<>();
            final String query = "select positive, negative, neutral, mixed from console.tweet_sentiment_today ts " +
                            " inner join console.twitter_config tc on tc.keywords = ts.keywords" +
                            " where job_name = 'default'";

            context.getPersistence().runTransaction( dbcon ->  {
                    try (PreparedStatement pstm = dbcon.prepareStatement(query)) {
                        try (ResultSet rs = pstm.executeQuery()) {
                            double positive = rs.getDouble("positive");
                            double negative = rs.getDouble("negative");
                            double neutral = rs.getDouble("neutral");
                            double mix = rs.getDouble("mixed");

                            map.put(positive, "positive");
                            map.put(negative, "negative");
                            map.put(neutral, "neutral");
                            map.put(mix, "mixed");
                        }
                    }
            });

            if (map.isEmpty()){
                context.getLogService().error("no data for query: "+query);
                return;
            }
            String sentiment = map.entrySet()
                    .stream()
                    .sorted((a, b) -> Double.compare(b.getKey(), a.getKey()))
                    .findFirst()
                    .get().getValue().toLowerCase();

            Map<String, Map<String, Map<String, String>>> state = new HashMap<>();
            Map<String, Map<String, String>> data = new HashMap<>();
            Map<String, String> color = new HashMap<>();

            data.put("desired", color);
            state.put("state", data);

            switch (sentiment) {
                case "positive":
                    color.put("color", "GREEN");
                    break;
                case "negative":
                    color.put("color", "RED");
                    break;
                case "neutral":
                    color.put("color", "BLUE");
                    break;
                case "mixed":
                    color.put("color", "PURPLE");
                    break;

                default:
                    context.getLogService().error("unknown sentiment : " + sentiment);
                    return;  //nop
            }
            context.getLogService().info("message to send: " + UT.toJSON(state));
            device.update(UT.toJSON(state));
            context.getLogService().info("message sent.");
            device.get(3_000L);
        } catch (Throwable t) {
            context.getLogService().error(t.getMessage());
        }
    }
}
