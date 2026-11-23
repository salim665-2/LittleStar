package com.littlestar.app.feature.words;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
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
import com.littlestar.app.databinding.ActivityThemesBinding;
import com.littlestar.app.feature.flashcard.FlashcardActivity;

import java.util.List;

/** Hosts the themes and the selected word list in one window. */
public final class ThemesActivity extends ChildActivity {

    private ActivityThemesBinding binding;
    private final OnBackPressedCallback browserBack = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (!isTwoPane() && getSupportFragmentManager().findFragmentById(R.id.wordsPane) != null) {
                closeWords();
            } else {
                finish();
                Motion.childExit(ThemesActivity.this);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityThemesBinding.inflate(getLayoutInflater());
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
        if (fragments.findFragmentById(R.id.themesPane) == null) {
            fragments.beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.themesPane, ThemesFragment.class, null)
                    .commitNow();
        }
        getOnBackPressedDispatcher().addCallback(this, browserBack);
        updatePanes();
    }

    AppContainer appContainer() {
        return container();
    }

    boolean isTwoPane() {
        return binding.chooseThemeHint != null;
    }

    void showWords(String themeId) {
        FragmentManager fragments = getSupportFragmentManager();
        Fragment current = fragments.findFragmentById(R.id.wordsPane);
        if (fragments.isStateSaved() || (current != null
                && themeId.equals(current.requireArguments().getString(WordListFragment.ARG_THEME_ID)))) {
            return;
        }
        Bundle args = new Bundle();
        args.putString(WordListFragment.ARG_THEME_ID, themeId);
        fragments.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.wordsPane, WordListFragment.class, args)
                .commitNow();
        updatePanes();
    }

    private void closeWords() {
        FragmentManager fragments = getSupportFragmentManager();
        Fragment current = fragments.findFragmentById(R.id.wordsPane);
        if (current != null && !fragments.isStateSaved()) {
            fragments.beginTransaction()
                    .setReorderingAllowed(true)
                    .remove(current)
                    .commitNow();
            updatePanes();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
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

    void openCards(List<String> wordIds, int index) {
        startActivity(FlashcardActivity.browseIntent(this, wordIds, index));
        Motion.childEnter(this);
    }

    @Nullable
    private WordListFragment currentWordList() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.wordsPane);
        return fragment instanceof WordListFragment words ? words : null;
    }

    private void updatePanes() {
        boolean hasWords = currentWordList() != null;
        binding.themesPane.setVisibility(isTwoPane() || !hasWords ? View.VISIBLE : View.GONE);
        binding.wordsPane.setVisibility(hasWords ? View.VISIBLE : View.GONE);
        if (binding.chooseThemeHint != null) {
            binding.chooseThemeHint.setVisibility(hasWords ? View.GONE : View.VISIBLE);
        }
        invalidateOptionsMenu();
    }
}
