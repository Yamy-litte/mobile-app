package com.example.libraryonline.ui.admin;

/** config has a dedicated Activity and inherits verified Admin-only data operations. */
public final class AdminConfigActivity extends AdminManagementActivity {
    @Override protected String managementSection() { return "config"; }
}
