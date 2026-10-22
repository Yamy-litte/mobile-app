package com.example.libraryonline.ui.admin;

/** readers has a dedicated Activity and inherits verified Admin-only data operations. */
public final class AdminReadersActivity extends AdminManagementActivity {
    @Override protected String managementSection() { return "readers"; }
}
