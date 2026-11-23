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
import androidx.lifecycle.Lifecycle;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.littlestar.app.AppContainer;
import com.littlestar.app.R;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.databinding.ActivityThemesBinding;
import com.littlestar.app.feature.flashcard.FlashcardActivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Hosts the themes and the selected word list in one window. */
public final class ThemesActivity extends ChildActivity {

    private static final String SEARCH_DIALOG = "wordSearch";
    private ActivityThemesBinding binding;
    private boolean openingReview;
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
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_find_word) {
            FragmentManager fragments = getSupportFragmentManager();
            if (!fragments.isStateSaved() && fragments.findFragmentByTag(SEARCH_DIALOG) == null) {
                new WordSearchDialogFragment().showNow(fragments, SEARCH_DIALOG);
            }
            return true;
        }
        if (item.getItemId() == R.id.action_review_recorded) {
            openRecordedWords();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openRecordedWords() {
        if (openingReview) {
            return;
        }
        openingReview = true;
        AppContainer app = appContainer();
        app.store.read(state -> {
            app.content.ensureLoaded();
            List<Word> recorded = new ArrayList<>();
            for (Word word : app.content.wordsInOrder()) {
                ProgressState.WordProgress progress = state.words.get(word.id);
                if (progress != null && progress.learnedAt != null) {
                    recorded.add(word);
                }
            }
            recorded.sort(Comparator.comparingLong(
                    (Word word) -> state.words.get(word.id).learnedAt).reversed());
            List<String> ids = new ArrayList<>();
            for (Word word : recorded) {
                ids.add(word.id);
            }
            return ids;
        }, wordIds -> {
            openingReview = false;
            if (isFinishing() || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
                return;
            }
            if (wordIds.isEmpty()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.word_review_recorded)
                        .setMessage(R.string.word_review_empty)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            } else {
                openCards(wordIds, 0);
            }
        });
    }

    void openCards(List<String> wordIds, int index) {
        startActivity(FlashcardActivity.browseIntent(this, wordIds, index));
        Motion.childEnter(this);
    }

    private void updatePanes() {
        boolean hasWords = getSupportFragmentManager().findFragmentById(R.id.wordsPane) != null;
        binding.themesPane.setVisibility(isTwoPane() || !hasWords ? View.VISIBLE : View.GONE);
        binding.wordsPane.setVisibility(hasWords ? View.VISIBLE : View.GONE);
        if (binding.chooseThemeHint != null) {
            binding.chooseThemeHint.setVisibility(hasWords ? View.GONE : View.VISIBLE);
        }
    }
}
