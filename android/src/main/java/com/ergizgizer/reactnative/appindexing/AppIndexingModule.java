package com.ergizgizer.reactnative.appindexing;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

public class AppIndexingModule extends ReactContextBaseJavaModule {

    public AppIndexingModule(ReactApplicationContext reactContext) { super(reactContext); }

    @Override
    public String getName() {
        return "AppIndexingModule";
    }

    @Nullable
    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        return constants;
    }

    @ReactMethod
    public void syncStickers(JSONObject firebaseOptions, JSONArray stickerPack){
        FirebaseOptions options = new FirebaseOptions.Builder()
                .setApiKey(FIRBEASE_API_KEY)
                .build();
        FirebaseApp.initializeApp(getReactApplicationContext(), options);

        AppIndexingUpdateService.enqueueWork(getReactApplicationContext(), stickerPack);
    }
}
