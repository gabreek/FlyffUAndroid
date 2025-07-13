package com.droidev.flyffuwebviewclient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int MAX_CLIENTS = 10; // Let's allow up to 10 slots

    private SparseArray<WebView> webViews = new SparseArray<>();
    private SparseArray<FrameLayout> layouts = new SparseArray<>();
    private int activeClientId = -1;

    private LinearLayout linearLayout;
    private FloatingActionButton floatingActionButton;

    private boolean exit = false;
    private final String userAgent = System.getProperty("http.agent");
    private final String url = "https://universe.flyff.com/play";

    // FAB movement variables
    private int _xDelta;
    private int _yDelta;
    private int screenWidth;
    private int screenHeight;
    private long downTime;
    private float initialRawX;
    private float initialRawY;
    private final Handler longPressHandler = new Handler();
    private Runnable longPressRunnable;

    /**
     * This interface is the bridge between the WebView's JavaScript and Android code.
     * It intercepts localStorage calls and redirects them to a client-specific TinyDB.
     */
    public static class LocalStorageInterface {
        private TinyDB db;

        LocalStorageInterface(Context context, int clientId) {
            this.db = new TinyDB(context, "client_prefs_" + clientId);
        }

        @JavascriptInterface
        public void setItem(String key, String value) {
            db.putString(key, value);
        }

        @JavascriptInterface
        public String getItem(String key) {
            return db.getString(key);
        }

        @JavascriptInterface
        public void removeItem(String key) {
            db.remove(key);
        }

        @JavascriptInterface
        public void clear() {
            db.clear();
        }
    }

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
            createNewClient(); // Start with one client
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
                    if (Math.abs(event.getRawX() - initialRawX) > currentTouchSlop || Math.abs(event.getRawY() - initialRawY) > currentTouchSlop) {
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

    private void showClientManagerMenu(View view) {
        PopupMenu popup = new PopupMenu(MainActivity.this, view);
        popup.getMenu().add(Menu.NONE, 1, Menu.NONE, "New Client");

        if (webViews.size() > 0) {
            SubMenu clientsMenu = popup.getMenu().addSubMenu(Menu.NONE, 2, Menu.NONE, "Clients");
            for (int i = 0; i < webViews.size(); i++) {
                int clientId = webViews.keyAt(i);
                SubMenu clientSubMenu = clientsMenu.addSubMenu(Menu.NONE, Menu.NONE, i, "Client " + clientId);
                clientSubMenu.add(Menu.NONE, 1000 + clientId, 1, "Switch to");
                clientSubMenu.add(Menu.NONE, 2000 + clientId, 2, "Kill");
            }
        }

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == 1) { // New Client
                createNewClient();
                return true;
            } else if (itemId >= 1000 && itemId < 2000) { // Switch to
                switchToClient(itemId - 1000);
                return true;
            } else if (itemId >= 2000 && itemId < 3000) { // Kill
                confirmKillClient(itemId - 2000);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void createNewClient() {
        if (webViews.size() >= MAX_CLIENTS) {
            Toast.makeText(this, "Max clients reached", Toast.LENGTH_SHORT).show();
            return;
        }

        int newClientId = -1;
        for (int i = 1; i <= MAX_CLIENTS; i++) {
            if (webViews.get(i) == null) {
                newClientId = i;
                break;
            }
        }

        if (newClientId == -1) {
            Toast.makeText(this, "Could not find an available client slot.", Toast.LENGTH_SHORT).show();
            return;
        }

        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        linearLayout.addView(frameLayout);
        layouts.put(newClientId, frameLayout);

        WebView webView = new WebView(getApplicationContext());
        createWebViewer(webView, frameLayout, newClientId);
        webViews.put(newClientId, webView);

        switchToClient(newClientId);
        floatingActionButton.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Client " + newClientId + " created.", Toast.LENGTH_SHORT).show();
    }

    private void switchToNextClient() {
        if (webViews.size() > 1) {
            List<Integer> clientIds = new ArrayList<>();
            for (int i = 0; i < webViews.size(); i++) {
                clientIds.add(webViews.keyAt(i));
            }
            Collections.sort(clientIds);

            int currentIndexInList = clientIds.indexOf(activeClientId);
            int nextIndexInList = (currentIndexInList + 1) % clientIds.size();
            int nextClientId = clientIds.get(nextIndexInList);

            switchToClient(nextClientId);
        }
    }

    private void switchToClient(int clientId) {
        if (webViews.get(clientId) == null) return;

        for (int i = 0; i < layouts.size(); i++) {
            int key = layouts.keyAt(i);
            layouts.get(key).setVisibility(key == clientId ? View.VISIBLE : View.GONE);
        }
        activeClientId = clientId;
        setTitle("FlyffU Android - Client " + activeClientId);
    }

    private void confirmKillClient(int clientId) {
        if (webViews.get(clientId) == null) return;

        new AlertDialog.Builder(MainActivity.this)
                .setCancelable(false)
                .setTitle("Are you sure?")
                .setMessage("Do you want to kill Client " + clientId + "?")
                .setPositiveButton("Yes", (dialog, which) -> killClient(clientId))
                .setNegativeButton("No", null)
                .show();
    }

    private void killClient(int clientId) {
        if (webViews.get(clientId) == null) return;

        WebView webViewToKill = webViews.get(clientId);
        FrameLayout layoutToKill = layouts.get(clientId);

        layoutToKill.removeAllViews();
        webViewToKill.destroy();
        linearLayout.removeView(layoutToKill);

        webViews.remove(clientId);
        layouts.remove(clientId);

        Toast.makeText(this, "Client " + clientId + " killed.", Toast.LENGTH_SHORT).show();

        if (webViews.size() == 0) {
            activeClientId = -1;
            setTitle("FlyffU Android");
            floatingActionButton.setVisibility(View.GONE);
            // Maybe prompt to create a new one or close the app
        } else {
            // If we killed the active client, switch to the first available one
            if (activeClientId == clientId) {
                activeClientId = webViews.keyAt(0);
                switchToClient(activeClientId);
            }
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    private void createWebViewer(WebView webView, FrameLayout frameLayout, int clientId) {
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // This is the magic: inject our Java object into the WebView's JS context
        webView.addJavascriptInterface(new LocalStorageInterface(this, clientId), "AndroidLocalStorage");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // This JS code replaces the browser's localStorage with our Android-backed version
                view.evaluateJavascript("javascript: (function() { " +
                        "window.localStorage.setItem = function(key, value) { AndroidLocalStorage.setItem(key, value); }; " +
                        "window.localStorage.getItem = function(key) { return AndroidLocalStorage.getItem(key); }; " +
                        "window.localStorage.removeItem = function(key) { AndroidLocalStorage.removeItem(key); }; " +
                        "window.localStorage.clear = function() { AndroidLocalStorage.clear(); }; " +
                        "})()", null);
            }
        });
        
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true); // Keep this true, our JS override handles it
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

    private void snapFabToEdge(View view) {
        int fabWidth = view.getWidth();
        float currentX = view.getX();
        if (currentX < (float) -fabWidth / 2) {
            currentX = (float) -fabWidth / 2;
        } else if (currentX > screenWidth - (float) fabWidth / 2) {
            currentX = screenWidth - (float) fabWidth / 2;
        }
        ObjectAnimator.ofFloat(view, "x", currentX).setDuration(200).start();
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
        for (int i = 0; i < webViews.size(); i++) {
            webViews.valueAt(i).destroy();
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
        // State saving is complex with this model.
        // The primary state (localStorage) is already persisted by TinyDB.
        // We can save the active client ID.
        outState.putInt("activeClientId", activeClientId);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // The WebViews will be recreated, but their localStorage will be correctly
        // loaded via the JavascriptInterface. We just need to restore which one was active.
        activeClientId = savedInstanceState.getInt("activeClientId", 1);
    }
}