package com.littlestar.app.feature.words;

import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.littlestar.app.AppContainer;
import com.littlestar.app.R;
import com.littlestar.app.core.audio.SpeechPlayer;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.databinding.FragmentWordListBinding;
import com.littlestar.app.databinding.ItemWordTileBinding;
import com.littlestar.app.feature.quiz.QuizActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 词表（§7.3 / §9.3）：6 字网格 + Quiz(THEME) 入口。数据驱动：返回时绿勾自动刷新。
 * References consulted:
 * - Android Developers: Fragment lifecycle & view state (https://developer.android.com/guide/fragments/lifecycle)
 * - Android Developers: Get a result from an activity (https://developer.android.com/training/basics/intents/result)
 * - Android Developers: Handle touch and long-click input (https://developer.android.com/develop/ui/views/touch-and-input/input-events)
 */
public final class WordListFragment extends Fragment {

    /** Host contract so the list can run inside the two-pane browser or a standalone entry. */
    interface Host {
        @NonNull
        AppContainer appContainer();

        void openCards(@NonNull List<String> wordIds, int index);
    }

    static final String ARG_THEME_ID = "themeId";
    private boolean launchingQuiz;
    private boolean playingTheme;
    private FragmentWordListBinding binding;
    private WordListViewModel vm;
    private final ActivityResultLauncher<Intent> quizLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> resetQuizLaunch());

    private final SpeechPlayer.SequenceListener themeAudio = new SpeechPlayer.SequenceListener() {
        @Override
        public void onWordChanged(@NonNull String wordId) {
            if (binding == null) {
                return;
            }
            Word word = host().appContainer().content.wordById(wordId);
            if (word != null) {
                binding.wordListHint.setText(getString(R.string.wl_now_playing, word.hanzi));
            }
            if (!playingTheme) {
                playingTheme = true;
                binding.stopThemeAudio.setVisibility(View.VISIBLE);
                requireActivity().invalidateOptionsMenu();
            }
        }

        @Override
        public void onFinished(boolean failed) {
            playingTheme = false;
            if (binding == null) {
                return;
            }
            binding.wordListHint.setText(R.string.wl_hint);
            binding.stopThemeAudio.setVisibility(View.GONE);
            requireActivity().invalidateOptionsMenu();
            if (failed) {
                Snackbar.make(binding.getRoot(), R.string.wl_audio_unavailable, Snackbar.LENGTH_SHORT).show();
            }
        }
    };

    private final SpeechPlayer.SequenceListener previewAudio = new SpeechPlayer.SequenceListener() {
        @Override
        public void onWordChanged(@NonNull String wordId) {
            if (binding == null) {
                return;
            }
            Word word = host().appContainer().content.wordById(wordId);
            if (word != null) {
                binding.wordListHint.setText(getString(R.string.wl_now_playing, word.hanzi));
            }
        }

        @Override
        public void onFinished(boolean failed) {
            if (binding == null) {
                return;
            }
            binding.wordListHint.setText(R.string.wl_hint);
            if (failed) {
                Snackbar.make(binding.getRoot(), R.string.wl_audio_unavailable, Snackbar.LENGTH_SHORT).show();
            }
        }
    };

    public WordListFragment() {
        super(R.layout.fragment_word_list);
    }

    @NonNull
    private Host host() {
        return (Host) requireActivity();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FragmentWordListBinding binding = FragmentWordListBinding.bind(view);
        this.binding = binding;
        Host host = host();
        AppContainer container = host.appContainer();
        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new WordListViewModel(container)))
                .get(WordListViewModel.class);

        String themeId = requireArguments().getString(ARG_THEME_ID);
        WordAdapter adapter = new WordAdapter();
        adapter.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        GridLayoutManager grid = new GridLayoutManager(requireContext(), 1);
        binding.wordGrid.setLayoutManager(grid);
        binding.wordGrid.addItemDecoration(new ThemesFragment.GapDecoration(
                getResources().getDimensionPixelSize(R.dimen.ls_gap)));
        binding.wordGrid.setAdapter(adapter);
        if (getResources().getConfiguration().fontScale < 1.3f) {
            int minWidth = getResources().getDimensionPixelSize(R.dimen.ls_word_grid_two_column_width);
            binding.wordGrid.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                       oldLeft, oldTop, oldRight, oldBottom) -> {
                int columns = right - left >= minWidth ? 2 : 1;
                if (grid.getSpanCount() != columns) {
                    grid.setSpanCount(columns);
                }
            });
        }

        binding.btnQuiz.setEnabled(false);
        binding.btnQuiz.setOnClickListener(v -> startThemeQuiz(binding, container, themeId));
        binding.stopThemeAudio.setOnClickListener(v -> container.speech.stopSequence(themeAudio));

        vm.state().observe(getViewLifecycleOwner(), state -> render(binding, adapter, state));
        container.content.load(container.executors, () -> vm.init(themeId));
    }

    private void render(FragmentWordListBinding binding, WordAdapter adapter, WordListViewModel.UiState s) {
        if (s == null) {
            return;
        }
        binding.listTitle.setText(s.theme.nameEn);
        binding.listProgress.setMax(Math.max(1, s.words.size()));
        binding.listProgress.setProgressCompat(s.learned, false);
        binding.listProgressText.setText(
                getString(R.string.wl_learned, s.learned, s.words.size()));
        adapter.setItems(s.words);
        binding.btnQuiz.setEnabled(!launchingQuiz);
        requireActivity().invalidateOptionsMenu();
    }

    boolean hasWords() {
        WordListViewModel.UiState state = vm == null ? null : vm.state().getValue();
        return state != null && !state.words.isEmpty();
    }

    boolean hasBrowsingPosition() {
        WordListViewModel.UiState state = vm == null ? null : vm.state().getValue();
        return state != null && state.resumeIndex >= 0;
    }

    boolean isPlayingTheme() {
        return playingTheme;
    }

    void playTheme() {
        WordListViewModel.UiState state = vm.state().getValue();
        if (!playingTheme && state != null) {
            host().appContainer().speech.playSequence(state.theme.words, themeAudio);
        }
    }

    void continueBrowsing() {
        WordListViewModel.UiState state = vm.state().getValue();
        if (state != null) {
            openCards(Math.max(0, state.resumeIndex));
        }
    }

    private void openCards(int selected) {
        WordListViewModel.UiState state = vm.state().getValue();
        if (state == null || selected < 0 || selected >= state.words.size()) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (WordListViewModel.WordItem item : state.words) {
            ids.add(item.word.id);
        }
        host().openCards(ids, selected);
    }

    private boolean previewWord(int selected, @NonNull View anchor) {
        WordListViewModel.UiState state = vm == null ? null : vm.state().getValue();
        if (state == null || selected < 0 || selected >= state.words.size()) {
            return false;
        }
        Word word = state.words.get(selected).word;
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        host().appContainer().speech
                .playSequence(Collections.singletonList(word), previewAudio);
        return true;
    }

    @Override
    public void onPause() {
        AppContainer container = host().appContainer();
        container.speech.stopSequence(themeAudio);
        container.speech.stopSequence(previewAudio);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        launchingQuiz = false;
        super.onDestroyView();
    }

    /** Quiz 入口：建 THEME 会话（立即落盘）→ QuizActivity(THEME)。 */
    private void startThemeQuiz(FragmentWordListBinding binding, AppContainer container, String themeId) {
        if (!binding.btnQuiz.isEnabled()) {
            return;
        }
        launchingQuiz = true;
        binding.btnQuiz.setEnabled(false);
        container.store.update(state -> {
            ProgressState.Session s = container.sessionBuilder.buildThemeSession(state, themeId);
            long id = s.id;
            container.store.flushNow();
            container.executors.mainThread().execute(() -> {
                if (this.binding != binding) {
                    return;
                }
                FragmentActivity host = getActivity();
                if (host == null || host.isFinishing() || host.isDestroyed()) {
                    resetQuizLaunch();
                    return;
                }
                quizLauncher.launch(QuizActivity.themeIntent(host, id));
                Motion.childEnter(host);
            });
        });
    }

    private void resetQuizLaunch() {
        launchingQuiz = false;
        if (binding != null) {
            binding.btnQuiz.setEnabled(vm.state().getValue() != null);
        }
    }

    private final class WordAdapter extends RecyclerView.Adapter<WordAdapter.Holder> {

        private List<WordListViewModel.WordItem> items = new ArrayList<>();

        void setItems(@NonNull List<WordListViewModel.WordItem> newItems) {
            items = new ArrayList<>(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemWordTileBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            WordListViewModel.WordItem item = items.get(position);
            int resId = item.word.image == null ? 0
                    : getResources().getIdentifier(item.word.image, "drawable",
                    requireContext().getPackageName());
            boolean hasDedicatedIllustration = resId != 0 && !"ill_placeholder".equals(item.word.image);
            if (hasDedicatedIllustration) {
                h.b.wordIllustration.setImageResource(resId);
                h.b.wordGlyph.setVisibility(View.GONE);
            } else {
                h.b.wordIllustration.setImageResource(R.drawable.ill_placeholder);
                h.b.wordGlyph.setVisibility(View.VISIBLE);
                h.b.wordGlyph.setText(item.word.hanzi);
            }
            h.b.wordHanzi.setText(item.word.hanzi);
            h.b.wordPinyin.setText(item.word.pinyin);
            h.b.wordEnglish.setText(item.word.english);
            h.b.wordDone.setVisibility(item.learned ? View.VISIBLE : View.GONE);
            h.b.wordTile.setOnClickListener(v -> openCards(h.getBindingAdapterPosition()));
            h.b.wordTile.setOnLongClickListener(v -> previewWord(h.getBindingAdapterPosition(), v));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemWordTileBinding b;

            Holder(@NonNull ItemWordTileBinding binding) {
                super(binding.getRoot());
                b = binding;
            }
        }
    }
}
