package com.droidev.flyffuwebviewclient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.view.WindowManager;
import android.widget.PopupMenu;
import android.util.DisplayMetrics;
import android.animation.ObjectAnimator;
import android.view.ViewConfiguration;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private List<WebView> webViews = new ArrayList<>();
    private List<FrameLayout> layouts = new ArrayList<>();
    private int activeClientIndex = -1;

    private LinearLayout linearLayout;
    private FloatingActionButton floatingActionButton;

    private boolean exit = false;
    private final String userAgent = System.getProperty("http.agent");
    private final String url = "https://universe.flyff.com/play";

    private int _xDelta;
    private int _yDelta;
    private int screenWidth;
    private int screenHeight;
    private long downTime;
    private float initialRawX;
    private float initialRawY;

    private final Handler longPressHandler = new Handler();
    private Runnable longPressRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setTitle("FlyffU Android");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        fullScreenOn();

        linearLayout = findViewById(R.id.linearLayout);
        floatingActionButton = findViewById(R.id.fab);
        floatingActionButton.setAlpha(0.5f);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        screenWidth = displayMetrics.widthPixels;
        screenHeight = displayMetrics.heightPixels;

        setupFabTouchListener();

        if (savedInstanceState == null) {
            createNewClient();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupFabTouchListener() {
        longPressRunnable = () -> floatingActionButton.performLongClick();

        floatingActionButton.setOnTouchListener((view, event) -> {
            final int X = (int) event.getRawX();
            final int Y = (int) event.getRawY();

            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    downTime = event.getDownTime();
                    initialRawX = event.getRawX();
                    initialRawY = event.getRawY();
                    _xDelta = (int) (X - view.getX());
                    _yDelta = (int) (Y - view.getY());
                    longPressHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                    return true;
                case MotionEvent.ACTION_UP:
                    longPressHandler.removeCallbacks(longPressRunnable);
                    long eventDuration = event.getEventTime() - downTime;
                    float deltaX = Math.abs(event.getRawX() - initialRawX);
                    float deltaY = Math.abs(event.getRawY() - initialRawY);
                    int touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();

                    if (deltaX < touchSlop && deltaY < touchSlop) {
                        if (eventDuration < ViewConfiguration.getLongPressTimeout()) {
                            view.performClick();
                        }
                    } else {
                        snapFabToEdge(view);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int currentTouchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
                    float currentDeltaX = Math.abs(event.getRawX() - initialRawX);
                    float currentDeltaY = Math.abs(event.getRawY() - initialRawY);
                    if (currentDeltaX > currentTouchSlop || currentDeltaY > currentTouchSlop) {
                        longPressHandler.removeCallbacks(longPressRunnable);
                    }
                    view.setX(X - _xDelta);
                    view.setY(Y - _yDelta);
                    return true;
            }
            return false;
        });

        floatingActionButton.setOnClickListener(view -> switchToNextClient());

        floatingActionButton.setOnLongClickListener(view -> {
            showClientManagerMenu(view);
            return true;
        });
    }

    private void snapFabToEdge(View view) {
        int fabWidth = view.getWidth();
        float currentX = view.getX();
        float targetX = currentX;

        if (currentX < -fabWidth * 0.5f) {
            targetX = -fabWidth * 0.5f;
        } else if (currentX > screenWidth - fabWidth * 0.5f) {
            targetX = screenWidth - fabWidth * 0.5f;
        }

        if (targetX != currentX) {
            ObjectAnimator.ofFloat(view, "x", targetX).setDuration(200).start();
        }
    }

    private void showClientManagerMenu(View view) {
        PopupMenu popup = new PopupMenu(MainActivity.this, view);
        popup.getMenu().add(Menu.NONE, 1, Menu.NONE, "New Client");

        if (!webViews.isEmpty()) {
            SubMenu clientsMenu = popup.getMenu().addSubMenu(Menu.NONE, 2, Menu.NONE, "Clients");
            for (int i = 0; i < webViews.size(); i++) {
                SubMenu clientSubMenu = clientsMenu.addSubMenu(Menu.NONE, Menu.NONE, i, "Client " + (i + 1));
                clientSubMenu.add(Menu.NONE, 100 + i, 1, "Switch to");
                clientSubMenu.add(Menu.NONE, 200 + i, 2, "Kill");
            }
        }

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == 1) { // New Client
                createNewClient();
                return true;
            } else if (itemId >= 100 && itemId < 200) { // Switch to
                int clientIndex = itemId - 100;
                switchToClient(clientIndex);
                return true;
            } else if (itemId >= 200 && itemId < 300) { // Kill
                int clientIndex = itemId - 200;
                confirmKillClient(clientIndex);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void createNewClient() {
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        linearLayout.addView(frameLayout);
        layouts.add(frameLayout);

        WebView webView = new WebView(getApplicationContext());
        createWebViewer(webView, frameLayout);
        webViews.add(webView);

        switchToClient(webViews.size() - 1);
        floatingActionButton.setVisibility(View.VISIBLE);
    }

    private void switchToNextClient() {
        if (webViews.size() > 1) {
            int nextIndex = (activeClientIndex + 1) % webViews.size();
            switchToClient(nextIndex);
        }
    }

    private void switchToClient(int index) {
        if (index < 0 || index >= webViews.size()) return;

        for (int i = 0; i < layouts.size(); i++) {
            layouts.get(i).setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        activeClientIndex = index;
        setTitle("FlyffU Android - Client " + (activeClientIndex + 1));
    }

    private void confirmKillClient(int index) {
        if (index < 0 || index >= webViews.size()) return;

        AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                .setCancelable(false)
                .setTitle("Are you sure?")
                .setMessage("Do you want to kill Client " + (index + 1) + "?")
                .setPositiveButton("Yes", null)
                .setNegativeButton("No", null)
                .show();

        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(v -> {
            killClient(index);
            dialog.dismiss();
        });
    }

    private void killClient(int index) {
        if (index < 0 || index >= webViews.size()) return;

        WebView webViewToKill = webViews.get(index);
        FrameLayout layoutToKill = layouts.get(index);

        layoutToKill.removeAllViews();
        webViewToKill.destroy();
        linearLayout.removeView(layoutToKill);

        webViews.remove(index);
        layouts.remove(index);

        Toast.makeText(this, "Client " + (index + 1) + " killed.", Toast.LENGTH_SHORT).show();

        if (webViews.isEmpty()) {
            activeClientIndex = -1;
            setTitle("FlyffU Android");
            // Optionally, you could close the app or show a "create new client" screen
        } else {
            if (activeClientIndex >= index) {
                activeClientIndex = Math.max(0, activeClientIndex - 1);
            }
            switchToClient(activeClientIndex);
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    private void createWebViewer(WebView webView, FrameLayout frameLayout) {
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        webView.requestFocus(View.FOCUS_DOWN);
        webView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_UP:
                    if (!v.hasFocus()) {
                        v.requestFocus();
                    }
                    break;
            }
            return false;
        });

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUserAgentString(userAgent);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        frameLayout.addView(webView);
        webView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (exit) {
            finish();
        } else {
            Toast.makeText(this, "Press Back again to Exit.", Toast.LENGTH_SHORT).show();
            exit = true;
            new Handler().postDelayed(() -> exit = false, 3 * 1000);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (WebView webView : webViews) {
            webView.destroy();
        }
        webViews.clear();
        layouts.clear();
    }

    private void fullScreenOn() {
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
            windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Note: Saving state for multiple WebViews is complex.
        // This basic implementation only saves the first client's state.
        if (!webViews.isEmpty()) {
            webViews.get(0).saveState(outState);
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // Note: Restoring state for multiple WebViews is complex.
        // This basic implementation only restores the first client's state.
        if (!webViews.isEmpty()) {
            webViews.get(0).restoreState(savedInstanceState);
        }
    }
}
