package com.littlestar.app.feature.culture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

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
import com.littlestar.app.databinding.ActivityCultureListBinding;
import com.littlestar.app.feature.player.PlayerActivity;

import java.util.List;
import java.util.Locale;

/**
 * Standalone host Activity for {@link CultureListFragment} when launched directly with a categoryId.
 * References consulted:
 * - Android Developers: Fragments & FragmentContainerView (https://developer.android.com/guide/fragments)
 */
public final class CultureListActivity extends ChildActivity implements CultureListFragment.Host {

    private static final String EXTRA_CATEGORY_ID = "categoryId";

    @NonNull
    public static Intent intent(@NonNull Activity from, @NonNull String categoryId) {
        Intent i = new Intent(from, CultureListActivity.class);
        i.putExtra(EXTRA_CATEGORY_ID, categoryId);
        return i;
    }

    private ActivityCultureListBinding binding;
    @Nullable
    private AlertDialog tipsDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCultureListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        setSupportActionBar(binding.browserToolbar.getRoot());
        ActionBar toolbar = getSupportActionBar();
        if (toolbar != null) {
            toolbar.setTitle(R.string.cul_top);
            toolbar.setDisplayHomeAsUpEnabled(true);
            toolbar.setHomeAsUpIndicator(R.drawable.ic_back);
            toolbar.setHomeActionContentDescription(R.string.nav_back);
        }

        FragmentManager fragments = getSupportFragmentManager();
        if (fragments.findFragmentById(R.id.cultureListPane) == null) {
            Bundle args = new Bundle();
            args.putString(CultureListFragment.ARG_CATEGORY_ID,
                    getIntent().getStringExtra(EXTRA_CATEGORY_ID));
            fragments.beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.cultureListPane, CultureListFragment.class, args)
                    .commitNow();
        }
    }

    @NonNull
    @Override
    public AppContainer appContainer() {
        return container();
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
        finish();
        Motion.childExit(this);
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
            if (tipsDialog != null && tipsDialog.isShowing()) {
                tipsDialog.dismiss();
            }
            tipsDialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.culture_tips_title)
                    .setMessage(R.string.culture_tips_body)
                    .setPositiveButton(R.string.culture_tips_ok, null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Nullable
    private CultureListFragment currentCultureList() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.cultureListPane);
        return fragment instanceof CultureListFragment list ? list : null;
    }

    @Override
    protected void onDestroy() {
        if (tipsDialog != null) {
            tipsDialog.dismiss();
            tipsDialog = null;
        }
        super.onDestroy();
    }

    /** Formats seconds as m:ss for list rows and detail headers. */
    @NonNull
    public static String formatDuration(int seconds) {
        return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }
}
