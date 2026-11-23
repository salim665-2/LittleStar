package com.littlestar.app.feature.words;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.littlestar.app.AppContainer;
import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.databinding.FragmentWordListBinding;
import com.littlestar.app.databinding.ItemWordTileBinding;
import com.littlestar.app.feature.flashcard.FlashcardActivity;
import com.littlestar.app.feature.quiz.QuizActivity;

import java.util.ArrayList;
import java.util.List;

/** 词表（§7.3 / §9.3）：6 字网格 + Quiz(THEME) 入口。数据驱动：返回时绿勾自动刷新。 */
public final class WordListFragment extends Fragment {

    static final String ARG_THEME_ID = "themeId";
    private static final String STATE_UNRECORDED = "showUnrecorded";

    private FragmentWordListBinding binding;
    private WordAdapter adapter;
    private WordListViewModel.UiState state;
    private boolean showUnrecorded;
    private boolean launchingQuiz;

    public WordListFragment() {
        super(R.layout.fragment_word_list);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showUnrecorded = savedInstanceState != null && savedInstanceState.getBoolean(STATE_UNRECORDED);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding = FragmentWordListBinding.bind(view);
        ThemesActivity host = (ThemesActivity) requireActivity();
        AppContainer container = host.appContainer();
        WordListViewModel vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new WordListViewModel(container)))
                .get(WordListViewModel.class);

        String themeId = requireArguments().getString(ARG_THEME_ID);
        adapter = new WordAdapter();
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
        FragmentWordListBinding viewBinding = binding;
        binding.btnQuiz.setOnClickListener(v -> startThemeQuiz(viewBinding, container, themeId));

        vm.state().observe(getViewLifecycleOwner(), this::render);
        container.content.load(container.executors, () -> vm.init(themeId));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_UNRECORDED, showUnrecorded);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.wordGrid.setAdapter(null);
        binding = null;
        adapter = null;
        state = null;
    }

    boolean hasWords() {
        return state != null && !state.words.isEmpty();
    }

    boolean hasVisibleWords() {
        return adapter != null && adapter.getItemCount() > 0;
    }

    boolean isShowingUnrecorded() {
        return showUnrecorded;
    }

    void practiceWords() {
        adapter.openCards(0);
    }

    void toggleUnrecorded() {
        showUnrecorded = !showUnrecorded;
        renderWords();
        binding.wordGrid.scrollToPosition(0);
    }

    private void render(WordListViewModel.UiState s) {
        if (s == null) {
            return;
        }
        boolean wasReady = hasWords();
        boolean hadVisibleWords = hasVisibleWords();
        state = s;
        binding.listTitle.setText(s.theme.nameEn);
        binding.listProgress.setMax(Math.max(1, s.words.size()));
        binding.listProgress.setProgressCompat(s.learned, false);
        binding.listProgressText.setText(
                getString(R.string.wl_learned, s.learned, s.words.size()));
        renderWords();
        binding.btnQuiz.setEnabled(!launchingQuiz);
        if (wasReady != hasWords() || hadVisibleWords != hasVisibleWords()) {
            requireActivity().invalidateOptionsMenu();
        }
    }

    private void renderWords() {
        List<WordListViewModel.WordItem> visible = new ArrayList<>();
        for (WordListViewModel.WordItem item : state.words) {
            if (!showUnrecorded || !item.learned) {
                visible.add(item);
            }
        }
        adapter.setItems(visible);
        boolean allRecorded = showUnrecorded && visible.isEmpty();
        binding.wordGridEmpty.setVisibility(allRecorded ? View.VISIBLE : View.GONE);
        binding.wordListHintRow.setVisibility(allRecorded ? View.INVISIBLE : View.VISIBLE);
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
                if (getView() != binding.getRoot() || requireActivity().isFinishing()) {
                    return;
                }
                startActivity(QuizActivity.themeIntent(requireActivity(), id));
                Motion.childEnter(requireActivity());
                binding.btnQuiz.postDelayed(() -> {
                    launchingQuiz = false;
                    binding.btnQuiz.setEnabled(true);
                }, 800);
            });
        });
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
        }

        void openCards(int selected) {
            if (selected < 0 || selected >= items.size()) {
                return;
            }
            List<String> ids = new ArrayList<>();
            for (WordListViewModel.WordItem item : items) {
                ids.add(item.word.id);
            }
            startActivity(FlashcardActivity.browseIntent(requireActivity(), ids, selected));
            Motion.childEnter(requireActivity());
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
