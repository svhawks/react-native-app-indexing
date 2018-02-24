package com.ergizgizer.reactnative.appindexing;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;

public class AppIndexingModule extends ReactContextBaseJavaModule {

    public AppIndexingModule(ReactApplicationContext reactContext) { super(reactContext); }

    @Override
    public String getName() {
        return "AppIndexingModule";
    }

    @ReactMethod
    public void syncStickers(ReadableArray stickerPack){
        AppIndexingUpdateService.enqueueWork(getReactApplicationContext(), stickerPack);
    }
}
