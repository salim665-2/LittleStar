package com.littlestar.app.feature.words;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.littlestar.app.AppContainer;
import com.littlestar.app.R;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.databinding.ActivityWordListBinding;
import com.littlestar.app.feature.flashcard.FlashcardActivity;

import java.util.List;

/**
 * Standalone host for {@link WordListFragment} when a theme is opened directly by id
 * (for example a test, a shortcut or a future deep link). The adaptive two-pane browsing
 * path stays in {@link ThemesActivity}; this entry keeps the word list reachable on its own.
 * References consulted:
 * - Android Developers: Fragments & FragmentContainerView (https://developer.android.com/guide/fragments)
 * - Android Developers: Add menus with AppBar/Toolbar (https://developer.android.com/develop/ui/views/components/menus)
 */
public final class WordListActivity extends ChildActivity implements WordListFragment.Host {

    private static final String EXTRA_THEME_ID = "themeId";

    @NonNull
    public static Intent intent(@NonNull Context from, @NonNull String themeId) {
        return new Intent(from, WordListActivity.class).putExtra(EXTRA_THEME_ID, themeId);
    }

    private ActivityWordListBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWordListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        setSupportActionBar(binding.browserToolbar.getRoot());
        ActionBar toolbar = getSupportActionBar();
        if (toolbar != null) {
            toolbar.setTitle(R.string.themes_top);
            toolbar.setDisplayHomeAsUpEnabled(true);
            toolbar.setHomeAsUpIndicator(R.drawable.ic_back);
            toolbar.setHomeActionContentDescription(R.string.nav_back);
        }

        FragmentManager fragments = getSupportFragmentManager();
        if (fragments.findFragmentById(R.id.wordsPane) == null) {
            Bundle args = new Bundle();
            args.putString(WordListFragment.ARG_THEME_ID, getIntent().getStringExtra(EXTRA_THEME_ID));
            fragments.beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.wordsPane, WordListFragment.class, args)
                    .commitNow();
        }
    }

    @NonNull
    @Override
    public AppContainer appContainer() {
        return container();
    }

    @Override
    public void openCards(@NonNull List<String> wordIds, int index) {
        startActivity(FlashcardActivity.browseIntent(this, wordIds, index));
        Motion.childEnter(this);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        Motion.childExit(this);
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_word_browser, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        WordListFragment words = currentWordList();
        boolean ready = words != null && words.hasWords();
        menu.setGroupVisible(R.id.wordBrowserActions, ready);
        if (ready) {
            menu.findItem(R.id.action_continue_browsing).setTitle(words.hasBrowsingPosition()
                    ? R.string.wl_continue_browsing : R.string.wl_start_browsing);
            menu.findItem(R.id.action_play_theme).setEnabled(!words.isPlayingTheme());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        WordListFragment words = currentWordList();
        if (words != null && words.hasWords()) {
            if (item.getItemId() == R.id.action_continue_browsing) {
                words.continueBrowsing();
                return true;
            }
            if (item.getItemId() == R.id.action_play_theme) {
                words.playTheme();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Nullable
    private WordListFragment currentWordList() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.wordsPane);
        return fragment instanceof WordListFragment words ? words : null;
    }
}
