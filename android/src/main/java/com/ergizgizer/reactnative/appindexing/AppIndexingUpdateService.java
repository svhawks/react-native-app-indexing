package com.ergizgizer.reactnative.appindexing;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.JobIntentService;
import android.util.Log;

import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.appindexing.FirebaseAppIndex;
import com.google.firebase.appindexing.FirebaseAppIndexingInvalidArgumentException;
import com.google.firebase.appindexing.Indexable;
import com.google.firebase.appindexing.builders.Indexables;
import com.google.firebase.appindexing.builders.StickerBuilder;
import com.google.firebase.appindexing.builders.StickerPackBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppIndexingUpdateService extends JobIntentService {

    private static final int UNIQUE_JOB_ID = 42;

    private static final int REMOVE_ALL_STICKERS_ACTION = 0;
    private static final int REMOVE_STICKERS_ACTION = 1;
    private static final int ADD_STICKERS_ACTION = 2;

    private static final String LOG_TAG = AppIndexingUpdateService.class.getSimpleName();
    private static final String DEFAULT_STICKER_URL_KEY = "default-sticker-url";
    private static final String STICKER_INFO_MAP_KEY = "sticker-info-map";
    private static final String STICKER_PACK_ID_KEY = "sticker-pack-id";
    private static final String STICKER_PACK_NAME_KEY = "sticker-pack-name";
    private static final String STICKERS_COUNT_KEY = "stickers-count";
    private static final String ACTION_ID_KEY = "action-id";

    private static final String STICKER_URL_PATTERN = "mystickers://sticker/%s/%d";
    private static final String STICKER_PACK_URL_PATTERN = "mystickers://sticker/pack/%s";
    private static final String STICKER_FILENAME_PATTERN = "sticker_%s_%d.png";

    private static final String INSTALL_STICKERS_SUCCESSFULLY = "Successfully added stickers";
    private static final String FAILED_TO_INSTALL_STICKERS = "Failed to install stickers";

    private static final String CLEAR_STICKERS_SUCCESSFULLY = "Successfully cleared stickers";
    private static final String FAILED_TO_CLEAR_STICKERS = "Failed to clear stickers";

    public static void addStickers(Context context, ReadableMap stickerPack) {
        Intent intent = buildIntentWithStickerData(stickerPack, ADD_STICKERS_ACTION);
        enqueueWork(context, AppIndexingUpdateService.class, UNIQUE_JOB_ID, intent);
    }

    public static void removeStickers(Context context, ReadableMap stickerPack) {
        Intent intent = buildIntentWithStickerData(stickerPack, REMOVE_STICKERS_ACTION);
        enqueueWork(context, AppIndexingUpdateService.class, UNIQUE_JOB_ID, intent);
    }

    public static void removeAllStickers(Context context) {
        Intent intent = buildIntentWithStickerData(null, REMOVE_ALL_STICKERS_ACTION);
        enqueueWork(context, AppIndexingUpdateService.class, UNIQUE_JOB_ID, intent);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        Bundle stickerData = intent.getExtras();
        int action = stickerData.getInt(ACTION_ID_KEY);
        switch (action) {
            case REMOVE_ALL_STICKERS_ACTION:
                clearAll(FirebaseAppIndex.getInstance());
            case REMOVE_STICKERS_ACTION:
                unsetStickers(FirebaseAppIndex.getInstance(), stickerData);
                break;
            case ADD_STICKERS_ACTION:
                setStickers(FirebaseAppIndex.getInstance(), stickerData);
                break;
                default: throw new IllegalStateException("This action is not recognized in this service");
        }

    }

    private static Intent buildIntentWithStickerData(@Nullable ReadableMap stickerPack, int action) {
        Intent intent = new Intent();
        intent.putExtra(ACTION_ID_KEY, action);

        //Return the intent early with only the action key.
        if (action == REMOVE_ALL_STICKERS_ACTION) return intent;

        ReadableArray stickers = stickerPack.getMap("stickers").getArray("edges");
        String packageId = extractPackageIdFromPackage(stickerPack);
        String packageName = extractPackageNameFromPackage(stickerPack);
        int stickerCount = stickers.size();
        intent.putExtra(STICKER_PACK_ID_KEY, packageId);
        intent.putExtra(STICKER_PACK_NAME_KEY, packageName);
        intent.putExtra(STICKERS_COUNT_KEY, stickerCount);

        // Return the intent early with only the action key and packageId.
        if (action == REMOVE_STICKERS_ACTION) return intent;


        // Add other necessary data for add action.
        if (action == ADD_STICKERS_ACTION) {
            String defaultStickerUrl = extractDefaultStickerUrlFromPackage(stickerPack);
            HashMap<String, String[]> stickersInfo = extractStickersInfoFromPackage(stickers);
            intent.putExtra(DEFAULT_STICKER_URL_KEY, defaultStickerUrl);
            intent.putExtra(STICKER_INFO_MAP_KEY, stickersInfo);
        }

        return intent;
    }

    private void clearAll(FirebaseAppIndex firebaseAppIndex) {
        Task<Void> task = firebaseAppIndex.removeAll();

        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.d(LOG_TAG, CLEAR_STICKERS_SUCCESSFULLY);
            }
        });

        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e(LOG_TAG, FAILED_TO_CLEAR_STICKERS, e);
            }
        });
    }

    private void unsetStickers(FirebaseAppIndex firebaseAppIndex, Bundle stickerData) {
        String packageId = stickerData.getString(STICKER_PACK_ID_KEY);
        int stickerCount = stickerData.getInt(STICKERS_COUNT_KEY);
        Task<Void> removePackTask = firebaseAppIndex.remove(String.format(STICKER_PACK_URL_PATTERN, packageId));
        String[] stickerUrls = new String[stickerCount];
        for (int i=0; i<stickerCount; i++) {
            stickerUrls[i] = String.format(STICKER_URL_PATTERN, packageId, i);
        }
        Task<Void> removeStickersTask = firebaseAppIndex.remove(stickerUrls);

        removePackTask.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.d(LOG_TAG, CLEAR_STICKERS_SUCCESSFULLY);
            }
        });

        removePackTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e(LOG_TAG, FAILED_TO_CLEAR_STICKERS, e);
            }
        });

        removeStickersTask.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.d(LOG_TAG, CLEAR_STICKERS_SUCCESSFULLY);
            }
        });

        removeStickersTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e(LOG_TAG, FAILED_TO_CLEAR_STICKERS, e);
            }
        });
    }

    private void setStickers(FirebaseAppIndex firebaseAppIndex, Bundle stickerData) {
        String packageId = stickerData.getString(STICKER_PACK_ID_KEY);
        String packageName = stickerData.getString(STICKER_PACK_NAME_KEY);
        String defaultStickerUrl = stickerData.getString(DEFAULT_STICKER_URL_KEY);
        HashMap<String, String[]> stickersInfo = (HashMap<String, String[]>) stickerData.getSerializable(STICKER_INFO_MAP_KEY);
        try {
            List<StickerBuilder> stickerBuilders = createStickerBuilders(packageId, packageName, stickersInfo);
            List<Indexable> stickers = getIndexableStickers(packageName, stickerBuilders);
            Indexable stickerPack = getIndexableStickerPack(packageId, packageName, defaultStickerUrl, stickerBuilders);

            List<Indexable> indexables = new ArrayList<>(stickers);
            indexables.add(stickerPack);

            Task<Void> task = firebaseAppIndex.update(
                    indexables.toArray(new Indexable[indexables.size()]));

            task.addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Log.d(LOG_TAG, INSTALL_STICKERS_SUCCESSFULLY);
                }
            });

            task.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.e(LOG_TAG, FAILED_TO_INSTALL_STICKERS, e);
                }
            });

        } catch (IOException | FirebaseAppIndexingInvalidArgumentException e) {
            Log.e(LOG_TAG, "Unable to set stickers", e);
        }
    }

    private Indexable getIndexableStickerPack(String packageId, String packageName, String defaultStickerUrl, List<StickerBuilder> stickerBuilders)
            throws IOException, FirebaseAppIndexingInvalidArgumentException {

        StickerPackBuilder stickerPackBuilder = Indexables.stickerPackBuilder()
                .setName(packageName)
                .setUrl(String.format(STICKER_PACK_URL_PATTERN, packageId))
                .setImage(defaultStickerUrl)
                .setHasSticker(stickerBuilders.toArray(new StickerBuilder[stickerBuilders.size()]));
        
         return stickerPackBuilder.build();
    }

    private List<Indexable> getIndexableStickers(String packageName, List<StickerBuilder> stickerBuilders) throws IOException, FirebaseAppIndexingInvalidArgumentException {
        List<Indexable> indexableStickers = new ArrayList<>();

        for (StickerBuilder stickerBuilder : stickerBuilders) {
            stickerBuilder.setIsPartOf(Indexables.stickerPackBuilder()
                    .setName(packageName));
            indexableStickers.add(stickerBuilder.build());
        }

        return indexableStickers;
    }

    private List<StickerBuilder> createStickerBuilders(String packageId, String packageName, HashMap<String, String[]> stickersInfo) throws IOException,
            FirebaseAppIndexingInvalidArgumentException {
        List<StickerBuilder> stickerBuilders = new ArrayList<>();

        int counter = 0;
        for (Map.Entry<String, String[]> entry: stickersInfo.entrySet()) {
            String imageUrl = entry.getKey();
            String[] tags = entry.getValue();
            Log.d(LOG_TAG, Arrays.toString(tags));
            StickerBuilder stickerBuilder = Indexables.stickerBuilder()
                    .setName(String.format(STICKER_FILENAME_PATTERN, packageId, counter))
                    .setUrl(String.format(STICKER_URL_PATTERN, packageId, counter))
                    .setImage(imageUrl)
                    .setIsPartOf(Indexables.stickerPackBuilder()
                            .setName(packageName))
                    .setKeywords(tags);

            stickerBuilders.add(stickerBuilder);
            counter++;
        }

        return stickerBuilders;
    }

    private static HashMap<String, String[]> extractStickersInfoFromPackage(ReadableArray stickers) {
        HashMap<String, String[]> stickerInfo = new HashMap<>(stickers.size());
        for(int i=0; i < stickers.size(); i++){
            ReadableMap stickerNode = stickers.getMap(i).getMap("node");
            String url = extractUrlFromSticker(stickerNode);
            String[] tags = extractTagsFromSticker(stickerNode);
            stickerInfo.put(url, tags);
        }

        return stickerInfo;
    }

    private static String extractPackageNameFromPackage(ReadableMap stickerPack) {
        return stickerPack.getString("name");
    }

    private static String extractPackageIdFromPackage(ReadableMap stickerPack) {
        return stickerPack.getString("id");
    }

    private static String extractDefaultStickerUrlFromPackage(ReadableMap stickerPack) {
        return stickerPack.getMap("defaultSticker").getString("fileUrl");
    }

    private static String extractUrlFromSticker(ReadableMap stickerNode) {
        return stickerNode.getString("fileUrl");
    }

    private static String[] extractTagsFromSticker(ReadableMap stickerNode) {
        ReadableArray tagsArray = stickerNode.getMap("tags").getArray("edges");
        String[] tags = new String[tagsArray.size()];
        for (int i=0; i<tags.length; i++) {
            String tagName = tagsArray.getMap(i).getMap("node").getString("name");
            tags[i] = tagName;
        }
        return tags;
    }
}
