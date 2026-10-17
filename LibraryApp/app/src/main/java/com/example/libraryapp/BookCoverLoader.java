package com.example.libraryapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.widget.ImageView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Small demo-friendly cover loader that uses only Android platform APIs. */
public final class BookCoverLoader {
    private BookCoverLoader() {}

    public static void load(String imageUrl, ImageView target) {
        target.setTag(imageUrl);
        target.setImageResource(android.R.drawable.ic_menu_gallery);
        if (imageUrl == null || imageUrl.trim().isEmpty()
                || !(imageUrl.startsWith("https://") || imageUrl.startsWith("http://"))) return;
        new AsyncTask<Void, Void, Bitmap>() {
            @Override protected Bitmap doInBackground(Void... ignored) {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                    connection.setConnectTimeout(7000);
                    connection.setReadTimeout(7000);
                    connection.setInstanceFollowRedirects(true);
                    connection.connect();
                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
                    try (InputStream input = connection.getInputStream()) {
                        return BitmapFactory.decodeStream(input);
                    }
                } catch (Exception ignoredError) {
                    return null;
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
            @Override protected void onPostExecute(Bitmap bitmap) {
                if (imageUrl.equals(target.getTag()) && bitmap != null) target.setImageBitmap(bitmap);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}