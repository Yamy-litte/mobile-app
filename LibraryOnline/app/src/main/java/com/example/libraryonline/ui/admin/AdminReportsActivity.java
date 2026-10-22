package com.example.libraryonline.ui.admin;

/** reports has a dedicated Activity and inherits verified Admin-only data operations. */
public final class AdminReportsActivity extends AdminManagementActivity {
    @Override protected String managementSection() { return "reports"; }
}
