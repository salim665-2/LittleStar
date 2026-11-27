package com.littlestar.app.feature.culture;

import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.littlestar.app.AppContainer;
import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.ContentImage;
import com.littlestar.app.data.content.model.CultureItem;
import com.littlestar.app.databinding.FragmentCultureListBinding;
import com.littlestar.app.databinding.ItemCultureRowBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays cultural items for the selected category with tap-for-details, direct play,
 * and long-press quick-preview dialog interaction.
 * References consulted:
 * - Android Developers: Fragment lifecycle & state (https://developer.android.com/guide/fragments/lifecycle)
 * - Android Developers: Handle touch and long-click input (https://developer.android.com/develop/ui/views/touch-and-input/input-events)
 * - Material Design 3: Dialogs (https://m3.material.io/components/dialogs/overview)
 */
public final class CultureListFragment extends Fragment {

    static final String ARG_CATEGORY_ID = "categoryId";

    interface Host {
        @NonNull AppContainer appContainer();
        void openCultureDetail(@NonNull String itemId);
        void openCulturePlayer(@NonNull String itemId, @NonNull List<String> queue);
        void onCultureListReady();
    }

    private FragmentCultureListBinding binding;
    private CultureListViewModel vm;
    @Nullable
    private AlertDialog previewDialog;

    public CultureListFragment() {
        super(R.layout.fragment_culture_list);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FragmentCultureListBinding binding = FragmentCultureListBinding.bind(view);
        this.binding = binding;
        Host host = (Host) requireActivity();
        AppContainer container = host.appContainer();
        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new CultureListViewModel(container)))
                .get(CultureListViewModel.class);

        RowAdapter adapter = new RowAdapter(container);
        adapter.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        binding.itemList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.itemList.setAdapter(adapter);

        String categoryId = getArguments() != null ? getArguments().getString(ARG_CATEGORY_ID) : null;
        vm.state().observe(getViewLifecycleOwner(), state -> render(binding, adapter, host, state));
        container.content.load(container.executors, () -> vm.init(categoryId));
    }

    private void render(@NonNull FragmentCultureListBinding binding,
                        @NonNull RowAdapter adapter,
                        @NonNull Host host,
                        @Nullable CultureListViewModel.UiState s) {
        if (s == null) {
            return;
        }
        binding.categoryName.setText(s.category.nameEn);
        binding.categoryCount.setText(s.category.nameZh);
        adapter.setItems(s.items);
        host.onCultureListReady();
    }

    boolean hasItems() {
        CultureListViewModel.UiState s = vm == null ? null : vm.state().getValue();
        return s != null && !s.items.isEmpty();
    }

    void playCategory() {
        openPlayer(0);
    }

    private void openDetail(int index) {
        CultureListViewModel.UiState s = vm == null ? null : vm.state().getValue();
        if (s == null || index < 0 || index >= s.items.size() || !isAdded()) {
            return;
        }
        ((Host) requireActivity()).openCultureDetail(s.items.get(index).id);
    }

    private void openPlayer(int index) {
        CultureListViewModel.UiState s = vm == null ? null : vm.state().getValue();
        if (s == null || index < 0 || index >= s.items.size() || !isAdded()) {
            return;
        }
        List<String> queue = new ArrayList<>();
        for (CultureItem item : s.items) {
            queue.add(item.id);
        }
        ((Host) requireActivity()).openCulturePlayer(s.items.get(index).id, queue);
    }

    private boolean showQuickPreview(int index, @NonNull View anchor) {
        CultureListViewModel.UiState s = vm == null ? null : vm.state().getValue();
        if (s == null || index < 0 || index >= s.items.size() || !isAdded()) {
            return false;
        }
        CultureItem item = s.items.get(index);
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (previewDialog != null && previewDialog.isShowing()) {
            previewDialog.dismiss();
        }
        String message = item.summary + "\n\n"
                + getString(R.string.culture_detail_focus) + ": " + item.learningFocus;
        previewDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.notif_culture_title, item.titleZh, item.titleEn))
                .setMessage(message)
                .setPositiveButton(R.string.culture_preview_play, (d, which) -> openPlayer(index))
                .setNeutralButton(R.string.culture_preview_discover, (d, which) -> openDetail(index))
                .setNegativeButton(R.string.culture_preview_close, null)
                .show();
        return true;
    }

    @Override
    public void onDestroyView() {
        if (previewDialog != null) {
            previewDialog.dismiss();
            previewDialog = null;
        }
        binding = null;
        super.onDestroyView();
    }

    private final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {

        private final AppContainer container;
        private List<CultureItem> items = new ArrayList<>();

        RowAdapter(@NonNull AppContainer container) {
            this.container = container;
        }

        void setItems(@NonNull List<CultureItem> newItems) {
            items = new ArrayList<>(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemCultureRowBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            CultureItem item = items.get(position);
            h.cover.bind(item.cover);
            h.b.rowTitle.setText(item.titleZh);
            h.b.rowTitleEn.setText(item.titleEn);
            h.b.rowSub.setText(getString(R.string.player_subtitle_fmt, item.subtitleEn,
                    CultureListActivity.formatDuration(item.durationSec)));
            h.b.rowBlock.setContentDescription(getString(R.string.culture_detail_open, item.titleEn));
            h.b.btnPlay.setContentDescription(getString(R.string.culture_list_play_item, item.titleEn));
            h.b.rowBlock.setOnClickListener(v -> openDetail(h.getBindingAdapterPosition()));
            h.b.rowBlock.setOnLongClickListener(v -> showQuickPreview(h.getBindingAdapterPosition(), v));
            h.b.btnPlay.setOnClickListener(v -> {
                v.setEnabled(false);
                v.postDelayed(() -> v.setEnabled(true), 800);
                openPlayer(h.getBindingAdapterPosition());
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemCultureRowBinding b;
            final ContentImage cover;

            Holder(@NonNull ItemCultureRowBinding binding) {
                super(binding.getRoot());
                b = binding;
                cover = new ContentImage(b.rowCover, container.executors);
            }
        }
    }
}
