package com.cloudreach.hackathon.panels;

import com.cloudreach.connect.Datasource;
import com.cloudreach.hackathon.Portal;
import com.cloudreach.x2.ui.ApplicationFeature;
import com.cloudreach.x2.ui.Button;
import com.cloudreach.x2.ui.Panel;
import com.cloudreach.x2.ui.components.Chart;
import com.cloudreach.x2.ui.view.*;
import com.cloudreach.x2.ui.view.chart.*;
import com.cloudreach.x2.ui.view.form.TextView;
import org.apache.commons.lang.StringEscapeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;


public class TwitterTagTracker extends Panel {
    public static final Map<String, List<Double>> data = new ConcurrentHashMap<>();

    @Override
    public PanelView onCreate(ApplicationFeature applicationFeature) throws Throwable {
        PanelView pv = new PanelView("Twitter Tag Tacker");
        FormView fv = new FormView();
        AreaView areaView = new AreaView(12);

        fv.addComponent(new TextView("att.hashtag").setTitle("Hashtag"));
//            ButtonView track = new ButtonView();
//            track.setTitle("Track");
//            track.setTarget("javascript:" +
//                            "$('.tack').click();"+
//                            "setTimeout( function(){" +
//                                "$('.tack').click();" +
//                                "}, 20000)" +
//                            "}");
//            fv.addButton(track);
        fv.addButton(new ButtonView("Track", areaView.getId(), TrackButton.class));
//                    .setStyle("display:none;")
//                    .setAdditionalClass("track"));
        pv.addRow().addComponent(fv);
        pv.addRow().addComponent(areaView);
        return pv;
    }

    public static class TrackButton extends Button {
        @Override
        public View onClick(ButtonView buttonView) throws Throwable {
            String hashtag = getFormFieldAsString("att.hashtag");
            Portal.ds.execute(String.format("update console.twitter_config set keywords= '%s' where job_name ='default'", StringEscapeUtils.escapeSql(hashtag)));

            
            ChartView chartView = new ChartView(TwitterChart.class);
            chartView.setType(ChartView.Type.SCATTER);
            chartView.setWidth("100%");
            chartView.setHeight("100%");
            chartView.setMargin("0");


            return chartView;
        }
    }

    public static class TwitterChart extends Chart {

        @Override
        public ChartLegendView onLegend(ChartView chartView) throws Throwable {
            data.clear();
            data.put("positive", new ArrayList<>());
            data.put("negative", new ArrayList<>());
            data.put("neutral", new ArrayList<>());
            data.put("mixed", new ArrayList<>());

            Portal.ds.query(rs -> {
                double positive = rs.getDouble("positive");
                double negative = rs.getDouble("negative");
                double neutral = rs.getDouble("neutral");
                double mixed = rs.getDouble("mixed");

                data.get("positive").add(positive);
                data.get("negative").add(negative);
                data.get("neutral").add(neutral);
                data.get("mixed").add(mixed);

            }, "select round(positive,4) as positive, round(negative,4) as negative, round(neutral,4) as neutral, round(mixed,4) as mixed from console.tweet_sentiment ts inner join console.twitter_config tc on tc.keywords = ts.keywords order by created_at DESC limit 200");
            
            ChartLegendBuilder builder = new ChartLegendBuilder();
            builder.addLabels("Positive", "Negative", "Neutral", "Mixed");
            ChartDataset positive = builder.addDataset("positive", "#Positive");
            positive.addProperty("backgroundColor", "#51c0bf"); //, "Green"
            positive.addProperty("borderWidth", 3);
            positive.addProperty("fill", true);
            positive.addProperty("borderColor", "#51c0bf");

            ChartDataset negative = builder.addDataset("negative", "#Negative");
            negative.addProperty("backgroundColor", "#fc6c85");//, "Red");
            negative.addProperty("borderWidth", 3);
            negative.addProperty("fill", true);
            negative.addProperty("borderColor", "#fc6c85");

            ChartDataset neutral = builder.addDataset("neutral", "#Neutral");
            neutral.addProperty("backgroundColor", "#3da3e8");//, "Blue");
            neutral.addProperty("borderWidth", 3);
            neutral.addProperty("fill", true);
            neutral.addProperty("borderColor", "#3da3e8");

            ChartDataset mixed = builder.addDataset("mixed", "#Mixed");
            mixed.addProperty("backgroundColor", "#996cfb");//, "Purple");
            mixed.addProperty("borderWidth", 3);
            mixed.addProperty("fill", true);
            mixed.addProperty("borderColor", "#996cfb");

            ChartOption xAxes = new ChartOption("xAxes", new ChartOption("display", true),
                    new ChartOption("scaleLabel", new ChartOption("display", true),
                            new ChartOption("labelString", "x-axis-1")),
                    new ChartOption("ticks", new ChartOption("autoSkip", false)));

            ChartOption yAxes = new ChartOption("yAxes",
                    new ChartOption("display", true),
                    new ChartOption("scaleLabel", new ChartOption("display", true),
                            new ChartOption("labelString", "x-axis-1"))
            );

            ChartOption legend = new ChartOption("legend", new ChartOption("position", "top"));
            builder.addOption(legend).addOption(new ChartOption("scales", xAxes, yAxes));
            builder.addOption(new ChartOption("chartArea", new ChartOption("backgroundColor", "rgba(251, 85, 85, 0.4)")));

            return builder.build();
        }

        @Override
        public ChartDataView onData(ChartView chartView, String key) throws Throwable {
            ChartDataView cdv = new ChartDataView(key);
            List<Double> doubles = data.get(key);
            if (doubles == null || doubles.isEmpty()) {
                cdv.addDataPoint(0d, 0d);
            } else
                switch (key) {
                    case "positive":
                        doubles.forEach(d -> cdv.addDataPoint(d, 0.0d));
                        break;
                    case "negative":
                        doubles.forEach(d -> cdv.addDataPoint(0.0d, d));
                        break;
                    case "mixed":
                        doubles.forEach(d -> cdv.addDataPoint(-1.0d * d, 0d));
                        break;
                    case "neutral":
                        doubles.forEach(d -> cdv.addDataPoint(0d, -1.0d * d));
                        break;
                }

            return cdv;
        }
    }

}
