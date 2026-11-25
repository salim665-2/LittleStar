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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.databinding.FragmentReviewListBinding;
import com.littlestar.app.databinding.ItemReviewAnswerBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 本轮回顾的答案列表（Fragment）：答对数量 + 逐题行。
 * 手机点一行由 Activity 换成详情 Fragment；平板只更新选中项，右侧详情同时显示。
 */
public final class ReviewListFragment extends Fragment {

    private FragmentReviewListBinding binding;
    private RoundReviewViewModel vm;
    private ReviewAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_review_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentReviewListBinding.bind(view);
        RoundReviewActivity host = (RoundReviewActivity) requireActivity();
        vm = new ViewModelProvider(requireActivity(),
                ViewModelFactory.of(() -> new RoundReviewViewModel(host.appContainer())))
                .get(RoundReviewViewModel.class);

        adapter = new ReviewAdapter();
        binding.answerList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.answerList.setAdapter(adapter);

        vm.state().observe(getViewLifecycleOwner(), s -> {
            if (s == null) {
                return;
            }
            boolean empty = s.items.isEmpty();
            binding.reviewSummary.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.answerList.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.reviewSummary.setText(getString(R.string.review_summary, s.correct, s.total));
            adapter.setItems(s.items);
        });
        vm.selected().observe(getViewLifecycleOwner(), adapter::setSelected);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        adapter = null;
    }

    private void onRowClick(int position) {
        vm.select(position);
        ((RoundReviewActivity) requireActivity()).onItemSelected(position);
    }

    private final class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.Holder> {

        private List<RoundReviewViewModel.ReviewItem> items = new ArrayList<>();
        private int selected;

        void setItems(@NonNull List<RoundReviewViewModel.ReviewItem> newItems) {
            items = new ArrayList<>(newItems);
            notifyDataSetChanged();
        }

        void setSelected(int index) {
            if (index == selected) {
                return;
            }
            int old = selected;
            selected = index;
            if (old >= 0 && old < items.size()) {
                notifyItemChanged(old);
            }
            if (selected >= 0 && selected < items.size()) {
                notifyItemChanged(selected);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemReviewAnswerBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            RoundReviewViewModel.ReviewItem item = items.get(position);
            Word word = item.word;

            h.b.rowHanzi.setText(word.hanzi);
            h.b.rowPinyin.setText(word.pinyin);
            h.b.rowEnglish.setText(word.english);

            String chosen = item.chosen.isEmpty()
                    ? getString(R.string.review_no_answer) : item.chosen;
            h.b.rowAnswer.setText(item.correct
                    ? getString(R.string.review_correct)
                    : getString(R.string.review_wrong, chosen, word.hanzi));
            h.b.rowAnswer.setTextColor(ContextCompat.getColor(requireContext(),
                    item.correct ? R.color.ls_muted : R.color.ls_error));

            ImageView status = h.b.rowStatus;
            status.setImageResource(item.correct ? R.drawable.ic_check : R.drawable.ic_close);
            status.setColorFilter(ContextCompat.getColor(requireContext(),
                    item.correct ? R.color.ls_green : R.color.ls_error));
            status.setContentDescription(getString(item.correct
                    ? R.string.review_correct_desc : R.string.review_wrong_desc));

            h.b.btnSpeak.setContentDescription(getString(R.string.review_play_desc, word.hanzi));
            h.b.btnSpeak.setOnClickListener(v ->
                    ((RoundReviewActivity) requireActivity()).appContainer().speech.play(word));

            boolean active = position == selected;
            h.b.answerRow.setStrokeWidth(dp(active ? 2 : 0));
            h.b.answerRow.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.ls_primary));
            h.b.getRoot().setContentDescription(getString(R.string.review_select_desc));
            h.b.getRoot().setOnClickListener(v -> onRowClick(h.getBindingAdapterPosition()));
        }

        private int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemReviewAnswerBinding b;

            Holder(@NonNull ItemReviewAnswerBinding binding) {
                super(binding.getRoot());
                b = binding;
            }
        }
    }
}
