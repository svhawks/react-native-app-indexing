package com.ergizgizer.reactnative.appindexing;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
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

    private static final String LOG_TAG = AppIndexingUpdateService.class.getSimpleName();
    private static final String DEFAULT_STICKER_URL_KEY = "default-sticker-url";
    private static final String STICKER_INFO_MAP_KEY = "sticker-info-map";
    private static final String STICKER_PACK_ID_KEY = "sticker-pack-id";

    private static final String STICKER_URL_PATTERN = "mystickers://sticker/%s";
    private static final String STICKER_PACK_URL_PATTERN = "mystickers://sticker/pack/%s";
    private static final String STICKER_PACK_NAME = "Firebase Storage Content Pack";
    private static final String STICKER_FILENAME_PATTERN = "sticker%s.png";

    private static final String INSTALL_STICKERS_SUCCESSFULLY = "Successfully added stickers";
    private static final String FAILED_TO_INSTALL_STICKERS = "Failed to install stickers";

    private static final String CLEAR_STICKERS_SUCCESSFULLY = "Successfulyy cleared stickers";
    private static final String FAILED_TO_CLEAR_STICKERS = "Failed to clear stickers";

    public static void enqueueWork(Context context, ReadableMap stickerPack) {
        Log.d(LOG_TAG, "enqueuing work started");
        Intent intent = buildIntentWithStickerData(stickerPack);
        enqueueWork(context, AppIndexingUpdateService.class, UNIQUE_JOB_ID, intent);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        Log.d(LOG_TAG, "handling work started");
        Bundle stickerData = intent.getExtras();
        setStickers(FirebaseAppIndex.getInstance(), stickerData);
    }

    private static Intent buildIntentWithStickerData(ReadableMap stickerPack) {
        Intent intent = new Intent();
        String packageId = extractPackageIdFromPackage(stickerPack);
        String defaultStickerUrl = extractDefaultStickerUrlFromPackage(stickerPack);
        ReadableArray stickers = stickerPack.getMap("stickers").getArray("edges");
        HashMap<String, String[]> stickersInfo = extractStickersInfoFromPackage(stickers);
        intent.putExtra(STICKER_PACK_ID_KEY, packageId);
        intent.putExtra(DEFAULT_STICKER_URL_KEY, defaultStickerUrl);
        intent.putExtra(STICKER_INFO_MAP_KEY, stickersInfo);

        return intent;
    }

    private void clearStickers(FirebaseAppIndex firebaseAppIndex) {
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
                Log.d(LOG_TAG, FAILED_TO_CLEAR_STICKERS, e);
            }
        });
    }

    private void setStickers(FirebaseAppIndex firebaseAppIndex, Bundle stickerData) {
        Log.d(LOG_TAG, "sticker indexing started");
        String packageId = stickerData.getString(STICKER_PACK_ID_KEY);
        String defaultStickerUrl = stickerData.getString(DEFAULT_STICKER_URL_KEY);
        HashMap<String, String[]> stickersInfo = (HashMap<String, String[]>) stickerData.getSerializable(STICKER_INFO_MAP_KEY);
        Log.d(LOG_TAG, packageId);
        Log.d(LOG_TAG, defaultStickerUrl);
        try {
            List<StickerBuilder> stickerBuilders = createStickerBuilders(stickersInfo);
            List<Indexable> stickers = getIndexableStickers(stickerBuilders);
            Indexable stickerPack = getIndexableStickerPack(defaultStickerUrl, stickerBuilders);

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
                    Log.d(LOG_TAG, FAILED_TO_INSTALL_STICKERS);
                }
            });

        } catch (IOException | FirebaseAppIndexingInvalidArgumentException e) {
            Log.e(LOG_TAG, "Unable to set stickers", e);
        }
    }

    private Indexable getIndexableStickerPack(String defaultStickerUrl, List<StickerBuilder> stickerBuilders)
            throws IOException, FirebaseAppIndexingInvalidArgumentException {

        StickerPackBuilder stickerPackBuilder = Indexables.stickerPackBuilder()
                .setName(STICKER_PACK_NAME)
                .setUrl(String.format(STICKER_PACK_URL_PATTERN, stickerBuilders.size()))
                .setImage(defaultStickerUrl)
                .setHasSticker(stickerBuilders.toArray(new StickerBuilder[stickerBuilders.size()]));
        
         return stickerPackBuilder.build();
    }

    private List<Indexable> getIndexableStickers(List<StickerBuilder> stickerBuilders) throws IOException, FirebaseAppIndexingInvalidArgumentException {
        List<Indexable> indexableStickers = new ArrayList<>();

        for (StickerBuilder stickerBuilder : stickerBuilders) {
            stickerBuilder.setIsPartOf(Indexables.stickerPackBuilder()
                    .setName(STICKER_PACK_NAME));
            indexableStickers.add(stickerBuilder.build());
        }

        return indexableStickers;
    }

    private List<StickerBuilder> createStickerBuilders(HashMap<String, String[]> stickersInfo) throws IOException,
            FirebaseAppIndexingInvalidArgumentException {
        List<StickerBuilder> stickerBuilders = new ArrayList<>();

        int counter = 0;
        for (Map.Entry<String, String[]> entry: stickersInfo.entrySet()) {
            String imageUrl = entry.getKey();
            String[] tags = entry.getValue();
            Log.d(LOG_TAG, Arrays.toString(tags));
            StickerBuilder stickerBuilder = Indexables.stickerBuilder()
                    .setName(String.format(STICKER_FILENAME_PATTERN, counter))
                    .setUrl(String.format(STICKER_URL_PATTERN, counter))
                    .setImage(imageUrl)
                    .setIsPartOf(Indexables.stickerPackBuilder()
                            .setName(STICKER_PACK_NAME))
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

    private static String extractPackageIdFromPackage(ReadableMap stickerPackage) {
        return stickerPackage.getString("id");
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
