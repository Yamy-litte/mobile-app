package com.example.libraryonline.data;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.Request;
import com.bumptech.glide.request.target.DrawableImageViewTarget;
import com.example.libraryonline.ui.reader.BookCoverLoader;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class BookCoverLoaderTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private ImageView image;
    private TextView fallback;

    @Before public void setUp() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        FrameLayout root = new FrameLayout(activity);
        image = new ImageView(activity);
        fallback = new TextView(activity);
        root.addView(image, new FrameLayout.LayoutParams(76, 104));
        root.addView(fallback, new FrameLayout.LayoutParams(76, 104));
        activity.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(140, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 100, 140);
    }
    @After public void tearDown() {
        Glide.with(activity).clear(image);
        controller.pause().stop().destroy();
        Glide.tearDown();
    }
    private File createImageFile() throws Exception {
        File file = new File(activity.getCacheDir(), "book-cover-test.png");
        Bitmap bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        return file;
    }
    private String imageFile() throws Exception {
        // Glide's StringLoader handles absolute file paths directly; this avoids
        // Robolectric/Windows URI normalization differences for local files.
        return createImageFile().getAbsolutePath();
    }
    private void awaitRequest() throws Exception {
        Request request = new DrawableImageViewTarget(image).getRequest();
        assertNotNull(request);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
        while (!request.isComplete() && System.nanoTime() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }
    @Test public void validImageIsDisplayedAndPlaceholderIsHidden() throws Exception {
        BookCoverLoader.load(imageFile(), image, fallback);
        awaitRequest();
        assertNotNull(image.getDrawable());
        assertEquals(View.GONE, fallback.getVisibility());
    }
    @Test public void missingImageKeepsPlaceholderVisible() throws Exception {
        BookCoverLoader.load(Uri.fromFile(new File(activity.getCacheDir(), "missing-cover.png")).toString(), image, fallback);
        awaitRequest();
        assertEquals(View.VISIBLE, fallback.getVisibility());
        assertNull(image.getDrawable());
    }
    @Test public void rebindingAnEmptyUrlClearsThePreviousBookCover() throws Exception {
        BookCoverLoader.load(imageFile(), image, fallback);
        awaitRequest();
        BookCoverLoader.load("   ", image, fallback);
        assertNull(image.getDrawable());
        assertEquals(View.VISIBLE, fallback.getVisibility());
    }
    @Test public void httpCoverIsDecodedAndRebindingReusesTheCache() throws Exception {
        File sourceFile = createImageFile();
        byte[] png = java.nio.file.Files.readAllBytes(sourceFile.toPath());
        java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();
        java.net.ServerSocket server = new java.net.ServerSocket();
        server.bind(new java.net.InetSocketAddress("127.0.0.1", 0));
        server.setSoTimeout(5000);
        java.util.concurrent.ExecutorService responder = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<?> response = responder.submit(() -> {
            try (java.net.Socket socket = server.accept();
                 java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
                 java.io.OutputStream out = socket.getOutputStream()) {
                socket.setSoTimeout(5000);
                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) { /* request headers */ }
                requests.incrementAndGet();
                String headers = "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: "
                        + png.length + "\r\nConnection: close\r\n\r\n";
                out.write(headers.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                out.write(png);
                out.flush();
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        try {
            String address = "http://127.0.0.1:" + server.getLocalPort() + "/cover.png";
            BookCoverLoader.load(address, image, fallback);
            awaitRequest();
            assertNotNull(image.getDrawable());
            assertEquals(View.GONE, fallback.getVisibility());
            response.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(1, requests.get());
            BookCoverLoader.load(address, image, fallback);
            awaitRequest();
            assertNotNull(image.getDrawable());
            assertEquals(1, requests.get());
        } finally {
            server.close();
            responder.shutdownNow();
        }
    }

}
