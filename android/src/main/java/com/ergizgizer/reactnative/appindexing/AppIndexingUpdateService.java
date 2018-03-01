package com.ergizgizer.reactnative.appindexing;

import android.content.Context;
import android.content.Intent;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AppIndexingUpdateService extends JobIntentService {

    private static final int UNIQUE_JOB_ID = 42;

    private static final String LOG_TAG = AppIndexingUpdateService.class.getSimpleName();
    private static final String DEFAULT_STICKER_URL_KEY = "default-sticker-url";
    private static final String STICKER_INFO_MAP_KEY = "sticker-info-map";

    private static final String STICKER_URL_PATTERN = "mystickers://sticker/%s";
    private static final String STICKER_PACK_URL_PATTERN = "mystickers://sticker/pack/%s";
    private static final String CONTENT_PROVIDER_STICKER_PACK_NAME = "Firebase Storage Content Pack";

    public static final String INSTALL_STICKERS_SUCCESSFULLY = "Successfully added stickers";
    public static final String FAILED_TO_INSTALL_STICKERS = "Failed to install stickers";

    public static void enqueueWork(Context context, ReadableMap stickerPack) {
        Log.d(LOG_TAG, "enqueuing work started");
        Intent intent = buildStickerIndexingIntentWithArgs(stickerPack);

        enqueueWork(context, AppIndexingUpdateService.class, UNIQUE_JOB_ID, intent);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        Log.d(LOG_TAG, "handling work started");
        HashMap<String, String[]> stickersInfo = (HashMap<String, String[]>) intent.getSerializableExtra(STICKER_INFO_MAP_KEY);
        String defaultStickerUrl = intent.getStringExtra(DEFAULT_STICKER_URL_KEY);
        setStickers(FirebaseAppIndex.getInstance(), defaultStickerUrl, stickersInfo);
    }

    private static Intent buildStickerIndexingIntentWithArgs(ReadableMap stickerPack) {
        Intent intent = new Intent();
        String defaultStickerUrl = extractDefaultStickerUrlFromPackage(stickerPack);
        ReadableArray stickers = stickerPack.getMap("stickers").getArray("edges");
        LinkedHashMap<String, String[]> stickersInfo = extractStickersInfoFromPackage(stickers);
        intent.putExtra(DEFAULT_STICKER_URL_KEY, defaultStickerUrl);
        intent.putExtra(STICKER_INFO_MAP_KEY, stickersInfo);
        return intent;
    }

    private void setStickers(FirebaseAppIndex firebaseAppIndex,
                             String defaultStickerUrl,
                             HashMap<String, String[]> stickersInfo) {
        Log.d(LOG_TAG, "sticker indexing started");
        try {
            List<Indexable> stickers = getIndexableStickers(stickersInfo);
            Indexable stickerPack = getIndexableStickerPack(stickers, defaultStickerUrl);

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

    private Indexable getIndexableStickerPack(List<Indexable> stickers, String defaultStickerUrl)
            throws IOException, FirebaseAppIndexingInvalidArgumentException {
        Indexable.Builder indexableBuilder = getIndexableBuilder(
                defaultStickerUrl, STICKER_PACK_URL_PATTERN, stickers.size());
        indexableBuilder.put("hasSticker", stickers.toArray(new Indexable[stickers.size()]));
        return indexableBuilder.build();
    }

    private List<Indexable> getIndexableStickers(HashMap<String, String[]> stickersInfo) throws IOException,
            FirebaseAppIndexingInvalidArgumentException {
        List<Indexable> indexableStickers = new ArrayList<>();

        int counter = 1;
        for (Map.Entry<String, String[]> entry: stickersInfo.entrySet()) {
            String url = entry.getKey();
            String[] tags = entry.getValue();
            Indexable.Builder indexableStickerBuilder = getIndexableBuilder(url, STICKER_URL_PATTERN, counter);
            indexableStickerBuilder.put("keywords", tags)
                    .put("partOf", new Indexable.Builder("StickerPack")
                                .setName(CONTENT_PROVIDER_STICKER_PACK_NAME)
                                .build());
            indexableStickers.add(indexableStickerBuilder.build());
        }

//        for (int i = 1; i < stickerUrls.length; i++) {
//            Indexable.Builder indexableStickerBuilder = getIndexableBuilder(stickerUrls[i], STICKER_URL_PATTERN, i);
//            indexableStickerBuilder.put("keywords", "tag1_" + i, "tag2_" + i)
//                    // StickerPack object that the sticker is part of.
//                    .put("partOf", new Indexable.Builder("StickerPack")
//                            .setName(CONTENT_PROVIDER_STICKER_PACK_NAME)
//                            .build());
//            indexableStickers.add(indexableStickerBuilder.build());
//        }

        return indexableStickers;
    }

    private Indexable.Builder getIndexableBuilder(String stickerURL, String urlPattern, int index)
            throws IOException {
        String url = String.format(urlPattern, index);

        Indexable.Builder indexableBuilder = new Indexable.Builder("StickerPack")
                // name of the sticker pack
                .setName(CONTENT_PROVIDER_STICKER_PACK_NAME)
                // Firebase App Indexing unique key that must match an intent-filter
                // (e.g. mystickers://stickers/pack/0)
                .setUrl(url)
                // (Optional) - Defaults to the first sticker in "hasSticker"
                // displayed as a category image to select between sticker packs that should
                // be representative of the sticker pack
                //.setImage(contentUri.toString())
                .setImage(stickerURL)
                // (Optional) - Defaults to a generic phrase
                // content description of the image that is used for accessibility
                // (e.g. TalkBack)
                .setDescription("Indexable description");

        return indexableBuilder;
    }

    private static LinkedHashMap<String, String[]> extractStickersInfoFromPackage(ReadableArray stickers) {
        LinkedHashMap<String, String[]> stickerInfo = new LinkedHashMap<>(stickers.size());
        for(int i=0; i < stickers.size(); i++){
            ReadableMap stickerNode = stickers.getMap(i).getMap("node");
            String url = extractUrlFromSticker(stickerNode);
            String[] tags = extractTagsFromSticker(stickerNode);
            stickerInfo.put(url, tags);
        }

        return stickerInfo;
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
