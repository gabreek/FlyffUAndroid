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
import android.view.View;
import android.view.ViewGroup;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.view.WindowManager;
import android.widget.PopupMenu;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    WebView mClientWebView, sClientWebView;

    FrameLayout mClient, sClient;

    LinearLayout linearLayout;

    FloatingActionButton floatingActionButton;

    Boolean exit = false, isOpen = false;

    TinyDB tinyDB;

    String userAgent = System.getProperty("http.agent");

    Menu optionMenu;

    String url = "https://universe.flyff.com/play";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setTitle("FlyffU Android - Main Client");

        tinyDB = new TinyDB(this);

        // Allow content to extend into the display cutout area
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        // Ensure content is drawn behind the status bar
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Ensure full screen mode is applied on start
        fullScreenOn();

        linearLayout = findViewById(R.id.linearLayout);

        mClient = findViewById(R.id.frameLayoutMainClient);

        sClient = findViewById(R.id.frameLayoutSecondClient);

        mClientWebView = new WebView(getApplicationContext());

        sClientWebView = new WebView(getApplicationContext());

        floatingActionButton = findViewById(R.id.fab);

        floatingActionButton.setOnClickListener(view -> {
            if (sClient.getVisibility() == View.VISIBLE) {
                sClient.setVisibility(View.GONE);
                mClient.setVisibility(View.VISIBLE);
                setTitle("FlyffU Android - Main Client");
            } else if (sClient.getVisibility() == View.GONE) {
                sClient.setVisibility(View.VISIBLE);
                mClient.setVisibility(View.GONE);
                setTitle("FlyffU Android - Second Client");
            }
        });

        floatingActionButton.setOnLongClickListener(view -> {
            PopupMenu popup = new PopupMenu(MainActivity.this, view);
            popup.getMenuInflater().inflate(R.menu.main_activity_menu, popup.getMenu());

            // Add "Show Both Side by Side" option
            popup.getMenu().add(Menu.NONE, R.id.showBothClients, Menu.NONE, "Show Both Side by Side");

            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case R.id.secondClient:
                        if (sClient.getVisibility() == View.GONE && !isOpen) {
                            sClient.setVisibility(View.VISIBLE);
                            secondClient();
                            item.setTitle("Close Second Client");
                            // Update the activity's menu item as well if it exists
                            if (optionMenu != null) {
                                MenuItem secondClientMenuItem = optionMenu.findItem(R.id.secondClient);
                                if (secondClientMenuItem != null) {
                                    secondClientMenuItem.setTitle("Close Second Client");
                                }
                                MenuItem reloadSecondClientMenuItem = optionMenu.findItem(R.id.reloadSecondClient);
                                if (reloadSecondClientMenuItem != null) {
                                    reloadSecondClientMenuItem.setEnabled(true);
                                }
                            }
                            floatingActionButton.setVisibility(View.VISIBLE);
                            isOpen = true;
                        } else {
                            AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                                    .setCancelable(false)
                                    .setTitle("Are you sure you want to close the second client?")
                                    .setPositiveButton("Yes", null)
                                    .setNegativeButton("No", null)
                                    .show();
                            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                            positiveButton.setOnClickListener(v -> {
                                sClient.removeAllViews();
                                sClientWebView.loadUrl("about:blank");
                                sClient.setVisibility(View.GONE);
                                item.setTitle("Open Second Client");
                                // Update the activity's menu item as well if it exists
                                if (optionMenu != null) {
                                    MenuItem secondClientMenuItem = optionMenu.findItem(R.id.secondClient);
                                    if (secondClientMenuItem != null) {
                                        secondClientMenuItem.setTitle("Open Second Client");
                                    }
                                    MenuItem reloadSecondClientMenuItem = optionMenu.findItem(R.id.reloadSecondClient);
                                    if (reloadSecondClientMenuItem != null) {
                                        reloadSecondClientMenuItem.setEnabled(false);
                                    }
                                }
                                if (mClient.getVisibility() == View.GONE) {
                                    mClient.setVisibility(View.VISIBLE);
                                }
                                floatingActionButton.setVisibility(View.GONE);
                                isOpen = false;
                                setTitle("FlyffU Android - Main Client");
                                Toast.makeText(MainActivity.this, "Second Client closed.", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            });
                        }
                        return true;
                    case R.id.reloadMainClient:
                        mClientWebView.loadUrl(url);
                        return true;
                    case R.id.reloadSecondClient:
                        sClientWebView.loadUrl(url);
                        return true;
                    case R.id.showBothClients:
                        sClient.setVisibility(View.VISIBLE);
                        mClient.setVisibility(View.VISIBLE);
                        setTitle("FlyffU Android - Both Clients");
                        if (!isOpen) {
                            secondClient();
                            isOpen = true;
                            // Update the activity's menu item as well if it exists
                            if (optionMenu != null) {
                                MenuItem secondClientMenuItem = optionMenu.findItem(R.id.secondClient);
                                if (secondClientMenuItem != null) {
                                    secondClientMenuItem.setTitle("Close Second Client");
                                }
                                MenuItem reloadSecondClientMenuItem = optionMenu.findItem(R.id.reloadSecondClient);
                                if (reloadSecondClientMenuItem != null) {
                                    reloadSecondClientMenuItem.setEnabled(true);
                                }
                            }
                        }
                        return true;
                }
                return false;
            });
            popup.show();
            return true;
        });

        mainClient();

        if (savedInstanceState == null) {
            mClientWebView.loadUrl(url);
            sClientWebView.loadUrl(url);
        }
    }

    @Override
    public void onBackPressed() {
        if (exit) {
            finish();
        } else {
            Toast.makeText(this, "Press Back again to Exit.",
                    Toast.LENGTH_SHORT).show();
            exit = true;
            new Handler().postDelayed(() -> exit = false, 3 * 1000);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mClient.removeAllViews();
        sClient.removeAllViews();
        mClientWebView.destroy();
        sClientWebView.destroy();
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // Options are now handled by the FAB's long click PopupMenu
        return super.onOptionsItemSelected(item);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_menu, menu);

        optionMenu = menu;

        return super.onCreateOptionsMenu(menu);
    }

    private void fullScreenOn() {
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
            windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        Objects.requireNonNull(getSupportActionBar()).hide();
    }

    private void fullScreenOff() {
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
        }
        Objects.requireNonNull(getSupportActionBar()).show();
    }

    private void mainClient() {

        createWebViewer(mClientWebView, mClient);
    }

    private void secondClient() {

        createWebViewer(sClientWebView, sClient);
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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mClientWebView.saveState(outState);
        sClientWebView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mClientWebView.restoreState(savedInstanceState);
        sClientWebView.saveState(savedInstanceState);
    }
}