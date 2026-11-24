package nl.collin.looplijst;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

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
        getBridge().getWebView().addJavascriptInterface(new AgendaBrug(), "LooplijstAgenda");
        leesIntent(getIntent(), false);
    }

    // ===== Telefoon-agenda (Google/Samsung/OneCalendar via de agenda van Android) =====
    private static final int AGENDA_VERZOEK = 4711;

    private boolean heeftAgenda() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private void meldAgenda(final boolean ok) {
        getBridge().getWebView().post(new Runnable() {
            public void run() {
                getBridge().getWebView().evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('looplijstAgendaToegang',{detail:" + (ok ? "true" : "false") + "}))", null);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AGENDA_VERZOEK) meldAgenda(heeftAgenda());
    }

    private class AgendaBrug {
        @JavascriptInterface
        public boolean toegang() { return heeftAgenda(); }

        @JavascriptInterface
        public void vraagToegang() {
            if (heeftAgenda()) { meldAgenda(true); return; }
            runOnUiThread(new Runnable() {
                public void run() {
                    ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, AGENDA_VERZOEK);
                }
            });
        }

        @JavascriptInterface
        public String kalenders() {
            JSONArray uit = new JSONArray();
            if (!heeftAgenda()) return uit.toString();
            String[] kol = new String[]{
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.IS_PRIMARY
            };
            Cursor c = null;
            try {
                c = getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, kol, null, null, null);
                while (c != null && c.moveToNext()) {
                    JSONObject o = new JSONObject();
                    o.put("id", String.valueOf(c.getLong(0)));
                    o.put("naam", c.getString(1) == null ? "" : c.getString(1));
                    o.put("account", c.getString(2) == null ? "" : c.getString(2));
                    o.put("soort", c.getString(3) == null ? "" : c.getString(3));
                    o.put("kleur", String.format("#%06X", 0xFFFFFF & c.getInt(4)));
                    o.put("schrijfbaar", c.getInt(5) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR);
                    o.put("zichtbaar", c.getInt(6) == 1);
                    o.put("hoofd", !c.isNull(7) && c.getInt(7) == 1);
                    uit.put(o);
                }
            } catch (Exception e) {
                // leeg teruggeven
            } finally {
                if (c != null) c.close();
            }
            return uit.toString();
        }

        @JavascriptInterface
        public String afspraken(String van, String tot) {
            JSONArray uit = new JSONArray();
            if (!heeftAgenda()) return uit.toString();
            Cursor c = null;
            try {
                Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
                ContentUris.appendId(b, Long.parseLong(van));
                ContentUris.appendId(b, Long.parseLong(tot));
                String[] kol = new String[]{
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.DESCRIPTION,
                    CalendarContract.Instances.CALENDAR_ID,
                    CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Instances.DISPLAY_COLOR,
                    CalendarContract.Instances.RRULE
                };
                c = getContentResolver().query(b.build(), kol, CalendarContract.Instances.VISIBLE + "=1", null, CalendarContract.Instances.BEGIN + " ASC");
                while (c != null && c.moveToNext()) {
                    JSONObject o = new JSONObject();
                    o.put("id", String.valueOf(c.getLong(0)));
                    o.put("titel", c.getString(1) == null ? "" : c.getString(1));
                    o.put("begin", c.getLong(2));
                    o.put("eind", c.getLong(3));
                    o.put("heleDag", c.getInt(4) == 1);
                    o.put("plek", c.getString(5) == null ? "" : c.getString(5));
                    o.put("notitie", c.getString(6) == null ? "" : c.getString(6));
                    o.put("kalender", String.valueOf(c.getLong(7)));
                    o.put("kalenderNaam", c.getString(8) == null ? "" : c.getString(8));
                    o.put("kleur", String.format("#%06X", 0xFFFFFF & c.getInt(9)));
                    o.put("herhaalt", c.getString(10) != null && c.getString(10).length() > 0);
                    uit.put(o);
                }
            } catch (Exception e) {
                // leeg teruggeven
            } finally {
                if (c != null) c.close();
            }
            return uit.toString();
        }

        private ContentValues waarden(JSONObject o, boolean nieuw) throws Exception {
            ContentValues v = new ContentValues();
            boolean heleDag = o.optBoolean("heleDag", false);
            v.put(CalendarContract.Events.TITLE, o.optString("titel", ""));
            v.put(CalendarContract.Events.DESCRIPTION, o.optString("notitie", ""));
            v.put(CalendarContract.Events.DTSTART, o.getLong("begin"));
            v.put(CalendarContract.Events.DTEND, o.getLong("eind"));
            v.put(CalendarContract.Events.ALL_DAY, heleDag ? 1 : 0);
            v.put(CalendarContract.Events.EVENT_TIMEZONE, heleDag ? "UTC" : TimeZone.getDefault().getID());
            if (nieuw || o.has("kalender")) v.put(CalendarContract.Events.CALENDAR_ID, Long.parseLong(o.getString("kalender")));
            return v;
        }

        @JavascriptInterface
        public String maak(String json) {
            if (!heeftAgenda()) return "";
            try {
                Uri u = getContentResolver().insert(CalendarContract.Events.CONTENT_URI, waarden(new JSONObject(json), true));
                return u == null ? "" : u.getLastPathSegment();
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean wijzig(String id, String json) {
            if (!heeftAgenda()) return false;
            try {
                Uri u = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, Long.parseLong(id));
                return getContentResolver().update(u, waarden(new JSONObject(json), false), null, null) > 0;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean verwijder(String id) {
            if (!heeftAgenda()) return false;
            try {
                Uri u = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, Long.parseLong(id));
                return getContentResolver().delete(u, null, null) > 0;
            } catch (Exception e) {
                return false;
            }
        }
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
