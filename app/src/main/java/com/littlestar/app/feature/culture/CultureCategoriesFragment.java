package com.littlestar.app.feature.culture;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.BlockLayout;
import com.littlestar.app.data.content.model.CultureCategory;
import com.littlestar.app.databinding.FragmentCultureCategoriesBinding;

import java.util.List;

/**
 * Displays the three bundled cultural categories (Songs, Stories, Poems) inside an adaptive pane.
 * References consulted:
 * - Android Developers: Create a fragment (https://developer.android.com/guide/fragments/create)
 * - Android Developers: Support different screen sizes (https://developer.android.com/guide/topics/large-screens/support-different-screen-sizes)
 */
public final class CultureCategoriesFragment extends Fragment {

    public CultureCategoriesFragment() {
        super(R.layout.fragment_culture_categories);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FragmentCultureCategoriesBinding binding = FragmentCultureCategoriesBinding.bind(view);
        CultureCategoriesActivity host = (CultureCategoriesActivity) requireActivity();
        CultureCategoriesViewModel vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new CultureCategoriesViewModel(host.appContainer())))
                .get(CultureCategoriesViewModel.class);

        vm.categories().observe(getViewLifecycleOwner(), items -> render(binding, host, items));
        host.appContainer().content.load(host.appContainer().executors, vm::load);
    }

    private void render(@NonNull FragmentCultureCategoriesBinding binding,
                        @NonNull CultureCategoriesActivity host,
                        @NonNull List<CultureCategoriesViewModel.CategoryItem> items) {
        for (CultureCategoriesViewModel.CategoryItem item : items) {
            CultureCategory c = item.category;
            switch (c.id) {
                case "songs":
                    bind(host, c, item.count, binding.nameSongs, binding.nameSongsEn,
                            binding.countSongs, binding.catSongs);
                    break;
                case "stories":
                    bind(host, c, item.count, binding.nameStories, binding.nameStoriesEn,
                            binding.countStories, binding.catStories);
                    break;
                case "poems":
                    bind(host, c, item.count, binding.namePoems, binding.namePoemsEn,
                            binding.countPoems, binding.catPoems);
                    break;
                default:
                    break;
            }
        }
    }

    private void bind(@NonNull CultureCategoriesActivity host,
                      @NonNull CultureCategory c,
                      int count,
                      @NonNull TextView name,
                      @Nullable TextView nameEn,
                      @NonNull TextView countView,
                      @NonNull BlockLayout block) {
        name.setText(c.nameZh);
        if (nameEn != null) {
            nameEn.setText(c.nameEn);
        }
        int unitRes = switch (c.id) {
            case "songs" -> R.string.cul_unit_shou;
            case "poems" -> R.string.cul_unit_poems;
            default -> R.string.cul_unit_ge;
        };
        String unit = getString(unitRes);
        countView.setText(getString(R.string.cul_count_fmt, count, unit));
        block.setOnClickListener(v -> host.showCategory(c.id));
    }
}
