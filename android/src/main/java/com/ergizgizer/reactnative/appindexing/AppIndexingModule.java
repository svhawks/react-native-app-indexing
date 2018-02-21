package com.ergizgizer.reactnative.appindexing;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;

public class AppIndexingModule extends ReactContextBaseJavaModule {

    public AppIndexingModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "AppIndexingModule";
    }
}
