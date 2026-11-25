package com.littlestar.app.feature.review;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.databinding.FragmentReviewDetailBinding;

/**
 * 本轮回顾的单题详情（Fragment）：大字 + 所选/正确答案 + 发音 + Previous/Next。
 * 由 {@link RoundReviewActivity} 同时驱动平板右侧栏与手机整页。
 */
public final class ReviewDetailFragment extends Fragment {

    private FragmentReviewDetailBinding binding;
    private RoundReviewViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_review_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentReviewDetailBinding.bind(view);
        RoundReviewActivity host = (RoundReviewActivity) requireActivity();
        vm = new ViewModelProvider(requireActivity(),
                ViewModelFactory.of(() -> new RoundReviewViewModel(host.appContainer())))
                .get(RoundReviewViewModel.class);

        binding.btnPrev.setOnClickListener(v -> step(-1));
        binding.btnNext.setOnClickListener(v -> step(1));

        vm.state().observe(getViewLifecycleOwner(), s -> {
            if (s == null) {
                return;
            }
            render(s);
        });
        vm.selected().observe(getViewLifecycleOwner(), index -> {
            RoundReviewViewModel.UiState s = vm.state().getValue();
            if (s == null) {
                return;
            }
            render(s);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void step(int delta) {
        Integer index = vm.selected().getValue();
        vm.select((index == null ? 0 : index) + delta);
    }

    private void render(@NonNull RoundReviewViewModel.UiState s) {
        Integer raw = vm.selected().getValue();
        int index = raw == null ? 0 : Math.max(0, Math.min(raw, s.items.size() - 1));
        boolean empty = s.items.isEmpty();
        binding.detailHanzi.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        if (empty) {
            return;
        }
        RoundReviewViewModel.ReviewItem item = s.items.get(index);
        Word word = item.word;

        binding.detailHanzi.setText(word.hanzi);
        binding.detailPinyin.setText(word.pinyin);
        binding.detailEnglish.setText(word.english);
        binding.detailPinyin.setVisibility(word.pinyin.isEmpty() ? View.GONE : View.VISIBLE);
        binding.detailEnglish.setVisibility(word.english.isEmpty() ? View.GONE : View.VISIBLE);

        String chosen = item.chosen.isEmpty()
                ? getString(R.string.review_no_answer) : item.chosen;
        binding.detailAnswer.setText(item.correct
                ? getString(R.string.review_correct)
                : getString(R.string.review_wrong, chosen, word.hanzi));
        binding.detailAnswer.setTextColor(ContextCompat.getColor(requireContext(),
                item.correct ? R.color.ls_muted : R.color.ls_error));

        ImageView status = binding.detailStatus;
        status.setImageResource(item.correct ? R.drawable.ic_check : R.drawable.ic_close);
        status.setColorFilter(ContextCompat.getColor(requireContext(),
                item.correct ? R.color.ls_green : R.color.ls_error));
        status.setContentDescription(getString(item.correct
                ? R.string.review_correct_desc : R.string.review_wrong_desc));

        binding.btnSpeak.setContentDescription(getString(R.string.review_play_desc, word.hanzi));
        binding.btnSpeak.setOnClickListener(v ->
                ((RoundReviewActivity) requireActivity()).appContainer().speech.play(word));

        binding.detailPosition.setText(getString(R.string.review_position, index + 1, s.items.size()));
        binding.btnPrev.setEnabled(index > 0);
        binding.btnNext.setEnabled(index < s.items.size() - 1);
    }
}
