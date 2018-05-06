package com.cloudreach.hackathon;

import com.cloudreach.hackathon.panels.TwitterTagTracker;
import com.cloudreach.x2.security.GoogleAuthenticationHandler;
import com.cloudreach.x2.ui.*;

import com.cloudreach.connect.Datasource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Hello world!
 */
public class Portal extends Application {

    public static Datasource ds ;
    private Logger logger;
    private String name;
    private int pluginId;
    private double version;

    private static final String TITLE = "Twitter/reInvent";

    private String getGoogleApplicationKey_() {
        return "AIzaSyBANgdr1j08XI3xPFkm-aNbplXMRVdidSQ";
    }

    private String getGoogleClientId_() {
        return "831831253579-bhbtd69gfs1f3jj9hdfr3mum1v25hb4f.apps.googleusercontent.com";
    }

    private String getGoogleSecret_() {
        return "Mn4FKt8OtIaSAy2dpHFqqyM4";
    }

    @Override
    public void construct(Logger logger, String name, int id, double version) {
        super.construct(logger, name, id, version);

        this.logger = logger;
        this.name = name;
        this.pluginId = id;
        this.version = version;
    }

    @Override
    public void configure(Map<String, String> configuration) {
        super.configure(configuration);
        setTitle(TITLE);

        configureDatasource(Collections.unmodifiableMap(configuration));

        addAuthenticationHandler(new GoogleAuthenticationHandler(
                getGoogleApplicationKey_(),
                getGoogleSecret_(),
                getGoogleClientId_()));
    }

    private void configureDatasource(Map<String,String> configuration) {
        ds = new Datasource(configuration.get("dbdriver"),
                configuration.get("dburl"),
                configuration.get("dbuser"),
                configuration.get("dbpassword"));

    }

    public Logger getLogger() {
        return logger;
    }

    public String getName() {
        return name;
    }

    public int getPluginId() {
        return pluginId;
    }

    @Override
    public double getVersion() {
        return version;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public List<ApplicationFeature> onListFeatures() throws Throwable {
        ApplicationFeature feature = new ApplicationFeature(ICON.history, "Twitter Sentiment Analysis", TwitterTagTracker.class);
        return Arrays.asList(feature);
    }

    @Override
    public String getBasePath() {
        return "/portal";
    }
}
