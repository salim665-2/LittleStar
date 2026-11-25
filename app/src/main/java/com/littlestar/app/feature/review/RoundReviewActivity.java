package com.littlestar.app.feature.review;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.littlestar.app.AppContainer;
import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.databinding.ActivityRoundReviewBinding;

/**
 * 本轮回顾（A1）：Reward 通过 sessionId 进入；Activity 只做 Fragment 容器。
 * 手机：列表 → 详情整页替换（可返回）；平板：左列表右详情同屏。
 */
public final class RoundReviewActivity extends ChildActivity {

    private static final String EXTRA_SESSION_ID = "sessionId";
    private static final String TAG_DETAIL = "reviewDetail";

    @NonNull
    public static Intent intent(@NonNull Activity from, long sessionId) {
        Intent i = new Intent(from, RoundReviewActivity.class);
        i.putExtra(EXTRA_SESSION_ID, sessionId);
        return i;
    }

    private ActivityRoundReviewBinding binding;
    private RoundReviewViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRoundReviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new RoundReviewViewModel(container())))
                .get(RoundReviewViewModel.class);
        // The restored task may open this Activity in a fresh process. Resolve
        // answer word IDs only after bundled content is loaded.
        container().content.load(container().executors,
                () -> vm.init(getIntent().getLongExtra(EXTRA_SESSION_ID, -1)));

        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentById(R.id.reviewPane) == null) {
            fm.beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.reviewPane, ReviewListFragment.class, null)
                    .commitNow();
        }
        if (isTwoPane() && fm.findFragmentById(R.id.reviewDetailPane) == null) {
            fm.beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.reviewDetailPane, ReviewDetailFragment.class, null)
                    .commitNow();
        }

        binding.btnBack.setOnClickListener(v -> goBack());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBack();
            }
        });

        vm.state().observe(this, s -> {
            if (s == null || binding.reviewDetailPane == null) {
                return;
            }
            binding.reviewDetailPane.setVisibility(s.items.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    /** 手机点列表行：交给列表 Fragment 之后，这里负责换成详情页。 */
    void onItemSelected(int index) {
        if (isTwoPane()) {
            return;
        }
        FragmentManager fm = getSupportFragmentManager();
        if (fm.isStateSaved()) {
            return;
        }
        fm.beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(R.anim.ls_slide_in_right, R.anim.ls_slide_out_left,
                        R.anim.ls_slide_in_left, R.anim.ls_slide_out_right)
                .replace(R.id.reviewPane, ReviewDetailFragment.class, null)
                .addToBackStack(TAG_DETAIL)
                .commit();
    }

    private void goBack() {
        FragmentManager fm = getSupportFragmentManager();
        if (!isTwoPane() && fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
            return;
        }
        finish();
        Motion.childExit(this);
    }

    private boolean isTwoPane() {
        return binding.reviewDetailPane != null;
    }

    @NonNull
    AppContainer appContainer() {
        return container();
    }
}
