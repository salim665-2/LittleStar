package com.littlestar.app.feature.words;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.littlestar.app.AppContainer;
import com.littlestar.app.R;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.databinding.ActivityWordSearchBinding;
import com.littlestar.app.feature.flashcard.FlashcardActivity;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Looks up a flashcard by Hanzi, pinyin, or English meaning across all themes. */
public final class WordSearchActivity extends ChildActivity {

    private static final Pattern TONE_MARKS = Pattern.compile("\\p{M}+");

    private ActivityWordSearchBinding binding;
    private List<Word> words;
    private final List<Word> matches = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWordSearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.searchContent, (view, insets) -> {
            int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int bottom = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).bottom;
            view.setPadding(0, 0, 0, Math.max(0, keyboard - bottom));
            return insets;
        });
        binding.searchToolbar.getRoot().setTitle(R.string.word_search_title);
        binding.searchToolbar.getRoot().setNavigationIcon(R.drawable.ic_back);
        binding.searchToolbar.getRoot().setNavigationContentDescription(R.string.nav_back);
        binding.searchToolbar.getRoot().setNavigationOnClickListener(view -> {
            finish();
            Motion.childExit(this);
        });

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        binding.wordSearchResults.setAdapter(adapter);
        binding.wordSearchResults.setOnItemClickListener((parent, row, position, id) -> {
            binding.wordSearchResults.setEnabled(false);
            startActivity(FlashcardActivity.browseIntent(this, List.of(matches.get(position).id), 0));
            Motion.childEnter(this);
        });
        binding.wordQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateMatches(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        AppContainer app = container();
        app.content.load(app.executors, () -> {
            if (!isFinishing() && !isDestroyed()) {
                words = app.content.wordsInOrder();
                updateMatches(query());
            }
        });
        binding.wordQuery.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.wordSearchResults.setEnabled(true);
    }

    private String query() {
        Editable text = binding.wordQuery.getText();
        return text == null ? "" : text.toString();
    }

    private void updateMatches(String input) {
        if (words == null) {
            return;
        }
        String query = input.replace('\u3000', ' ').trim().toLowerCase(Locale.ROOT);
        String pinyinQuery = pinyinKey(query);
        matches.clear();
        List<String> labels = new ArrayList<>();
        if (!query.isEmpty()) {
            for (Word word : words) {
                if (word.hanzi.contains(query) || word.english.toLowerCase(Locale.ROOT).contains(query)
                        || (!pinyinQuery.isEmpty() && pinyinKey(word.pinyin).contains(pinyinQuery))) {
                    matches.add(word);
                    labels.add(getString(R.string.word_search_result, word.hanzi, word.pinyin, word.english));
                }
            }
        }
        adapter.setNotifyOnChange(false);
        adapter.clear();
        adapter.addAll(labels);
        adapter.notifyDataSetChanged();
        binding.wordSearchResults.setSelection(0);
        binding.wordSearchStatus.setText(query.isEmpty() ? R.string.word_search_prompt : R.string.word_search_empty);
        binding.wordSearchStatus.setVisibility(matches.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static String pinyinKey(String value) {
        String letters = value.toLowerCase(Locale.ROOT).replace("u:", "u").replace('v', 'u');
        return TONE_MARKS.matcher(Normalizer.normalize(letters, Normalizer.Form.NFD)).replaceAll("");
    }
}
