package com.littlestar.app.feature.culture;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.littlestar.app.AppContainer;
import com.littlestar.app.R;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.databinding.ActivityCultureCategoriesBinding;
import com.littlestar.app.feature.player.PlayerActivity;

import java.util.List;

/**
 * Hosts cultural categories and the selected category list in one adaptive window using Fragments.
 * Phone screens switch dynamically between panes; w600dp+ tablets render a side-by-side two-pane UI.
 * References consulted:
 * - Android Developers: FragmentManager transactions (https://developer.android.com/guide/fragments/transactions)
 * - Android Developers: Support different screen sizes (https://developer.android.com/guide/topics/large-screens/support-different-screen-sizes)
 * - Android Developers: Add menus with AppBar/Toolbar (https://developer.android.com/develop/ui/views/components/menus)
 */
public final class CultureCategoriesActivity extends ChildActivity implements CultureListFragment.Host {

    private ActivityCultureCategoriesBinding binding;
    @Nullable
    private AlertDialog tipsDialog;

    private final OnBackPressedCallback browserBack = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (!isTwoPane() && getSupportFragmentManager().findFragmentById(R.id.cultureListPane) != null) {
                closeCategoryList();
            } else {
                finish();
                Motion.childExit(CultureCategoriesActivity.this);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCultureCategoriesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        setSupportActionBar(binding.browserToolbar.getRoot());
        ActionBar toolbar = getSupportActionBar();
        if (toolbar != null) {
            toolbar.setTitle(R.string.home_module_culture_title);
            toolbar.setDisplayHomeAsUpEnabled(true);
            toolbar.setHomeAsUpIndicator(R.drawable.ic_back);
            toolbar.setHomeActionContentDescription(R.string.nav_back);
        }

        FragmentManager fragments = getSupportFragmentManager();
        if (fragments.findFragmentById(R.id.categoriesPane) == null) {
            fragments.beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.categoriesPane, CultureCategoriesFragment.class, null)
                    .commitNow();
        }
        getOnBackPressedDispatcher().addCallback(this, browserBack);
        updatePanes();
    }

    @NonNull
    @Override
    public AppContainer appContainer() {
        return container();
    }

    public boolean isTwoPane() {
        return binding.chooseCategoryHint != null;
    }

    public void showCategory(@NonNull String categoryId) {
        FragmentManager fragments = getSupportFragmentManager();
        Fragment current = fragments.findFragmentById(R.id.cultureListPane);
        if (fragments.isStateSaved() || (current != null && current.getArguments() != null
                && categoryId.equals(current.getArguments().getString(CultureListFragment.ARG_CATEGORY_ID)))) {
            return;
        }
        Bundle args = new Bundle();
        args.putString(CultureListFragment.ARG_CATEGORY_ID, categoryId);
        fragments.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.cultureListPane, CultureListFragment.class, args)
                .commitNow();
        updatePanes();
    }

    private void closeCategoryList() {
        FragmentManager fragments = getSupportFragmentManager();
        Fragment current = fragments.findFragmentById(R.id.cultureListPane);
        if (current != null && !fragments.isStateSaved()) {
            fragments.beginTransaction()
                    .setReorderingAllowed(true)
                    .remove(current)
                    .commitNow();
            updatePanes();
        }
    }

    @Override
    public void openCultureDetail(@NonNull String itemId) {
        startActivity(CultureDetailActivity.intent(this, itemId));
        Motion.childEnter(this);
    }

    @Override
    public void openCulturePlayer(@NonNull String itemId, @NonNull List<String> queue) {
        startActivity(PlayerActivity.intent(this, itemId, queue));
        Motion.childEnter(this);
    }

    @Override
    public void onCultureListReady() {
        invalidateOptionsMenu();
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_culture_browser, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        CultureListFragment list = currentCultureList();
        menu.setGroupVisible(R.id.cultureCategoryActions, list != null && list.hasItems());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_play_category) {
            CultureListFragment list = currentCultureList();
            if (list != null && list.hasItems()) {
                list.playCategory();
                return true;
            }
        } else if (item.getItemId() == R.id.action_listening_tips) {
            showListeningTipsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showListeningTipsDialog() {
        if (tipsDialog != null && tipsDialog.isShowing()) {
            tipsDialog.dismiss();
        }
        tipsDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.culture_tips_title)
                .setMessage(R.string.culture_tips_body)
                .setPositiveButton(R.string.culture_tips_ok, null)
                .show();
    }

    @Nullable
    private CultureListFragment currentCultureList() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.cultureListPane);
        return fragment instanceof CultureListFragment list ? list : null;
    }

    private void updatePanes() {
        boolean hasList = currentCultureList() != null;
        binding.categoriesPane.setVisibility(isTwoPane() || !hasList ? View.VISIBLE : View.GONE);
        binding.cultureListPane.setVisibility(hasList ? View.VISIBLE : View.GONE);
        if (binding.chooseCategoryHint != null) {
            binding.chooseCategoryHint.setVisibility(hasList ? View.GONE : View.VISIBLE);
        }
        invalidateOptionsMenu();
    }

    @Override
    protected void onDestroy() {
        if (tipsDialog != null) {
            tipsDialog.dismiss();
            tipsDialog = null;
        }
        super.onDestroy();
    }
}
