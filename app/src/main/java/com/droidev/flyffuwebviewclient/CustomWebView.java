package com.droidev.flyffuwebviewclient;

import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.WebView;

import static android.view.inputmethod.EditorInfo.IME_ACTION_DONE;
import static android.view.inputmethod.EditorInfo.TYPE_CLASS_TEXT;

public class CustomWebView extends WebView {
    public CustomWebView(Context context) {
        super(context);
        init();
    }

    public CustomWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    protected void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    // Commenting out onCreateInputConnection as per user's suggestion
    /*
    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        BaseInputConnection baseInputConnection = new BaseInputConnection(this, false);
        outAttrs.imeOptions = IME_ACTION_DONE;
        outAttrs.inputType = TYPE_CLASS_TEXT;
        return baseInputConnection;
    }
    */
}
