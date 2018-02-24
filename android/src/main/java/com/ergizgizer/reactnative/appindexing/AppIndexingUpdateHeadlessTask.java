package com.ergizgizer.reactnative.appindexing;

import android.content.Intent;


import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;

import javax.annotation.Nullable;


public class AppIndexingUpdateHeadlessTask extends HeadlessJsTaskService {

    private static final String TASK_ID = "APP_INDEXING";

    @Nullable
    @Override
    protected HeadlessJsTaskConfig getTaskConfig(Intent intent) {
        return new HeadlessJsTaskConfig(TASK_ID, null, getDefaultTimeout(), true);
    }

    private Integer getDefaultTimeout() {
        return getResources().getInteger(R.integer.default_timeout);
    }
}
