package com.cloudreach.hackathon.panels;

import com.cloudreach.x2.ui.ApplicationFeature;
import com.cloudreach.x2.ui.Panel;
import com.cloudreach.x2.ui.view.PanelView;

public class WelcomePanel extends Panel {
    @Override
    public PanelView onCreate(ApplicationFeature applicationFeature) throws Throwable {
        PanelView pv = new PanelView("Welcome to Twitter Streaming App");

        return pv;
    }
}
