package com.droidev.flyffuwebviewclient;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    private static final int MAX_CLIENTS = 10;
    private static final String CLIENT_NAME_KEY = "client_custom_name";

    private final SparseArray<WebView> webViews = new SparseArray<>();
    private final SparseArray<FrameLayout> layouts = new SparseArray<>();
    private int activeClientId = -1;
    private final Set<Integer> configuredClientIds = new HashSet<>();

    private LinearLayout linearLayout;
    private FloatingActionButton floatingActionButton;
    private FrameLayout rootContainer;
    private final Map<View, WebView> customFabMap = new HashMap<>();
    private final Map<String, Integer> keyCodeMap = new HashMap<>();


    private boolean exit = false;
    private final String userAgent = System.getProperty("http.agent");
    private final String url = "https://universe.flyff.com/play";

    /* FAB movement */
    private int _xDelta;
    private int _yDelta;
    private int screenWidth;
    private int screenHeight;
    private long downTime;
    private float initialRawX;
    private float initialRawY;
    private final Handler longPressHandler = new Handler();
    private Runnable longPressRunnable;

    private int dpToPx(int dp) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    /* JS
 Android bridges */
    public static class LocalStorageInterface {
        private final TinyDB db;
        LocalStorageInterface(Context context, int clientId) {
            this.db = new TinyDB(context, "client_prefs_" + clientId);
        }
        @JavascriptInterface public void setItem(String k, String v) { db.putString(k, v); }
        @JavascriptInterface public String getItem(String k) { return db.getString(k); }
        @JavascriptInterface public void removeItem(String k) { db.remove(k); }
        @JavascriptInterface public void clear() { db.clear(); }
    }

    public static class WebAppInterface {
        private final Context mContext;
        WebAppInterface(Context c) { mContext = c; }
        @JavascriptInterface public void showKeyboard() {
            InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
        }
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("FlyffU Android");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        fullScreenOn();

        rootContainer = findViewById(R.id.root_container);
        linearLayout   = findViewById(R.id.linearLayout);
        floatingActionButton = findViewById(R.id.fab);
        floatingActionButton.setAlpha(0.5f);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        screenWidth  = dm.widthPixels;
        screenHeight = dm.heightPixels;

        initializeKeyCodeMap();
        setupFabTouchListener();

        configuredClientIds.addAll(getExistingClientIdsFromFileSystem());

        if (savedInstanceState == null) {
            if (configuredClientIds.isEmpty()) {
                createNewClient();
            } else {
                int first = Collections.min(configuredClientIds);
                if (webViews.get(first) == null) openClient(first);
                else switchToClient(first);
            }
        }
    }

    /* ---------- WebView creation with .bin caching ---------- */
    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    private void createWebViewer(WebView webView, FrameLayout frameLayout, int clientId, String initialUrl) {
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        webView.addJavascriptInterface(new LocalStorageInterface(this, clientId), "AndroidLocalStorage");
        webView.addJavascriptInterface(new WebAppInterface(this), "FlyffUAndroid");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                /* localStorage override */
                view.evaluateJavascript(
                        "(function(){"
                        + "window.localStorage.setItem=function(k,v){AndroidLocalStorage.setItem(k,v)};"
                        + "window.localStorage.getItem=function(k){return AndroidLocalStorage.getItem(k)};"
                        + "window.localStorage.removeItem=function(k){AndroidLocalStorage.removeItem(k)};"
                        + "window.localStorage.clear=function(){AndroidLocalStorage.clear()};"
                        + "})()", null);

                /* .bin caching via IndexedDB */
                view.evaluateJavascript(
                        "(function(){"
                        + "const BIN=/\\.bin$/i,IDB_NAME='flyff_bin_cache',STORE='blobs',VER=1;"
                        + "let db;"
                        + "const openDb=()=>new Promise((res,rej)=>{"
                        + "  const r=indexedDB.open(IDB_NAME,VER);"
                        + "  r.onupgradeneeded=()=>r.result.createObjectStore(STORE);"
                        + "  r.onsuccess=()=>res(r.result);"
                        + "  r.onerror=()=>rej(r.error);"
                        + "});"
                        + "const key=u=>{try{return new URL(u).origin+new URL(u).pathname}catch{return u.split('?')[0]}};"
                        + "const get=u=>openDb().then(d=>d.transaction(STORE,'readonly').objectStore(STORE).get(key(u)));"
                        + "const put=(u,b)=>openDb().then(d=>d.transaction(STORE,'readwrite').objectStore(STORE).put(b,key(u)));"
                        + "const Native=XMLHttpRequest;"
                        + "window.XMLHttpRequest=function(){"
                        + "  const xhr=new Native,open=xhr.open,send=xhr.send;"
                        + "  xhr.open=function(m,u,...a){"
                        + "    this._url=u;this._bin=BIN.test(u);"
                        + "    return open.call(this,m,u,...a);"
                        + "  };"
                        + "  xhr.send=function(...a){"
                        + "    if(!this._bin)return send.apply(this,a);"
                        + "    const u=this._url;"
                        + "    get(u).then(blob=>{"
                        + "      if(blob){"
                        + "        ['response','responseText','readyState','status','statusText'].forEach(p=>Object.defineProperty(xhr,p,{writable:true}));"
                        + "        xhr.response=blob;xhr.responseText='';xhr.readyState=4;xhr.status=200;xhr.statusText='OK';"
                        + "        if(xhr.onreadystatechange)xhr.onreadystatechange();"
                        + "        if(xhr.onload)xhr.onload();"
                        + "        return;"
                        + "      }"
                        + "      xhr.addEventListener('load',()=>{"
                        + "        if(xhr.status===200&&xhr.response instanceof Blob)put(u,xhr.response);"
                        + "      });"
                        + "      send.apply(xhr,a);"
                        + "    });"
                        + "  };"
                        + "  return xhr;"
                        + "};"
                        + "})()", null);
            }
        });

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAppCacheEnabled(true);
        s.setAppCachePath(getCacheDir().getAbsolutePath());
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setUserAgentString(userAgent);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);

        frameLayout.addView(webView);
        webView.requestFocus();
        webView.loadUrl(initialUrl);
    }

    /* ---------- rest of the file unchanged ---------- */

    @SuppressLint("ClickableViewAccessibility")
    private void setupFabTouchListener() {
        longPressRunnable = () -> floatingActionButton.performLongClick();
        floatingActionButton.setOnTouchListener((v, e) -> {
            int X = (int) e.getRawX(), Y = (int) e.getRawY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downTime = e.getDownTime();
                    initialRawX = e.getRawX();
                    initialRawY = e.getRawY();
                    _xDelta = X - (int) v.getX();
                    _yDelta = Y - (int) v.getY();
                    longPressHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                    return true;
                case MotionEvent.ACTION_UP:
                    longPressHandler.removeCallbacks(longPressRunnable);
                    long dur = e.getEventTime() - downTime;
                    float dx = Math.abs(e.getRawX() - initialRawX);
                    float dy = Math.abs(e.getRawY() - initialRawY);
                    int slop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                    if (dx < slop && dy < slop && dur < ViewConfiguration.getLongPressTimeout())
                        v.performClick();
                    else snapFabToEdge(v);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getRawX() - initialRawX) > ViewConfiguration.get(v.getContext()).getScaledTouchSlop()
                            || Math.abs(e.getRawY() - initialRawY) > ViewConfiguration.get(v.getContext()).getScaledTouchSlop())
                        longPressHandler.removeCallbacks(longPressRunnable);
                    v.setX(X - _xDelta);
                    v.setY(Y - _yDelta);
                    return true;
            }
            return false;
        });
        floatingActionButton.setOnClickListener(v -> switchToNextClient());
        floatingActionButton.setOnLongClickListener(v -> {
            showClientManagerMenu(v);
            return true;
        });
    }

    private void snapFabToEdge(View v) {
        int w = v.getWidth();
        float x = v.getX();
        if (x < -w / 2f) x = -w / 2f;
        if (x > screenWidth - w / 2f) x = screenWidth - w / 2f;
        ObjectAnimator.ofFloat(v, "x", x).setDuration(200).start();
    }

    /* ---------- client helper methods ---------- */

    private void openClient(int id) {
        if (webViews.get(id) != null) { switchToClient(id); return; }
        if (webViews.size() >= MAX_CLIENTS) {
            Toast.makeText(this, "Max clients open", Toast.LENGTH_SHORT).show();
            return;
        }
        FrameLayout fl = new FrameLayout(this);
        fl.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        linearLayout.addView(fl);
        layouts.put(id, fl);

        WebView w = new CustomWebView(getApplicationContext());
        createWebViewer(w, fl, id, url);
        webViews.put(id, w);
        switchToClient(id);
        floatingActionButton.setVisibility(View.VISIBLE);
    }

    private void switchToClient(int id) {
        if (layouts.get(id) == null) {
            if (webViews.size() > 0) switchToClient(webViews.keyAt(0));
            else {
                activeClientId = -1;
                setTitle("FlyffU Android");
                floatingActionButton.setVisibility(View.GONE);
            }
            return;
        }
        for (int i = 0; i < layouts.size(); i++) {
            int k = layouts.keyAt(i);
            layouts.get(k).setVisibility(k == id ? View.VISIBLE : View.GONE);
        }
        activeClientId = id;
        setTitle(getClientDisplayName(id));
        WebView w = webViews.get(id);
        if (w != null) {
            w.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(w.getWindowToken(), 0);
        }
    }

    private void switchToNextClient() {
        if (webViews.size() > 1) {
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < webViews.size(); i++) ids.add(webViews.keyAt(i));
            Collections.sort(ids);
            int idx = (ids.indexOf(activeClientId) + 1) % ids.size();
            switchToClient(ids.get(idx));
        }
    }

    private void killClient(int id) {
        if (webViews.get(id) == null) return;
        WebView w = webViews.get(id);
        FrameLayout f = layouts.get(id);
        f.removeAllViews();
        w.destroy();
        linearLayout.removeView(f);
        webViews.remove(id);
        layouts.remove(id);
        Toast.makeText(this, getClientDisplayName(id) + " killed", Toast.LENGTH_SHORT).show();
        if (webViews.size() == 0) {
            activeClientId = -1;
            setTitle("FlyffU Android");
            floatingActionButton.setVisibility(View.GONE);
        } else if (activeClientId == id) {
            activeClientId = webViews.keyAt(0);
            switchToClient(activeClientId);
        }
    }

    private void deleteClient(int id) {
        if (webViews.get(id) != null) killClient(id);
        configuredClientIds.remove(id);
        TinyDB db = new TinyDB(this, "client_prefs_" + id);
        db.clear();
        File f = new File(getApplicationInfo().dataDir, "shared_prefs/client_prefs_" + id + ".xml");
        if (f.exists()) f.delete();
        Toast.makeText(this, "Client " + id + " deleted", Toast.LENGTH_SHORT).show();
        if (configuredClientIds.isEmpty()) createNewClient();
    }

    private List<Integer> getExistingClientIdsFromFileSystem() {
        List<Integer> ids = new ArrayList<>();
        File dir = new File(getApplicationInfo().dataDir, "shared_prefs");
        if (dir.exists() && dir.isDirectory()) {
            Pattern p = Pattern.compile("client_prefs_(\\d+)\\.xml");
            File[] fs = dir.listFiles();
            if (fs != null)
                for (File f : fs) {
                    Matcher m = p.matcher(f.getName());
                    if (m.matches()) try {
                        ids.add(Integer.parseInt(m.group(1)));
                    } catch (NumberFormatException ignore) {}
                }
        }
        return ids;
    }

    private String getClientDisplayName(int id) {
        if (id == WIKI_CLIENT_ID) return "Flyff Wiki";
        if (id == MADRIGAL_CLIENT_ID) return "Madrigal Inside";
        if (id == FLYFFULATOR_CLIENT_ID) return "Flyffulator";
        TinyDB db = new TinyDB(this, "client_prefs_" + id);
        String custom = db.getString(CLIENT_NAME_KEY);
        return (custom != null && !custom.isEmpty()) ? custom : "Client " + id;
    }

    /* ---------- FAB long-press menu ---------- */
    private void showClientManagerMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(Menu.NONE, 1, Menu.NONE, "New Client");

        List<Integer> sorted = new ArrayList<>(configuredClientIds);
        Collections.sort(sorted);
        if (!sorted.isEmpty()) {
            SubMenu sub = popup.getMenu().addSubMenu(Menu.NONE, 2, Menu.NONE, "Clients");
            for (int id : sorted) {
                SubMenu sm = sub.addSubMenu(getClientDisplayName(id));
                boolean open = webViews.get(id) != null;
                if (open) {
                    sm.add(Menu.NONE, 6000 + id, 5, "Action Buttons");
                    sm.add(Menu.NONE, 1000 + id, 1, "Switch");
                    sm.add(Menu.NONE, 2000 + id, 2, "Kill");
                } else sm.add(Menu.NONE, 3000 + id, 1, "Open");
                sm.add(Menu.NONE, 4000 + id, 3, "Rename");
                sm.add(Menu.NONE, 5000 + id, 4, "Delete");

            }
        }
        SubMenu util = popup.getMenu().addSubMenu(Menu.NONE, 3, Menu.NONE, "Utils");
        util.add(Menu.NONE, 7000 + Math.abs(WIKI_CLIENT_ID), Menu.NONE, "Flyff Wiki");
        util.add(Menu.NONE, 7000 + Math.abs(MADRIGAL_CLIENT_ID), Menu.NONE, "Madrigal Inside");
        util.add(Menu.NONE, 7000 + Math.abs(FLYFFULATOR_CLIENT_ID), Menu.NONE, "Flyffulator");

        popup.setOnMenuItemClickListener(item -> {
            int id = -1, itemId = item.getItemId();
            if (itemId > 1000) {
                if (itemId < 2000) id = itemId - 1000;
                else if (itemId < 3000) id = itemId - 2000;
                else if (itemId < 4000) id = itemId - 3000;
                else if (itemId < 5000) id = itemId - 4000;
                else if (itemId < 6000) id = itemId - 5000;
                else if (itemId < 7000) id = itemId - 6000;
                else if (itemId < 8000) id = -(itemId - 7000);
            }
            if (itemId == 1) createNewClient();
            else if (id != -1) {
                if (itemId >= 1000 && itemId < 2000) switchToClient(id);
                else if (itemId >= 2000 && itemId < 3000) confirmKillClient(id);
                else if (itemId >= 3000 && itemId < 4000) openClient(id);
                else if (itemId >= 4000 && itemId < 5000) showRenameDialog(id);
                else if (itemId >= 5000 && itemId < 6000) confirmDeleteClient(id);
                else if (itemId >= 6000 && itemId < 7000) {
                    if (webViews.get(id) == null) {
                        openClient(id);
                    } else {
                        switchToClient(id);
                    }
                    showActionButtonsMenu();
                }
                else if (itemId >= 7000 && itemId < 8000) openUtilityClient(id);
            }
            return true;
        });
        popup.show();
    }

    private void createNewClient() {
        List<Integer> open = new ArrayList<>();
        for (int i = 0; i < webViews.size(); i++) open.add(webViews.keyAt(i));
        for (int id : configuredClientIds) {
            if (!open.contains(id)) {
                if (webViews.size() >= MAX_CLIENTS) {
                    Toast.makeText(this, "Max open clients reached", Toast.LENGTH_SHORT).show();
                    return;
                }
                openClient(id);
                Toast.makeText(this, getClientDisplayName(id) + " opened", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if (webViews.size() >= MAX_CLIENTS) {
            Toast.makeText(this, "Max clients reached", Toast.LENGTH_SHORT).show();
            return;
        }
        int newId = 1;
        while (configuredClientIds.contains(newId) && newId <= MAX_CLIENTS) newId++;
        if (newId > MAX_CLIENTS) {
            Toast.makeText(this, "No free slot", Toast.LENGTH_SHORT).show();
            return;
        }
        configuredClientIds.add(newId);
        openClient(newId);
        Toast.makeText(this, "Client " + newId + " created", Toast.LENGTH_SHORT).show();
    }

    private void openUtilityClient(int id) {
        String u;
        switch (id) {
            case WIKI_CLIENT_ID: u = WIKI_URL; break;
            case MADRIGAL_CLIENT_ID: u = MADRIGAL_URL; break;
            case FLYFFULATOR_CLIENT_ID: u = FLYFFULATOR_URL; break;
            default: return;
        }
        if (webViews.get(id) != null) {
            if (activeClientId == id) confirmCloseUtilityClient(id);
            else switchToClient(id);
            return;
        }
        if (webViews.size() >= MAX_CLIENTS) {
            Toast.makeText(this, "Max clients reached", Toast.LENGTH_SHORT).show();
            return;
        }
        FrameLayout fl = new FrameLayout(this);
        fl.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        linearLayout.addView(fl);
        layouts.put(id, fl);

        WebView w = new CustomWebView(getApplicationContext());
        createWebViewer(w, fl, id, u);
        webViews.put(id, w);
        switchToClient(id);
        floatingActionButton.setVisibility(View.VISIBLE);
        Toast.makeText(this, getClientDisplayName(id) + " opened", Toast.LENGTH_SHORT).show();
    }

    private void confirmKillClient(int id) {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("Kill?")
                .setMessage("Kill " + getClientDisplayName(id) + "?")
                .setPositiveButton("Yes", (d, w) -> killClient(id))
                .setNegativeButton("No", null)
                .show();
    }

    private void confirmDeleteClient(int id) {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("Delete?")
                .setMessage("Delete " + getClientDisplayName(id) + "? This is permanent.")
                .setPositiveButton("Yes", (d, w) -> deleteClient(id))
                .setNegativeButton("No", null)
                .show();
    }

    private void confirmCloseUtilityClient(int id) {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("Close?")
                .setMessage("Close " + getClientDisplayName(id) + "?")
                .setPositiveButton("Yes", (d, w) -> closeUtilityClient(id))
                .setNegativeButton("No", null)
                .show();
    }

    private void closeUtilityClient(int id) {
        if (webViews.get(id) == null) return;
        WebView w = webViews.get(id);
        FrameLayout f = layouts.get(id);
        f.removeAllViews();
        w.destroy();
        linearLayout.removeView(f);
        webViews.remove(id);
        layouts.remove(id);
        Toast.makeText(this, getClientDisplayName(id) + " closed", Toast.LENGTH_SHORT).show();
        if (webViews.size() == 0) {
            activeClientId = -1;
            setTitle("FlyffU Android");
            floatingActionButton.setVisibility(View.GONE);
        } else {
            activeClientId = webViews.keyAt(0);
            switchToClient(activeClientId);
        }
    }

    private void showRenameDialog(int id) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Rename Client");
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(getClientDisplayName(id));
        b.setView(input);
        b.setPositiveButton("Save", (d, w) -> {
            String n = input.getText().toString().trim();
            if (!n.isEmpty()) {
                TinyDB db = new TinyDB(this, "client_prefs_" + id);
                db.putString(CLIENT_NAME_KEY, n);
                Toast.makeText(this, "Renamed to " + n, Toast.LENGTH_SHORT).show();
                if (activeClientId == id) setTitle(n);
            } else Toast.makeText(this, "Empty name", Toast.LENGTH_SHORT).show();
        });
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    /* ---------- Action Buttons methods ---------- */

    private void initializeKeyCodeMap() {
        for (int i = 0; i < 12; i++) {
            keyCodeMap.put("F" + (i + 1), KeyEvent.KEYCODE_F1 + i);
        }
        // Add other keys as needed
        keyCodeMap.put("A", KeyEvent.KEYCODE_A);
        keyCodeMap.put("B", KeyEvent.KEYCODE_B);
        // ... and so on
    }

    private void showActionButtonsMenu() {
        final CharSequence[] items = {"Create new AB", "Delete all ABs"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Action Button Configuration");
        builder.setItems(items, (dialog, item) -> {
            if (items[item].equals("Create new AB")) {
                showKeyTypeDialog();
            } else if (items[item].equals("Delete all ABs")) {
                deleteAllCustomFabs();
            }
        });
        builder.show();
    }

    private void showKeyTypeDialog() {
        final CharSequence[] items = {"Function Key", "Custom Key"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Key Type");
        builder.setItems(items, (dialog, item) -> {
            if (items[item].equals("Function Key")) {
                showFunctionKeyDialog();
            } else if (items[item].equals("Custom Key")) {
                showCustomKeyDialog();
            }
        });
        builder.show();
    }

    private void showFunctionKeyDialog() {
        final CharSequence[] fKeys = new CharSequence[12];
        for (int i = 0; i < 12; i++) {
            fKeys[i] = "F" + (i + 1);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Function Key");
        builder.setItems(fKeys, (dialog, item) -> {
            String key = fKeys[item].toString();
            createCustomFab(key, keyCodeMap.get(key));
        });
        builder.show();
    }

    private void showCustomKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Custom Key");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton("OK", (dialog, which) -> {
            String key = input.getText().toString().toUpperCase();
            if (key.length() == 1) {
                int keyCode = KeyEvent.keyCodeFromString("KEYCODE_" + key);
                if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                    createCustomFab(key, keyCode);
                } else {
                    Toast.makeText(this, "Invalid key", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Please enter a single character", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void createCustomFab(String keyText, int keyCode) {
        WebView targetWebView = webViews.get(activeClientId);
        if (targetWebView == null) {
            Toast.makeText(this, "No active client to attach the button to.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create a FrameLayout to hold the FAB and the TextView
        FrameLayout fabContainer = new FrameLayout(this);
        int fabSizePx = dpToPx(40); // Adjusted size for action buttons
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(fabSizePx, fabSizePx);
        fabContainer.setLayoutParams(containerParams);
        fabContainer.setAlpha(0.5f); // Set transparency
        fabContainer.setBackgroundResource(R.drawable.circular_button_background); // Set circular background directly on container
        fabContainer.setElevation(0f); // Remove shadow

        // Create the TextView for the label
        TextView label = new TextView(this);
        // CRITICAL CHANGE: Use WRAP_CONTENT for TextView to ensure it's drawn on top of the background
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.gravity = Gravity.CENTER; // Center the text within the button
        label.setLayoutParams(labelParams);
        label.setText(keyText);
        label.setTextColor(Color.WHITE);
        label.setTextSize(12); // Reduced text size for better fit
        label.setClickable(false); // Ensure TextView does not consume clicks
        label.setFocusable(false); // Ensure TextView does not consume focus

        // Add TextView to the container (only child)
        fabContainer.addView(label);

        // Set the click listener on the container
        fabContainer.setOnClickListener(v -> dispatchKeyEvent(targetWebView, keyCode));

        // Add the container to the root view and the map
        rootContainer.addView(fabContainer);
        customFabMap.put(fabContainer, targetWebView);

        // Make the entire container draggable
        makeFabDraggable(fabContainer);

        Toast.makeText(this, "Action Button for '" + keyText + "' created.", Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void makeFabDraggable(View view) {
        final float[] xDelta = new float[1];
        final float[] yDelta = new float[1];
        final float[] initialRawX = new float[1];
        final float[] initialRawY = new float[1];
        final long[] downTime = new long[1];

        view.setOnTouchListener((v, event) -> {
            int X = (int) event.getRawX();
            int Y = (int) event.getRawY();
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    downTime[0] = event.getDownTime();
                    initialRawX[0] = event.getRawX();
                    initialRawY[0] = event.getRawY();
                    xDelta[0] = X - v.getX();
                    yDelta[0] = Y - v.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                    long eventDuration = event.getEventTime() - downTime[0];
                    float dx = Math.abs(event.getRawX() - initialRawX[0]);
                    float dy = Math.abs(event.getRawY() - initialRawY[0]);
                    int slop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                    if (dx < slop && dy < slop && eventDuration < ViewConfiguration.getLongPressTimeout()) {
                        v.performClick();
                    } else {
                        snapFabToEdge(v);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    v.setX(X - xDelta[0]);
                    v.setY(Y - yDelta[0]);
                    return true;
            }
            return false;
        });
    }


    private void deleteAllCustomFabs() {
        for (View fab : customFabMap.keySet()) {
            rootContainer.removeView(fab);
        }
        customFabMap.clear();
        Toast.makeText(this, "All custom Action Buttons deleted.", Toast.LENGTH_SHORT).show();
    }

    private void dispatchKeyEvent(WebView webView, int keyCode) {
        String key;
        String code;
        switch (keyCode) {
            case KeyEvent.KEYCODE_F1: key = "F1"; code = "F1"; break;
            case KeyEvent.KEYCODE_F2: key = "F2"; code = "F2"; break;
            case KeyEvent.KEYCODE_F3: key = "F3"; code = "F3"; break;
            case KeyEvent.KEYCODE_F4: key = "F4"; code = "F4"; break;
            case KeyEvent.KEYCODE_F5: key = "F5"; code = "F5"; break;
            case KeyEvent.KEYCODE_F6: key = "F6"; code = "F6"; break;
            case KeyEvent.KEYCODE_F7: key = "F7"; code = "F7"; break;
            case KeyEvent.KEYCODE_F8: key = "F8"; code = "F8"; break;
            case KeyEvent.KEYCODE_F9: key = "F9"; code = "F9"; break;
            case KeyEvent.KEYCODE_F10: key = "F10"; code = "F10"; break;
            case KeyEvent.KEYCODE_F11: key = "F11"; code = "F11"; break;
            case KeyEvent.KEYCODE_F12: key = "F12"; code = "F12"; break;
            default:
                key = String.valueOf((char) (new KeyEvent(KeyEvent.ACTION_DOWN, keyCode)).getUnicodeChar());
                code = "Key" + key.toUpperCase();
                break;
        }

        String script = "javascript:(function() {" +
                "var canvas = document.querySelector('canvas');" +
                "if (canvas) {" +
                "   var eventProps = { bubbles: true, cancelable: true, key: '" + key + "', code: '" + code + "', keyCode: " + keyCode + " };" +
                "   canvas.dispatchEvent(new KeyboardEvent('keydown', eventProps));" +
                "   canvas.dispatchEvent(new KeyboardEvent('keyup', eventProps));" +
                "}" +
                "})()";
        webView.evaluateJavascript(script, null);
    }


    /* ---------- lifecycle ---------- */
    private void fullScreenOn() {
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (c != null) {
            c.hide(WindowInsetsCompat.Type.systemBars());
            c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        if (getSupportActionBar() != null) getSupportActionBar().hide();
    }

    @Override public void onBackPressed() {
        if (exit) { finish(); return; }
        Toast.makeText(this, "Press Back again to Exit", Toast.LENGTH_SHORT).show();
        exit = true;
        new Handler().postDelayed(() -> exit = false, 3000);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        for (int i = 0; i < webViews.size(); i++) webViews.valueAt(i).destroy();
        webViews.clear();
        layouts.clear();
        deleteAllCustomFabs();
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("activeClientId", activeClientId);
    }

    @Override protected void onRestoreInstanceState(@NonNull Bundle in) {
        super.onRestoreInstanceState(in);
        activeClientId = in.getInt("activeClientId", -1);
        if (activeClientId != -1) openClient(activeClientId);
    }
}
