package com.example.hiremydriver;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends CustomerNavigationActivity {

    @Override
    protected boolean backgroundBehindSystemBars() {
        return true;
    }

    @Override
    protected View statusBarImageHeader() {
        return findViewById(R.id.header);
    }

    @Override
    protected void onKeyboardVisibilityChanged(boolean visible) {
        View navigation = findViewById(R.id.bottomNavigation);
        if (navigation != null) navigation.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    @Override
    protected Integer statusBarBackgroundColor() {
        return android.graphics.Color.rgb(6, 27, 65);
    }

    // Search Views
    private EditText edtSearch;
    private ImageButton btnClearSearch;
    private LinearLayout searchResultsSection;
    private LinearLayout searchListContainer;
    private LinearLayout searchEmptyView;
    private TextView txtSearchResultCount;

    // Home Sections
    private CardView cardAiBanner;
    private LinearLayout aiRecommendedSection;
    private LinearLayout driverSection;

    private final List<RegisteredDriver> allDrivers = new ArrayList<>();
    private LinearLayout registeredList;
    private CustomerDriverFilters driverFilters;
    private boolean resetHomeAfterTabChange;
    private boolean resettingHomeSearch;
    private String directoryMessage = "Loading registered drivers…";
    private long popularSkeletonUntil;
    private final Runnable revealPopularDrivers = () -> {
        if (!isFinishing() && !isDestroyed() && registeredList != null) renderRegisteredDrivers();
    };
    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable searchRefresh = new Runnable() {
        @Override public void run() {
            if (edtSearch != null && edtSearch.length() > 0) performSearch(edtSearch.getText().toString().trim());
            searchHandler.postDelayed(this, 60000);
        }
    };

    @Override protected void onPause() {
        searchHandler.removeCallbacks(searchRefresh);
        super.onPause();
    }

    @Override protected void onLeavingCustomerTab() {
        // Open the requested tab immediately; reset only after Home is off-screen.
        resetHomeAfterTabChange = true;
    }

    @Override protected void onStop() {
        super.onStop();
        resetHomeSearchIfNeeded();
    }

    private void resetHomeSearchIfNeeded() {
        if (!resetHomeAfterTabChange || edtSearch == null || driverFilters == null) return;
        resetHomeAfterTabChange = false;
        resettingHomeSearch = true;
        // Focus loss saves the query in history; opening Home won't reopen that history panel.
        edtSearch.clearFocus();
        edtSearch.setText("");
        driverFilters.reset();
        resettingHomeSearch = false;
        searchListContainer.removeAllViews();
        searchListContainer.setTag(null);
        txtSearchResultCount.setText("");
        clearSearch();
        androidx.core.widget.NestedScrollView scroll = findViewById(R.id.homeScrollView);
        scroll.scrollTo(0, 0);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState) ;
        if (CustomerAuth.currentUser(this) == null) {
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        setContentView(R.layout.activity_customer_home);
        resetHomeAfterTabChange = savedInstanceState != null
                && savedInstanceState.getBoolean("reset_home_after_tab_change", false);
        popularSkeletonUntil = savedInstanceState == null
                ? android.os.SystemClock.elapsedRealtime() + 1000
                : savedInstanceState.getLong("popular_skeleton_until", 0);
        CustomerAuth.syncAccount(getApplicationContext()).addOnSuccessListener(unused -> {
            PeerProfilePhoto.publishCustomerName(this);
            com.example.hiremydriver.notifications.AppNotifications.INSTANCE.register(this, true);
        });
        PeerProfilePhoto.syncCustomerPhoto(this);

        // Animate active nav background
        animateNavActiveBackground(findViewById(R.id.iconHome));

        findViewById(R.id.btnNotification).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, NotificationsActivity.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });


        initViews();
        setupRegisteredDrivers();
        driverFilters = new CustomerDriverFilters(this, () -> performSearch(edtSearch.getText().toString().trim()));
        setupSearchLogic();
        new CustomerSearchHistory(this);
        setupCustomerNavigation();
        new com.example.hiremydriver.recommendation.HomeDriverRecommendations(this, () -> {
            renderRegisteredDrivers();
            performSearch(edtSearch.getText().toString().trim());
        }, popularSkeletonUntil);
        RegisteredDriverDirectory.observe(this, (drivers, message) -> {
            allDrivers.clear();
            allDrivers.addAll(drivers);
            driverFilters.updateDrivers(drivers);
            directoryMessage = message;
            renderRegisteredDrivers();
            performSearch(edtSearch.getText().toString().trim());
        });
    }
    private void initViews() {
        edtSearch = findViewById(R.id.edtSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        searchResultsSection = findViewById(R.id.searchResultsSection);
        searchListContainer = findViewById(R.id.searchListContainer);
        searchEmptyView = findViewById(R.id.searchEmptyView);
        txtSearchResultCount = findViewById(R.id.txtSearchResultCount);

        cardAiBanner = findViewById(R.id.cardAiBanner);
        aiRecommendedSection = findViewById(R.id.aiRecommendedSection);
        driverSection = findViewById(R.id.driverSection);
    }

    private void setupSearchLogic() {
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> {
            edtSearch.setText("");
            performSearch("");
        });
    }

    private void performSearch(String query) {
        if (resettingHomeSearch) return;
        if (query.isEmpty() && (driverFilters == null || !driverFilters.isActive())) {
            clearSearch();
            return;
        }

        btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
        cardAiBanner.setVisibility(View.GONE);
        aiRecommendedSection.setVisibility(View.GONE);
        driverSection.setVisibility(View.GONE);
        searchResultsSection.setVisibility(View.VISIBLE);

        List<RegisteredDriver> filteredList = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (RegisteredDriver driver : allDrivers) {
            if ((query.isEmpty() || driver.matchesSearch(query, now)) && (driverFilters == null || driverFilters.matches(driver))) filteredList.add(driver);
        }

        displaySearchResults(filteredList, query);
    }

    private void displaySearchResults(List<RegisteredDriver> list, String query) {

        if (list.isEmpty()) {
            searchListContainer.setVisibility(View.GONE);
            searchEmptyView.setVisibility(View.VISIBLE);
            txtSearchResultCount.setText(query.isEmpty() ? "No drivers match your filters" : "0 drivers found for \"" + query + "\"");
        } else {
            searchEmptyView.setVisibility(View.GONE);
            searchListContainer.setVisibility(View.VISIBLE);
            txtSearchResultCount.setText(list.size() + " driver" + (list.size() > 1 ? "s" : "") + " found");

            RegisteredDriverDirectory.render(this, searchListContainer, list, "");
        }
    }

    private void clearSearch() {
        btnClearSearch.setVisibility(View.GONE);
        searchResultsSection.setVisibility(View.GONE);
        searchEmptyView.setVisibility(View.GONE);

        cardAiBanner.setVisibility(View.VISIBLE);
        aiRecommendedSection.setVisibility(View.VISIBLE);
        driverSection.setVisibility(View.VISIBLE);
    }

    private void renderRegisteredDrivers() {
        if (android.os.SystemClock.elapsedRealtime() < popularSkeletonUntil) {
            RegisteredDriverDirectory.render(this, registeredList, java.util.Collections.emptyList(), "Loading registered drivers…");
            return;
        }
        RegisteredDriverDirectory.render(this, registeredList, allDrivers, directoryMessage);
    }

    private void setupRegisteredDrivers() {
        // Keep the original heading and item_driver card template, replace demo rows only.
        if (driverSection.getChildCount() > 1) driverSection.removeViews(1, driverSection.getChildCount() - 1);
        registeredList = new LinearLayout(this);
        registeredList.setOrientation(LinearLayout.VERTICAL);
        driverSection.addView(registeredList);
        OfflineSkeleton.bind(this, registeredList, R.layout.item_driver, 2);
        searchHandler.postDelayed(revealPopularDrivers,
                Math.max(0, popularSkeletonUntil - android.os.SystemClock.elapsedRealtime()));
        renderRegisteredDrivers();        // SEE ALL & BANNER LISTENERS
        findViewById(R.id.txtSeeAllAi).setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, AiResultsActivity.class);
            intent.putExtra("BROWSE_RECOMMENDED", true);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        cardAiBanner.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, AiRecommendationActivity.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        findViewById(R.id.txtSeeAll).setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, PopularDriversActivity.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        findViewById(R.id.btnFilter).setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, FilterActivity.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("reset_home_after_tab_change", resetHomeAfterTabChange);
        outState.putLong("popular_skeleton_until", popularSkeletonUntil);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        searchHandler.removeCallbacks(revealPopularDrivers);
        searchHandler.removeCallbacks(searchRefresh);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        // Also cover a quick return or activity recreation before onStop could reset Home.
        resetHomeSearchIfNeeded();
        super.onResume();
        searchHandler.removeCallbacks(searchRefresh);
        searchHandler.post(searchRefresh);
        TextView txtHello = findViewById(R.id.txtHello);
        if (txtHello != null) {
            txtHello.setText(getString(R.string.hello_user, CustomerAuth.firstName(this)));
        }
        overridePendingTransition(0, 0);
    }
}
