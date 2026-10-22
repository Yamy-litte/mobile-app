package com.example.libraryonline.ui.reader;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.HttpException;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import java.io.File;

/** Cached image requests bound to the target view and its Activity lifecycle. */
public final class BookCoverLoader {
    private static final String TAG = "BookCoverLoader";

    private BookCoverLoader() {}

    public static void load(String address, ImageView image, TextView fallback) {
        Glide.with(image).clear(image);
        image.setImageDrawable(null);
        fallback.setVisibility(View.VISIBLE);
        if (address == null || address.trim().isEmpty()) return;

        String source = address.trim();
        Uri uri = Uri.parse(source);
        Object model = "file".equalsIgnoreCase(uri.getScheme())
                ? new File(uri.getPath())
                : source;

        Glide.with(image)
                .load(model)
                .timeout(20_000)
                .centerCrop()
                .listener(
                        new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(
                                    GlideException error,
                                    Object model,
                                    Target<Drawable> target,
                                    boolean firstResource) {
                                fallback.setVisibility(View.VISIBLE);
                                // Log the failure type/status, never signed URL query parameters.
                                Throwable cause =
                                        error != null && !error.getRootCauses().isEmpty()
                                                ? error.getRootCauses().get(0)
                                                : error;
                                String reason =
                                        cause == null
                                                ? "unknown"
                                                : cause.getClass().getSimpleName();
                                if (cause instanceof HttpException) {
                                    reason += " HTTP " + ((HttpException) cause).getStatusCode();
                                }
                                Log.w(TAG, "Không tải được ảnh bìa: " + reason);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(
                                    Drawable resource,
                                    Object model,
                                    Target<Drawable> target,
                                    DataSource source,
                                    boolean firstResource) {
                                fallback.setVisibility(View.GONE);
                                return false;
                            }
                        })
                .into(image);
    }
}
