/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cloudreach.hackathon.connectors;

import com.cloudreach.connect.UT;
import com.cloudreach.connect.api.context.PluginContext;
import com.cloudreach.connect.twitter.TwitterIntegration;
import com.cloudreach.connect.twitter.TwitterStreamListener;
import com.cloudreach.connect.x2.internal.model.CCMessage;
import com.cloudreach.hackathon.message.handlers.MessageProcessor;
import com.cloudreach.hackathon.message.handlers.TweetDto;
import twitter4j.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 *
 */
public class TwitterConnector implements DataStream {

    private final Map<String, TwitterIntegration> runningConfigurations = new ConcurrentHashMap<>();
    private ScheduledExecutorService EXEC = Executors.newScheduledThreadPool(1);

    public TwitterConnector() {
    }

    private TwitterIntegration getTwitter(PluginContext context, Map<String, String> config) throws Throwable {
        String configKey = UT.md5(UT.toJSON(config));
        TwitterIntegration twitter = runningConfigurations.get(configKey);
        if (twitter == null) {
            twitter = context.getIntegration(TwitterIntegration.class)
                    .authenticate(config.get("consumer_key"), config.get("consumer_secret"), config.get("token"), config.get("secret"))
                    .context(context);
            runningConfigurations.put(configKey, twitter);
        }
        return twitter;
    }

    @Override
    public int getFrequency() {
        return 120;
    }

    @Override
    public UnitOfTime getUnitOfTime() {
        return UnitOfTime.SECONDS;
    }

    @Override
    public void streamTo(PluginContext context, Map<String, String> config, DataStream destination) throws Throwable {
        String configKey = UT.md5(UT.toJSON(config));
        if (!runningConfigurations.containsKey(configKey) && config.containsKey(CONFIG_KEYWORDS)) {
            final String keywords = config.get(CONFIG_KEYWORDS);
            context.getLogService().info("streaming for keywords: " + keywords);
            TwitterIntegration t = getTwitter(context, config);
            t.listenFor(new TwitterStreamListener(context) {
                @Override
                public void onStatus(Status status) {
                    dispatchTweet(status, keywords);
                }
            }, keywords);

            try {
                EXEC.schedule(() -> {
                t.shutdown();
                runningConfigurations.remove(configKey);
                context.getLogService().info("Stream terminated");
            }, getFrequency() - 10, TimeUnit.SECONDS)
                    .get(getFrequency()- 5, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                t.shutdown();
                runningConfigurations.remove(configKey);
                context.getLogService().info("Forced Stream to terminate");
            }
            context.getLogService().info(Thread.currentThread().getName() + " >>...");
        }
        else if (!config.containsKey(CONFIG_KEYWORDS)) {
            context.getLogService().info("No configuration found to run twitter integration.");
        }
        else if (runningConfigurations.containsKey(configKey)) {
            context.getLogService().info("Already running!!");
        }
    }

    private void dispatchTweet(Status status, final String keywords) {
        CCMessage request = new CCMessage();
        request.setPluginId(311);
        request.setModuleClass(MessageProcessor.class.getName());
        Map<String, Object> msgAtt = new HashMap<>();
        msgAtt.put("status", UT.toJSON(toTweetDto(status)));
        msgAtt.put(CONFIG_KEYWORDS, keywords);
        request.setAttributes(msgAtt);
        request.send(CCMessage.LOCATION_ANYWHERE);
    }

    private TweetDto toTweetDto(Status status) {
        TweetDto t = new TweetDto();
        t.setId(status.getId());
        t.setLang(status.getLang());
        t.setRetweet(status.isRetweet());
        t.setText(status.getText());
        t.setCreatedAt(status.getCreatedAt());
        return t;
    }

    @Override
    public void streamFrom(PluginContext context, Map<String, String> config, DataStream source) throws Throwable {
    }

    @Override
    public String title() {
        return "Twitter Sentiment Analysis";
    }

    @Override
    public String description() {
        return "This module will ingest Twitter Streaming API and translate/analyse sentiments.";
    }

}
