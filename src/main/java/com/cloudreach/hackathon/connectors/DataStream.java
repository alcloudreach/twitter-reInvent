/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cloudreach.hackathon.connectors;

import com.cloudreach.connect.Datasource;
import com.cloudreach.connect.UT;
import com.cloudreach.connect.api.Result;
import com.cloudreach.connect.api.batch.BatchProcessor;
import com.cloudreach.connect.api.context.PluginContext;
import com.cloudreach.connect.api.persistence.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public interface DataStream extends BatchProcessor, Comparable<DataStream> {

    String CONFIG_JOB_NAME = "_SYS_DS_JOB_NAME";
    String CONFIG_KEYWORDS = "_SYS_DS_KEYWORDS";

    String title();

    String description();

    void streamTo(PluginContext context, Map<String, String> config, DataStream destination) throws Throwable;

    void streamFrom(PluginContext context, Map<String, String> config, DataStream source) throws Throwable;

    default String getJobName(Map<String, String> config) {
        return config.get(CONFIG_JOB_NAME);
    }

    default String getKeywords(Map<String, String> config) {
        return config.get(CONFIG_KEYWORDS);
    }

    @Override
    default int getFrequency() {
        return 120;
    }

    @Override
    default UnitOfTime getUnitOfTime() {
        return UnitOfTime.SECONDS;
    }

    @Override
    default Result implement(PluginContext context) throws Exception {
        context.getLogService().info(String.format("thread: %s, starting %s", Thread.currentThread().getName(), getClass().getSimpleName()));
        try {
            Map<String, String> config = new HashMap<>();
            context.getPersistence().runTransaction(dbcon ->  {
                    try (PreparedStatement pstm = dbcon.prepareStatement("select job_name, keywords from console.twitter_config active=true")) {
                        try (ResultSet rs = pstm.executeQuery()) {
                            config.put(CONFIG_JOB_NAME, rs.getString("job_name"));
                            config.put(CONFIG_KEYWORDS, rs.getString("keywords"));
                        }
                    }

                    try (PreparedStatement pstm = dbcon.prepareStatement("select data from console.access_token where key='twitter' and active=true limit 1")) {
                        try (ResultSet rs = pstm.executeQuery()) {
                            String data = rs.getString("data");
                            @SuppressWarnings("unchecked") HashMap<String, String> keys = UT.fromJSON(HashMap.class, data);
                            config.putAll(keys);
                        }
                    }
            });
            streamTo(context, config, null);
        } catch (Throwable ex) {
            context.getLogService().error("Error while calling streaming api: " + ex.getMessage());
        }
        return null;
    }

    @Override
    default int compareTo(DataStream o) {
        return getClass().getName().compareTo(o.getClass().getName());
    }
}
