package com.example.libraryapp;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import com.journeyapps.barcodescanner.CaptureActivity;

/** Forces ZXing's camera preview into portrait even if the library defaults to landscape. */
public class PortraitCaptureActivity extends CaptureActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        super.onCreate(savedInstanceState);
    }
}