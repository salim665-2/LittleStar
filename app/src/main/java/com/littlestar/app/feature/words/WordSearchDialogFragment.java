package com.littlestar.app.feature.words;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.littlestar.app.AppContainer;
import com.littlestar.app.R;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.databinding.DialogWordSearchBinding;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Looks up a flashcard by Hanzi, pinyin, or English meaning across all themes. */
public final class WordSearchDialogFragment extends DialogFragment {

    private static final Pattern TONE_MARKS = Pattern.compile("\\p{M}+");
    private static final String STATE_QUERY = "query";

    private DialogWordSearchBinding binding;
    private List<Word> words;
    private final List<Word> matches = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        DialogWordSearchBinding search = DialogWordSearchBinding.inflate(LayoutInflater.from(builder.getContext()));
        binding = search;
        ThemesActivity host = (ThemesActivity) requireActivity();
        adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1);
        search.wordSearchResults.setAdapter(adapter);
        search.wordSearchResults.setOnItemClickListener((parent, row, position, id) -> {
            search.wordSearchResults.setEnabled(false);
            host.openCards(List.of(matches.get(position).id), 0);
        });
        search.wordQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateMatches(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        if (savedInstanceState != null) {
            search.wordQuery.setText(savedInstanceState.getString(STATE_QUERY, ""));
        }
        AppContainer app = host.appContainer();
        app.content.load(app.executors, () -> {
            if (binding == search) {
                words = app.content.wordsInOrder();
                updateMatches(query());
            }
        });
        search.wordQuery.requestFocus();
        Dialog dialog = builder
                .setTitle(R.string.word_search_title)
                .setView(search.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        return dialog;
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.wordSearchResults.setEnabled(true);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null) {
            outState.putString(STATE_QUERY, query());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        adapter = null;
        words = null;
        matches.clear();
    }

    private String query() {
        Editable text = binding.wordQuery.getText();
        return text == null ? "" : text.toString();
    }

    private void updateMatches(String input) {
        if (words == null) {
            return;
        }
        String query = input.strip().toLowerCase(Locale.ROOT);
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
