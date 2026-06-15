package com.example;

import android.app.Application;

public class MyApplication extends Application {
    @Override
    public String getAttributionTag() {
        return "ytplayer_attribution";
    }
}
