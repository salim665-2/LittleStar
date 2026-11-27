package com.littlestar.app.feature.culture;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.ContentImage;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.data.content.CultureReadingText;
import com.littlestar.app.databinding.ActivityCultureDetailBinding;
import com.littlestar.app.feature.player.PlayerActivity;

/**
 * Discover screen for cultural content: shows summary, expandable bilingual reading text,
 * custom Toolbar menu actions, and pinning an actionable storytime notification.
 * References consulted:
 * - Android Developers: Add menus with AppBar/Toolbar (https://developer.android.com/develop/ui/views/components/menus)
 * - Android Developers: Notification runtime permission (https://developer.android.com/develop/ui/views/notifications/notification-permission)
 */
public final class CultureDetailActivity extends ChildActivity {

    private static final String EXTRA_ITEM_ID = "itemId";
    private static final String EXTRA_EXPAND_PREVIEW = "expandPreview";
    private static final String EXTRA_FROM_NOTIFICATION = "fromCultureNotif";
    private static final String STATE_PREVIEW = "previewExpanded";

    private ActivityCultureDetailBinding binding;
    private CultureDetailViewModel vm;
    private CultureNotifier cultureNotifier;
    private boolean previewExpanded;
    private ContentImage cover;
    @Nullable
    private AlertDialog tipsDialog;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    postStorytimeNotificationNow();
                } else {
                    Snackbar.make(binding.getRoot(), R.string.culture_pin_off, Snackbar.LENGTH_SHORT).show();
                }
            });

    @NonNull
    public static Intent intent(@NonNull Activity from, @NonNull String itemId) {
        return new Intent(from, CultureDetailActivity.class).putExtra(EXTRA_ITEM_ID, itemId);
    }

    @NonNull
    public static Intent notificationIntent(@NonNull Context from, @NonNull String itemId,
                                            boolean expandPreview) {
        return new Intent(from, CultureDetailActivity.class)
                .putExtra(EXTRA_ITEM_ID, itemId)
                .putExtra(EXTRA_EXPAND_PREVIEW, expandPreview)
                .putExtra(EXTRA_FROM_NOTIFICATION, true);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCultureDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        cover = new ContentImage(binding.detailCover, container().executors);
        cultureNotifier = new CultureNotifier(this);
        Insets.applySystemBarPadding(binding.getRoot());

        setSupportActionBar(binding.browserToolbar.getRoot());
        ActionBar toolbar = getSupportActionBar();
        if (toolbar != null) {
            toolbar.setTitle(R.string.culture_detail_title);
            toolbar.setDisplayHomeAsUpEnabled(true);
            toolbar.setHomeAsUpIndicator(R.drawable.ic_back);
            toolbar.setHomeActionContentDescription(R.string.nav_back);
        }

        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            CultureNotifier.cancel(this);
        }
        previewExpanded = savedInstanceState != null
                ? savedInstanceState.getBoolean(STATE_PREVIEW)
                : getIntent().getBooleanExtra(EXTRA_EXPAND_PREVIEW, false);

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new CultureDetailViewModel(container())))
                .get(CultureDetailViewModel.class);

        binding.btnPreview.setOnClickListener(v -> {
            previewExpanded = !previewExpanded;
            renderPreview();
        });
        binding.btnListen.setOnClickListener(v -> {
            CultureDetailViewModel.UiState s = vm.state().getValue();
            if (s == null || s.item == null || !binding.btnListen.isEnabled()) {
                return;
            }
            binding.btnListen.setEnabled(false);
            startActivity(PlayerActivity.intent(this, s.item.id, s.queue));
            Motion.childEnter(this);
        });
        vm.state().observe(this, this::render);
        container().content.load(container().executors, () -> {
            if (!isFinishing() && !isDestroyed()) {
                vm.init(getIntent().getStringExtra(EXTRA_ITEM_ID));
            }
        });
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            CultureNotifier.cancel(this);
        }
        if (intent.getBooleanExtra(EXTRA_EXPAND_PREVIEW, false)) {
            previewExpanded = true;
            renderPreview();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (vm != null && vm.state().getValue() != null) {
            render(vm.state().getValue());
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        Motion.childExit(this);
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_culture_detail, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        CultureDetailViewModel.UiState s = vm == null ? null : vm.state().getValue();
        MenuItem pinItem = menu.findItem(R.id.action_pin_storytime);
        if (pinItem != null) {
            pinItem.setEnabled(s != null && s.item != null);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_pin_storytime) {
            requestAndPinStorytime();
            return true;
        } else if (item.getItemId() == R.id.action_listening_tips) {
            showListeningTipsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void requestAndPinStorytime() {
        CultureDetailViewModel.UiState s = vm == null ? null : vm.state().getValue();
        if (s == null || s.item == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        postStorytimeNotificationNow();
    }

    private void postStorytimeNotificationNow() {
        CultureDetailViewModel.UiState s = vm == null ? null : vm.state().getValue();
        if (s == null || s.item == null) {
            return;
        }
        if (!cultureNotifier.canPostNotifications()) {
            Snackbar.make(binding.getRoot(), R.string.culture_pin_off, Snackbar.LENGTH_SHORT).show();
            return;
        }
        cultureNotifier.postStorytimeCard(s.item, s.queue);
        Snackbar.make(binding.getRoot(), R.string.culture_pin_sent, Snackbar.LENGTH_SHORT).show();
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

    private void render(CultureDetailViewModel.UiState s) {
        if (s == null) {
            return;
        }
        boolean valid = s.item != null;
        binding.detailContent.setVisibility(valid ? View.VISIBLE : View.GONE);
        binding.detailStatus.setVisibility(valid ? View.GONE : View.VISIBLE);
        binding.detailStatus.setText(R.string.culture_detail_missing);
        binding.btnListen.setEnabled(valid);
        invalidateOptionsMenu();
        if (!valid) {
            cover.bind(null);
            return;
        }
        cover.bind(s.item.cover);
        binding.detailGlyph.setText(s.item.glyph);
        binding.detailTitle.setText(s.item.titleZh);
        binding.detailTitleEn.setText(s.item.titleEn);
        binding.detailSubtitle.setText(getString(R.string.player_subtitle_fmt,
                s.item.subtitleEn, CultureListActivity.formatDuration(s.item.durationSec)));
        binding.detailSummary.setText(s.item.summary);
        binding.detailFocus.setText(s.item.learningFocus);
        binding.detailTranscript.setText(CultureReadingText.format(s.item));
        renderPreview();
    }

    private void renderPreview() {
        int visibility = previewExpanded ? View.VISIBLE : View.GONE;
        binding.detailTranscript.setVisibility(visibility);
        binding.detailFocus.setVisibility(visibility);
        binding.detailFocusHeading.setVisibility(visibility);
        binding.btnPreview.setText(previewExpanded
                ? R.string.culture_detail_hide_text : R.string.culture_detail_read_together);
        binding.btnPreview.setContentDescription(getString(previewExpanded
                ? R.string.culture_detail_hide_text : R.string.culture_detail_read_together));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_PREVIEW, previewExpanded);
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
