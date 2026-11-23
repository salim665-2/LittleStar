package com.littlestar.app.feature.words;

import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.littlestar.app.AppContainer;
import com.littlestar.app.R;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.databinding.ActivityThemesBinding;

/** Hosts the themes and the selected word list in one window. */
public final class ThemesActivity extends ChildActivity {

    private ActivityThemesBinding binding;
    private final OnBackPressedCallback wordListBack = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            closeWords();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityThemesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        FragmentManager fragments = getSupportFragmentManager();
        if (fragments.findFragmentById(R.id.themesPane) == null) {
            fragments.beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.themesPane, ThemesFragment.class, null)
                    .commitNow();
        }
        getOnBackPressedDispatcher().addCallback(this, wordListBack);
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

    void closeWords() {
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

    void closeBrowser() {
        finish();
        Motion.childExit(this);
    }

    private void updatePanes() {
        boolean hasWords = getSupportFragmentManager().findFragmentById(R.id.wordsPane) != null;
        binding.themesPane.setVisibility(isTwoPane() || !hasWords ? View.VISIBLE : View.GONE);
        binding.wordsPane.setVisibility(hasWords ? View.VISIBLE : View.GONE);
        if (binding.chooseThemeHint != null) {
            binding.chooseThemeHint.setVisibility(hasWords ? View.GONE : View.VISIBLE);
        }
        wordListBack.setEnabled(hasWords && !isTwoPane());
    }
}
