package com.cloudreach.hackathon.message.handlers;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.comprehend.AmazonComprehend;
import com.amazonaws.services.comprehend.AmazonComprehendClientBuilder;
import com.amazonaws.services.comprehend.model.DetectSentimentRequest;
import com.amazonaws.services.comprehend.model.DetectSentimentResult;
import com.amazonaws.services.comprehend.model.SentimentScore;
import com.amazonaws.services.translate.AmazonTranslate;
import com.amazonaws.services.translate.AmazonTranslateClientBuilder;
import com.amazonaws.services.translate.model.TranslateTextRequest;
import com.amazonaws.util.StringUtils;
import com.cloudreach.connect.Datasource;
import com.cloudreach.connect.UT;
import com.cloudreach.connect.x2.api.Module;
import com.cloudreach.connect.x2.internal.model.CCMessage;
import com.cloudreach.hackathon.connectors.DataStream;
import org.apache.commons.lang.StringEscapeUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessageProcessor implements Module {
    private Logger logger;
    private String name;
    private int pluginId;
    private double version;

    private final AmazonComprehend comprehendClient;
    private final AmazonTranslate translate;
    private static final List<String> AWS_TRANSLATE_SUPPORTED_LANGS = Arrays.asList("ar", "zh", "fr", "de", "pt", "es", "en");
    private static final List<String> AWS_COMPREHEND_SUPPORTED_LANGS = Arrays.asList("en", "es");
    public  static final String TABLE_TWEET_SENTIMENT = "tweet_sentiment";
    private Datasource ds;
    public MessageProcessor() {
        AWSCredentialsProvider awsCreds = DefaultAWSCredentialsProviderChain.getInstance();
        comprehendClient = AmazonComprehendClientBuilder.standard()
                .withCredentials(awsCreds)
                .withRegion(Regions.EU_WEST_1)
                .build();

        translate = AmazonTranslateClientBuilder.standard()
                .withCredentials(awsCreds)
                .withRegion(Regions.EU_WEST_1)
                .build();

    }


    private boolean isSupportedTranslateLanguage(String langCode) {
        return AWS_TRANSLATE_SUPPORTED_LANGS.contains(langCode);
    }

    private boolean isSupportedComprehendLanguage(String langCode) {
        return AWS_COMPREHEND_SUPPORTED_LANGS.contains(langCode);
    }

    @Override
    public void construct(Logger logger, String name, int id, double version) {
        this.logger = logger;
        this.name = name;
        this.pluginId = id;
        this.version = version;
    }

    @Override
    public void configure(Map<String, String> configuration) {
        ds = new Datasource(
                configuration.get("dbdriver"),
                configuration.get("dburl"),
                configuration.get("dbuser"),
                configuration.get("dbpassword")
        );

        initTable();
    }

    private void initTable() {
        try {
            if (!ds.tableExists("console", TABLE_TWEET_SENTIMENT)) {

                ds.execute("create table IF NOT EXISTS console." + TABLE_TWEET_SENTIMENT + "("
                        + "id serial not null primary key,"
                        + "keywords varchar(250) not null,"
                        + "tweet text not null,"
                        + "sentiment varchar(50) not null,"
                        + "positive numeric(10,10) not null,"
                        + "negative numeric(10,10) not null,"
                        + "neutral numeric(10,10) not null,"
                        + "mixed numeric(10,10) not null,"
                        + "created_at timestamp not null,"
                        + "inserted timestamp without time zone NOT NULL DEFAULT (current_timestamp AT TIME ZONE 'UTC')"
                        + ")");

                logger.info(String.format("table %s is created? %s", TABLE_TWEET_SENTIMENT, ds.tableExists("console", TABLE_TWEET_SENTIMENT)));
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    @Override
    public void start() {
        logger.info(">>>\tHackathon message processor started");
    }

    @Override
    public void stop() {
        logger.info(">>>\tHackathon message processor stopped");
    }

    @Override
    public CCMessage process(CCMessage message) throws Throwable {
        if (message.getPluginId() != pluginId) {
            return null;
        }
        String sts = message.getAttributes().get("status").toString();
        String keywords = message.getAttributes().get(DataStream.CONFIG_KEYWORDS).toString();
        TweetDto t = UT.fromJSON(TweetDto.class, sts);
        processMessage(t, keywords);
        return null;
    }

    private String getDataForPG(Date date) {
        date.getTime();
        SimpleDateFormat ff  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ff.setTimeZone(TimeZone.getTimeZone("UTC"));
        return ff.format(date);
    }

    private void processMessage(TweetDto tweet, String keywords) {
        final String sourceLang = tweet.getLang();
        if (tweet.isRetweet() || StringUtils.isNullOrEmpty(sourceLang)) return;
        try {
            if (!isSupportedTranslateLanguage(sourceLang)) {
                return;
            }

            //TODO: is translate return original text if fails.
            String text;
            String targetLang;
            if (!isSupportedComprehendLanguage(sourceLang)){
                text = translate.translateText(new TranslateTextRequest()
                        .withText(tweet.getText())
                        .withSourceLanguageCode(sourceLang)
                        .withTargetLanguageCode("en"))
                        .getTranslatedText();
                targetLang = "en";
            } else {
                text = tweet.getText();
                targetLang = sourceLang;
            }

            DetectSentimentResult sentiment = comprehendClient.detectSentiment(new DetectSentimentRequest()
                    .withText(text)
                    .withLanguageCode(targetLang));

            persist(tweet, sentiment, keywords);

            //logger.info(String.format("%s: sentiment[%s], persisted", tweet.getText(), sentiment.toString()));

        } catch (Throwable e) {
            logger.log(Level.WARNING, e.getMessage());
        }
    }

    private void persist(TweetDto tweet, DetectSentimentResult sentiment, String keywords) throws Throwable {
        SentimentScore score = sentiment.getSentimentScore();
        String q = String.format("insert into console.%s (\"keywords\",\"tweet\", \"sentiment\", \"positive\", \"negative\", \"neutral\", \"mixed\", \"created_at\") values" +
                        "('%s','%s', '%s', %f, %f, %f, %f, '%s')",
                TABLE_TWEET_SENTIMENT, StringEscapeUtils.escapeSql(keywords), StringEscapeUtils.escapeSql(tweet.getText()), sentiment.getSentiment(), score.getPositive(), score.getNegative(), score.getNeutral(), score.getMixed(), getDataForPG(tweet.getCreatedAt()));
        //logger.info(String.format("===\tto insert : [%s]", q));
        ds.execute(q);
    }
}
