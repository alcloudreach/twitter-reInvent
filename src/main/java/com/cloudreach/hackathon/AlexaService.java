package com.cloudreach.hackathon;

import com.cloudreach.connect.Datasource;
import com.cloudreach.connect.UT;
import com.cloudreach.connect.x2.api.Module;
import org.apache.commons.lang.StringEscapeUtils;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AlexaService implements Module {
    private Datasource ds;
    private Logger logger;

    @Override
    public void construct(Logger logger, String s, int i, double v) {
        this.logger = logger;
    }

    @Override
    public void process(HttpServletRequest request, HttpServletResponse response) throws Throwable {
        String hashtag = request.getParameter("hashtag");
        Map<String, String> res = new HashMap<>();
        res.put("message", "Sorry Cannot find analysis for your hashtag");
        response.setContentType("application/json");
        try {
            String sentiment = getSentiment(ds, hashtag);
            res.put("message", sentiment);


        } catch (Throwable e) {
            logger.log(Level.SEVERE, e.getMessage());
        }
        ServletOutputStream outputStream = response.getOutputStream();
        outputStream.write(UT.toJSON(res).getBytes("utf-8"));
        outputStream.flush();
    }
    private static String getSentiment(Datasource ds, String hashtag) throws Throwable {
        Map<Double, String> map = new HashMap<>();
        ds.query((rs) -> {
                    double positive = rs.getDouble("positive");
                    double negative = rs.getDouble("negative");
                    double neutral = rs.getDouble("neutral");
                    double mix = rs.getDouble("mixed");

                    map.put(positive, "positive");
                    map.put(negative, "negative");
                    map.put(neutral, "neutral");
                    map.put(mix, "Mixed");
                },
                String.format("select positive, negative, neutral, mixed from console.tweet_sentiment_today where keywords='%s'",
                        StringEscapeUtils.escapeSql("#"+hashtag)));

        return map.entrySet()
                .stream()
                .sorted((a, b) -> Double.compare(b.getKey(), a.getKey()))
                .findFirst()
                .get().getValue();
    }

    @Override
    public String getBasePath() {
        return "/alexa";
    }

    @Override
    public void configure(Map<String, String> configuration) {
        ds = new Datasource(configuration.get("dbdriver"),
                configuration.get("dburl"),
                configuration.get("dbuser"),
                configuration.get("dbpassword"));
    }
}
