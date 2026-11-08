package nl.collin.looplijst;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import com.getcapacitor.BridgeActivity;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends BridgeActivity {
    private volatile String gedeeldePdf = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getBridge().getWebView().addJavascriptInterface(new Object() {
            @JavascriptInterface
            public String neem() {
                String r = gedeeldePdf;
                gedeeldePdf = "";
                return r;
            }
        }, "LooplijstShare");
        leesIntent(getIntent(), false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        leesIntent(intent, true);
    }

    private void leesIntent(Intent intent, boolean meld) {
        if (intent == null) return;
        String actie = intent.getAction();
        Uri uri = null;
        if (Intent.ACTION_SEND.equals(actie)) {
            uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        } else if (Intent.ACTION_VIEW.equals(actie)) {
            uri = intent.getData();
        }
        if (uri == null) return;
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return;
            ByteArrayOutputStream uit = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) uit.write(buf, 0, n);
            in.close();
            gedeeldePdf = Base64.encodeToString(uit.toByteArray(), Base64.NO_WRAP);
            if (meld) {
                getBridge().getWebView().post(new Runnable() {
                    public void run() {
                        getBridge().getWebView().evaluateJavascript(
                            "window.dispatchEvent(new Event('looplijstShare'))", null);
                    }
                });
            }
        } catch (Exception e) {
            // genegeerd: de app start gewoon zonder gedeeld bestand
        }
    }
}
