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
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final int WIKI_CLIENT_ID = -4;
    private static final int MADRIGAL_CLIENT_ID = -2;
    private static final int FLYFFULATOR_CLIENT_ID = -3;

    private static final String WIKI_URL = "https://flyff-wiki.gpotato.com.br/wiki/Main_Page";
    private static final String MADRIGAL_URL = "https://madrigalinside.com/";
    private static final String FLYFFULATOR_URL = "https://flyffulator.com/";

    private static final int MAX_CLIENTS = 10; // Let's allow up to 10 slots
    private static final String CLIENT_NAME_KEY = "client_custom_name";

    private SparseArray<WebView> webViews = new SparseArray<>();
    private SparseArray<FrameLayout> layouts = new SparseArray<>();
    private int activeClientId = -1;
    private Set<Integer> configuredClientIds = new HashSet<>(); // In-memory tracking of configured clients

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

    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void showKeyboard() {
            InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
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

        // Initialize configuredClientIds from existing files on startup
        configuredClientIds.addAll(getExistingClientIdsFromFileSystem());

        if (savedInstanceState == null) {
            // If there are no configs, create a fresh client. Otherwise, open the first available one.
            if (configuredClientIds.isEmpty()) {
                createNewClient();
            } else {
                // Open the first configured client if it's not already open
                int firstClientId = Collections.min(configuredClientIds);
                if (webViews.get(firstClientId) == null) {
                    openClient(firstClientId);
                } else {
                    switchToClient(firstClientId);
                }
            }
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

        List<Integer> sortedClientIds = new ArrayList<>(configuredClientIds);
        Collections.sort(sortedClientIds);

        if (!sortedClientIds.isEmpty()) {
            SubMenu clientsMenu = popup.getMenu().addSubMenu(Menu.NONE, 2, Menu.NONE, "Clients");
            for (int clientId : sortedClientIds) {
                String displayName = getClientDisplayName(clientId);
                SubMenu clientSubMenu = clientsMenu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, displayName);

                boolean isClientOpen = webViews.get(clientId) != null;

                if (isClientOpen) {
                    clientSubMenu.add(Menu.NONE, 1000 + clientId, 1, "Switch to");
                    clientSubMenu.add(Menu.NONE, 2000 + clientId, 2, "Kill");
                } else {
                    clientSubMenu.add(Menu.NONE, 3000 + clientId, 1, "Open");
                }
                clientSubMenu.add(Menu.NONE, 4000 + clientId, 3, "Rename");
                clientSubMenu.add(Menu.NONE, 5000 + clientId, 4, "Delete"); // New Delete option
            }
        }

        // Add Utils Menu
        SubMenu utilsMenu = popup.getMenu().addSubMenu(Menu.NONE, 3, Menu.NONE, "Utils");
        utilsMenu.add(Menu.NONE, 7000 + Math.abs(WIKI_CLIENT_ID), Menu.NONE, "Flyff Wiki");
        utilsMenu.add(Menu.NONE, 7000 + Math.abs(MADRIGAL_CLIENT_ID), Menu.NONE, "Madrigal Inside");
        utilsMenu.add(Menu.NONE, 7000 + Math.abs(FLYFFULATOR_CLIENT_ID), Menu.NONE, "Flyffulator");

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            int clientId = -1;

            if (itemId > 1000) {
                if (itemId < 2000) clientId = itemId - 1000; // Switch
                else if (itemId < 3000) clientId = itemId - 2000; // Kill
                else if (itemId < 4000) clientId = itemId - 3000; // Open
                else if (itemId < 5000) clientId = itemId - 4000; // Rename
                else if (itemId < 6000) clientId = itemId - 5000; // Delete
                else if (itemId < 8000) clientId = -(itemId - 7000); // Utils (New range 7000-7999)
            }

            if (itemId == 1) { // New Client
                createNewClient();
                return true;
            } else if (clientId != -1) {
                if (itemId >= 1000 && itemId < 2000) { // Switch to
                    switchToClient(clientId);
                    return true;
                } else if (itemId >= 2000 && itemId < 3000) { // Kill
                    confirmKillClient(clientId);
                    return true;
                } else if (itemId >= 3000 && itemId < 4000) { // Open
                    openClient(clientId);
                    return true;
                } else if (itemId >= 4000 && itemId < 5000) { // Rename
                    showRenameDialog(clientId);
                    return true;
                } else if (itemId >= 5000 && itemId < 6000) { // Delete
                    confirmDeleteClient(clientId);
                    return true;
                } else if (itemId >= 7000 && itemId < 8000) { // Utils (New range 7000-7999)
                    openUtilityClient(clientId);
                    return true;
                }
            }
            return false;
        });
        popup.show();
    }

    private void showRenameDialog(int clientId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename Client");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(getClientDisplayName(clientId));
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                TinyDB db = new TinyDB(this, "client_prefs_" + clientId);
                db.putString(CLIENT_NAME_KEY, newName);
                Toast.makeText(this, "Client renamed to " + newName, Toast.LENGTH_SHORT).show();
                if (activeClientId == clientId) {
                    setTitle(getClientDisplayName(clientId));
                }
            } else {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    // This method now only reads from the file system, used for initial population
    private List<Integer> getExistingClientIdsFromFileSystem() {
        List<Integer> ids = new ArrayList<>();
        File prefsDir = new File(getApplicationInfo().dataDir, "shared_prefs");
        if (prefsDir.exists() && prefsDir.isDirectory()) {
            Pattern p = Pattern.compile("client_prefs_(\\d+)\\.xml");
            File[] files = prefsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    Matcher m = p.matcher(file.getName());
                    if (m.matches()) {
                        try {
                            int id = Integer.parseInt(m.group(1));
                            ids.add(id);
                        } catch (NumberFormatException e) {
                            // Ignore if parsing fails
                        }
                    }
                }
            }
        }
        return ids;
    }

    private String getClientDisplayName(int clientId) {
        if (clientId == WIKI_CLIENT_ID) return "Flyff Wiki";
        if (clientId == MADRIGAL_CLIENT_ID) return "Madrigal Inside";
        if (clientId == FLYFFULATOR_CLIENT_ID) return "Flyffulator";

        TinyDB db = new TinyDB(this, "client_prefs_" + clientId);
        String customName = db.getString(CLIENT_NAME_KEY);
        String displayName = customName != null && !customName.isEmpty() ? customName : "Client " + clientId;
        return displayName;
    }

    private void openUtilityClient(int clientId) {
        String targetUrl;
        switch (clientId) {
            case WIKI_CLIENT_ID:
                targetUrl = WIKI_URL;
                break;
            case MADRIGAL_CLIENT_ID:
                targetUrl = MADRIGAL_URL;
                break;
            case FLYFFULATOR_CLIENT_ID:
                targetUrl = FLYFFULATOR_URL;
                break;
            default:
                Toast.makeText(this, "Unknown utility client.", Toast.LENGTH_SHORT).show();
                return;
        }

        if (webViews.get(clientId) != null) { // Already open
            if (activeClientId == clientId) {
                // If it's already the active client, ask to close it
                confirmCloseUtilityClient(clientId);
            } else {
                switchToClient(clientId);
            }
            return;
        }

        if (webViews.size() >= MAX_CLIENTS) {
            Toast.makeText(this, "Max open clients reached. Cannot open more.", Toast.LENGTH_SHORT).show();
            return;
        }

        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        linearLayout.addView(frameLayout);
        layouts.put(clientId, frameLayout);

        WebView webView = new CustomWebView(getApplicationContext());
        createWebViewer(webView, frameLayout, clientId, targetUrl);
        webViews.put(clientId, webView);

        switchToClient(clientId);
        floatingActionButton.setVisibility(View.VISIBLE);
        Toast.makeText(this, getClientDisplayName(clientId) + " opened.", Toast.LENGTH_SHORT).show();
    }

    private void createNewClient() {
        List<Integer> openIds = new ArrayList<>();
        for (int i = 0; i < webViews.size(); i++) {
            openIds.add(webViews.keyAt(i));
        }

        // 1. Try to open an existing but currently closed client
        for (int clientId : configuredClientIds) {
            if (!openIds.contains(clientId)) {
                if (webViews.size() >= MAX_CLIENTS) {
                    Toast.makeText(this, "Max open clients reached. Cannot open more.", Toast.LENGTH_SHORT).show();
                    return;
                }
                openClient(clientId);
                Toast.makeText(this, getClientDisplayName(clientId) + " opened.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // 2. If all existing clients are open, or no clients exist, create a new one
        if (webViews.size() >= MAX_CLIENTS) {
            Toast.makeText(this, "Max open clients reached. Cannot create new client.", Toast.LENGTH_SHORT).show();
            return;
        }

        int newClientId = -1;
        for (int i = 1; i <= MAX_CLIENTS; i++) {
            if (!configuredClientIds.contains(i)) { // Check against all configured IDs
                newClientId = i;
                break;
            }
        }

        if (newClientId == -1) {
            Toast.makeText(this, "Could not find an available client slot.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Add the new client ID to our in-memory set immediately
        configuredClientIds.add(newClientId);

        openClient(newClientId);
        Toast.makeText(this, getClientDisplayName(newClientId) + " created.", Toast.LENGTH_SHORT).show();
    }

    private void openClient(int clientId) {
        if (webViews.get(clientId) != null) { // Already open
            switchToClient(clientId);
            return;
        }

        if (webViews.size() >= MAX_CLIENTS) {
            Toast.makeText(this, "Max open clients reached", Toast.LENGTH_SHORT).show();
            return;
        }

        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        linearLayout.addView(frameLayout);
        layouts.put(clientId, frameLayout);

        WebView webView = new CustomWebView(getApplicationContext());
        createWebViewer(webView, frameLayout, clientId, url);
        webViews.put(clientId, webView);

        switchToClient(clientId);
        floatingActionButton.setVisibility(View.VISIBLE);
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
        } else {
            // Only one or no clients open. No switch needed.
        }
    }

    private void switchToClient(int clientId) {
        if (layouts.get(clientId) == null) {
            // This might happen if we try to switch to a client that was just killed
            // but the switch command was already in-flight.
            if (webViews.size() > 0) {
                int fallbackClientId = webViews.keyAt(0);
                switchToClient(fallbackClientId);
            } else {
                activeClientId = -1;
                setTitle("FlyffU Android");
                floatingActionButton.setVisibility(View.GONE);
            }
            return;
        }

        for (int i = 0; i < layouts.size(); i++) {
            int key = layouts.keyAt(i);
            layouts.get(key).setVisibility(key == clientId ? View.VISIBLE : View.GONE);
        }
        activeClientId = clientId;
        setTitle(getClientDisplayName(activeClientId));
    }

    private void confirmKillClient(int clientId) {
        if (webViews.get(clientId) == null) return;

        new AlertDialog.Builder(MainActivity.this)
                .setCancelable(false)
                .setTitle("Are you sure?")
                .setMessage("Do you want to kill " + getClientDisplayName(clientId) + "?")
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

        Toast.makeText(this, getClientDisplayName(clientId) + " killed.", Toast.LENGTH_SHORT).show();

        if (webViews.size() == 0) {
            activeClientId = -1;
            setTitle("FlyffU Android");
            floatingActionButton.setVisibility(View.GONE);
        } else {
            if (activeClientId == clientId) {
                activeClientId = webViews.keyAt(0);
                switchToClient(activeClientId);
            }
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    private void createWebViewer(WebView webView, FrameLayout frameLayout, int clientId, String initialUrl) {
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // This is the magic: inject our Java object into the WebView's JS context
        webView.addJavascriptInterface(new LocalStorageInterface(this, clientId), "AndroidLocalStorage");
        webView.addJavascriptInterface(new WebAppInterface(this), "FlyffUAndroid");

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
                        // Detectar foco em inputs e textareas
                        "var inputs = document.getElementsByTagName('input');" +
                        "var textareas = document.getElementsByTagName('textarea');" +
                        "var elements = Array.from(inputs).concat(Array.from(textareas));" +
                        "for (var i = 0; i < elements.length; i++) {" +
                        "  elements[i].addEventListener('focus', function() {" +
                        "    window.FlyffUAndroid.showKeyboard();" +
                        "  });" +
                        "}" +
                        // Detectar cliques no canvas
                        "var canvas = document.getElementsByTagName('canvas')[0];" +
                        "if (canvas) {" +
                        "  canvas.addEventListener('click', function() {" +
                        "    var input = document.createElement('input');" +
                        "    input.type = 'text';" +
                        "    input.style.position = 'absolute';" +
                        "    input.style.opacity = '0';" +
                        "    document.body.appendChild(input);" +
                        "    input.focus();" +
                        "    window.FlyffUAndroid.showKeyboard();" +
                        "  });" +
                        "}" +
                        "})()", null);
            }
        });

        // Add WebChromeClient to handle UI-related events, including keyboard
        webView.setWebChromeClient(new WebChromeClient());
        
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true); // Keep this true, our JS override handles it
        webSettings.setAppCacheEnabled(true);
        webSettings.setAppCachePath(getApplicationContext().getCacheDir().getAbsolutePath());
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUserAgentString(userAgent);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null); // Corrected: webView.setLayerType
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        frameLayout.addView(webView);
        webView.requestFocus(); // Request focus for the WebView

        // Add a touch listener to explicitly request focus and show keyboard on touch
        webView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
                }
            }
            return false; // Allow other touch events to be processed
        });

        webView.loadUrl(initialUrl);
    }

    private void createWebViewer(WebView webView, FrameLayout frameLayout, int clientId) {
        createWebViewer(webView, frameLayout, clientId, url);
    }

    private void confirmCloseUtilityClient(int clientId) {
        new AlertDialog.Builder(MainActivity.this)
                .setCancelable(false)
                .setTitle("Close Utility?")
                .setMessage("Do you want to close " + getClientDisplayName(clientId) + "?")
                .setPositiveButton("Yes", (dialog, which) -> closeUtilityClient(clientId))
                .setNegativeButton("No", null)
                .show();
    }

    private void closeUtilityClient(int clientId) {
        if (webViews.get(clientId) == null) return;

        WebView webViewToClose = webViews.get(clientId);
        FrameLayout layoutToClose = layouts.get(clientId);

        layoutToClose.removeAllViews();
        webViewToClose.destroy();
        linearLayout.removeView(layoutToClose);

        webViews.remove(clientId);
        layouts.remove(clientId);

        Toast.makeText(this, getClientDisplayName(clientId) + " closed.", Toast.LENGTH_SHORT).show();

        if (webViews.size() == 0) {
            activeClientId = -1;
            setTitle("FlyffU Android");
            floatingActionButton.setVisibility(View.GONE);
        } else {
            activeClientId = webViews.keyAt(0);
            switchToClient(activeClientId);
        }
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
        }
        else {
            Toast.makeText(this, "Press Back again to Exit.", Toast.LENGTH_SHORT).show();
            exit = true;
            new Handler().postDelayed(() -> exit = false, 3 * 1000);
        }
    }

    private void confirmDeleteClient(int clientId) {
        new AlertDialog.Builder(MainActivity.this)
                .setCancelable(false)
                .setTitle("Confirm Deletion")
                .setMessage("Are you sure you want to delete " + getClientDisplayName(clientId) + "? This will remove all its data.")
                .setPositiveButton("Yes", (dialog, which) -> deleteClient(clientId))
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteClient(int clientId) {
        // Kill the client if it's open
        if (webViews.get(clientId) != null) {
            killClient(clientId);
        }

        // Remove from configuredClientIds
        configuredClientIds.remove(clientId);

        // Delete the TinyDB file
        TinyDB db = new TinyDB(this, "client_prefs_" + clientId);
        db.clear(); // Clear all preferences
        File prefsFile = new File(getApplicationInfo().dataDir, "shared_prefs/client_prefs_" + clientId + ".xml");
        if (prefsFile.exists()) {
            prefsFile.delete();
        }

        Toast.makeText(this, "Client " + clientId + " and its data deleted.", Toast.LENGTH_SHORT).show();

        // If no clients are left, create a new one
        if (configuredClientIds.isEmpty()) {
            createNewClient();
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
        outState.putInt("activeClientId", activeClientId);
        // Client configurations are saved in TinyDB, so they persist automatically.
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // Don't automatically restore all webviews to avoid heavy load on startup.
        // The user can reopen them from the menu. We just restore the active one.
        activeClientId = savedInstanceState.getInt("activeClientId", -1);
        if (activeClientId != -1) {
            openClient(activeClientId);
        }
    }
}