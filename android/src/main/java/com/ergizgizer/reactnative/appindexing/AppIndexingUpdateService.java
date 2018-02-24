package com.ergizgizer.reactnative.appindexing;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.util.Log;
import android.widget.Toast;

import com.facebook.react.bridge.ReadableArray;
import com.google.android.gms.tasks.Task;
import com.google.firebase.appindexing.FirebaseAppIndex;
import com.google.firebase.appindexing.FirebaseAppIndexingInvalidArgumentException;
import com.google.firebase.appindexing.Indexable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AppIndexingUpdateService extends JobIntentService {

    private static final int UNIQUE_JOB_ID = 42;

    private static final String LOG_TAG = AppIndexingUpdateService.class.getSimpleName();
    private static final String STICKER_URL_ARRAY_KEY = "Sticker URLs";

    private static final String STICKER_URL_PATTERN = "mystickers://sticker/%s";
    private static final String STICKER_PACK_URL_PATTERN = "mystickers://sticker/pack/%s";
    private static final String CONTENT_PROVIDER_STICKER_PACK_NAME = "Firebase Storage Content Pack";

    public static final String INSTALL_STICKERS_SUCCESSFULLY = "Successfully added stickers";
    public static final String FAILED_TO_INSTALL_STICKERS = "Failed to install stickers";

    public static void enqueueWork(Context context, ReadableArray stickerPack) {
        Intent intent = new Intent();
        String[] urls = extractUrlsFromPackage(stickerPack);
        intent.putExtra(STICKER_URL_ARRAY_KEY, urls);
        enqueueWork(context, AppIndexingUpdateService.class, UNIQUE_JOB_ID, intent);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        String[] stickerUrls = intent.getStringArrayExtra(STICKER_URL_ARRAY_KEY);
        setStickers(getApplicationContext(), FirebaseAppIndex.getInstance(), stickerUrls);
    }

    private void setStickers(
            final Context context,
            FirebaseAppIndex firebaseAppIndex,
            String[] stickerUrls) {
        try {
            List<Indexable> stickers = getIndexableStickers(stickerUrls);
            Indexable stickerPack = getIndexableStickerPack(stickers, stickerUrls[0]);

            List<Indexable> indexables = new ArrayList<>(stickers);
            indexables.add(stickerPack);

            Task<Void> task = firebaseAppIndex.update(
                    indexables.toArray(new Indexable[indexables.size()]));

            task.addOnSuccessListener((voiD) -> {
                        Log.d(LOG_TAG, INSTALL_STICKERS_SUCCESSFULLY);
                        Toast.makeText(context, INSTALL_STICKERS_SUCCESSFULLY, Toast.LENGTH_SHORT)
                                .show();
                    }
            );

            task.addOnFailureListener((Exception e) -> {
                Log.d(LOG_TAG, FAILED_TO_INSTALL_STICKERS, e);
                Toast.makeText(context, FAILED_TO_INSTALL_STICKERS, Toast.LENGTH_SHORT)
                        .show();
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

    private List<Indexable> getIndexableStickers(String[] stickerUrls) throws IOException,
            FirebaseAppIndexingInvalidArgumentException {
        List<Indexable> indexableStickers = new ArrayList<>();

        for (int i = 1; i < stickerUrls.length; i++) {
            Indexable.Builder indexableStickerBuilder = getIndexableBuilder(stickerUrls[i], STICKER_URL_PATTERN, i);
            indexableStickerBuilder.put("keywords", "tag1_" + i, "tag2_" + i)
                    // StickerPack object that the sticker is part of.
                    .put("partOf", new Indexable.Builder("StickerPack")
                            .setName(CONTENT_PROVIDER_STICKER_PACK_NAME)
                            .build());
            indexableStickers.add(indexableStickerBuilder.build());
        }

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

    private static String[] extractUrlsFromPackage(ReadableArray stickers) {
        String[] stickerUrls = new String[stickers.size()];
        for(int i=0; i < stickerUrls.length; i++){
            String url = stickers.getMap(i).getMap("node").getString("fileUrl");
            stickerUrls[i] = url;
        }

        return stickerUrls;
    }
}
