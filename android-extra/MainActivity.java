package nl.collin.looplijst;

import android.Manifest;
import android.accounts.Account;
import android.content.ContentResolver;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends BridgeActivity {
    private volatile String gedeeldePdf = "";
    private volatile String bankTerug = "";

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
        getBridge().getWebView().addJavascriptInterface(new BankBrug(), "LooplijstBank");
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
                CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.SYNC_EVENTS
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
                    o.put("synct", !c.isNull(8) && c.getInt(8) == 1);
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
                    CalendarContract.Instances.RRULE,
                    CalendarContract.Instances.STATUS,
                    CalendarContract.Instances.SELF_ATTENDEE_STATUS
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
                    o.put("status", c.isNull(11) ? -1 : c.getInt(11));
                    o.put("zelf", c.isNull(12) ? -1 : c.getInt(12));
                    uit.put(o);
                }
            } catch (Exception e) {
                // leeg teruggeven
            } finally {
                if (c != null) c.close();
            }
            return uit.toString();
        }

        // Een afspraak meteen naar Google/Outlook laten doorsturen (anders ziet OneCalendar hem pas later)
        private void stuurDoor(long kalenderId) {
            Cursor c = null;
            try {
                c = getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
                    new String[]{CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE},
                    CalendarContract.Calendars._ID + "=?", new String[]{String.valueOf(kalenderId)}, null);
                if (c != null && c.moveToFirst()) stuurAccountDoor(c.getString(0), c.getString(1));
            } catch (Exception e) {
                // niet erg: de telefoon synchroniseert later vanzelf
            } finally {
                if (c != null) c.close();
            }
        }

        private void stuurAccountDoor(String naam, String soort) {
            if (naam == null || soort == null || CalendarContract.ACCOUNT_TYPE_LOCAL.equals(soort)) return;
            try {
                Bundle extra = new Bundle();
                extra.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                extra.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                ContentResolver.requestSync(new Account(naam, soort), CalendarContract.AUTHORITY, extra);
            } catch (Exception e) {
                // genegeerd
            }
        }

        private long kalenderVan(String eventId) {
            Cursor c = null;
            try {
                c = getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, Long.parseLong(eventId)),
                    new String[]{CalendarContract.Events.CALENDAR_ID}, null, null, null);
                if (c != null && c.moveToFirst()) return c.getLong(0);
            } catch (Exception e) {
                // genegeerd
            } finally {
                if (c != null) c.close();
            }
            return -1;
        }

        // Controle: bestaan deze afspraken nog en zijn ze al naar Google/Outlook gestuurd?
        @JavascriptInterface
        public String controle(String ids) {
            JSONArray uit = new JSONArray();
            if (!heeftAgenda() || ids == null) return uit.toString();
            for (String id : ids.split(",")) {
                if (id.trim().length() == 0) continue;
                Cursor c = null;
                try {
                    JSONObject o = new JSONObject();
                    o.put("id", id.trim());
                    c = getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, Long.parseLong(id.trim())),
                        new String[]{CalendarContract.Events.TITLE, CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.DIRTY,
                            CalendarContract.Events._SYNC_ID, CalendarContract.Events.DELETED, CalendarContract.Events.DTSTART}, null, null, null);
                    if (c != null && c.moveToFirst()) {
                        o.put("bestaat", true);
                        o.put("titel", c.getString(0) == null ? "" : c.getString(0));
                        o.put("kalender", String.valueOf(c.getLong(1)));
                        o.put("nogTeVersturen", !c.isNull(2) && c.getInt(2) == 1);
                        o.put("online", !c.isNull(3) && c.getString(3).length() > 0);
                        o.put("verwijderd", !c.isNull(4) && c.getInt(4) == 1);
                        o.put("begin", c.getLong(5));
                    } else {
                        o.put("bestaat", false);
                    }
                    uit.put(o);
                } catch (Exception e) {
                    // overslaan
                } finally {
                    if (c != null) c.close();
                }
            }
            return uit.toString();
        }

        // Staat synchroniseren van de agenda aan voor de accounts op deze telefoon?
        @JavascriptInterface
        public String accounts() {
            JSONArray uit = new JSONArray();
            if (!heeftAgenda()) return uit.toString();
            Cursor c = null;
            boolean hoofd = true;
            try { hoofd = ContentResolver.getMasterSyncAutomatically(); } catch (Exception e) { }
            try {
                c = getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
                    new String[]{CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE}, null, null, null);
                java.util.HashSet<String> gehad = new java.util.HashSet<String>();
                while (c != null && c.moveToNext()) {
                    String naam = c.getString(0), soort = c.getString(1);
                    if (naam == null || soort == null || !gehad.add(soort + "|" + naam)) continue;
                    JSONObject o = new JSONObject();
                    o.put("naam", naam);
                    o.put("soort", soort);
                    o.put("hoofdschakelaar", hoofd);
                    if (!CalendarContract.ACCOUNT_TYPE_LOCAL.equals(soort)) {
                        try {
                            Account a = new Account(naam, soort);
                            o.put("automatisch", ContentResolver.getSyncAutomatically(a, CalendarContract.AUTHORITY));
                            o.put("bezig", ContentResolver.isSyncActive(a, CalendarContract.AUTHORITY));
                        } catch (Exception e) {
                            o.put("onbekend", true);
                        }
                    }
                    uit.put(o);
                }
            } catch (Exception e) {
                // leeg
            } finally {
                if (c != null) c.close();
            }
            return uit.toString();
        }

        @JavascriptInterface
        public void synchroniseer() {
            if (!heeftAgenda()) return;
            Cursor c = null;
            try {
                c = getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
                    new String[]{CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE}, null, null, null);
                java.util.HashSet<String> gehad = new java.util.HashSet<String>();
                while (c != null && c.moveToNext()) {
                    String sleutel = c.getString(1) + "|" + c.getString(0);
                    if (gehad.add(sleutel)) stuurAccountDoor(c.getString(0), c.getString(1));
                }
            } catch (Exception e) {
                // genegeerd
            } finally {
                if (c != null) c.close();
            }
        }

        private ContentValues waarden(JSONObject o, boolean nieuw) throws Exception {
            ContentValues v = new ContentValues();
            boolean heleDag = o.optBoolean("heleDag", false);
            v.put(CalendarContract.Events.TITLE, o.optString("titel", ""));
            if (nieuw || o.has("notitie")) v.put(CalendarContract.Events.DESCRIPTION, o.optString("notitie", ""));
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
                JSONObject o = new JSONObject(json);
                Uri u = getContentResolver().insert(CalendarContract.Events.CONTENT_URI, waarden(o, true));
                if (u != null) stuurDoor(Long.parseLong(o.getString("kalender")));
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
                boolean ok = getContentResolver().update(u, waarden(new JSONObject(json), false), null, null) > 0;
                if (ok) stuurDoor(kalenderVan(id));
                return ok;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean verwijder(String id) {
            if (!heeftAgenda()) return false;
            try {
                long kal = kalenderVan(id);
                Uri u = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, Long.parseLong(id));
                boolean ok = getContentResolver().delete(u, null, null) > 0;
                if (ok && kal >= 0) stuurDoor(kal);
                return ok;
            } catch (Exception e) {
                return false;
            }
        }
    }

    // ===== Bank (Enable Banking): webverzoeken buiten de WebView om, en de bank-login in de browser =====
    private void bankAntwoord(final String id, final int status, final String body) {
        getBridge().getWebView().post(new Runnable() {
            public void run() {
                getBridge().getWebView().evaluateJavascript(
                    "window.__bankAntwoord && window.__bankAntwoord(" + JSONObject.quote(id) + "," + status + "," + JSONObject.quote(body == null ? "" : body) + ")", null);
            }
        });
    }

    private class BankBrug {
        @JavascriptInterface
        public String neem() {
            String r = bankTerug;
            bankTerug = "";
            return r;
        }

        @JavascriptInterface
        public void open(String url) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) {
                // genegeerd
            }
        }

        @JavascriptInterface
        public void verzoek(final String id, final String methode, final String adres, final String kopjes, final String inhoud) {
            new Thread(new Runnable() {
                public void run() {
                    HttpURLConnection con = null;
                    try {
                        con = (HttpURLConnection) new URL(adres).openConnection();
                        con.setRequestMethod(methode);
                        con.setConnectTimeout(20000);
                        con.setReadTimeout(60000);
                        JSONObject k = new JSONObject(kopjes == null || kopjes.length() == 0 ? "{}" : kopjes);
                        Iterator<String> it = k.keys();
                        while (it.hasNext()) { String n = it.next(); con.setRequestProperty(n, k.getString(n)); }
                        if (inhoud != null && inhoud.length() > 0) {
                            con.setDoOutput(true);
                            OutputStream os = con.getOutputStream();
                            os.write(inhoud.getBytes("UTF-8"));
                            os.close();
                        }
                        int status = con.getResponseCode();
                        InputStream in = status >= 400 ? con.getErrorStream() : con.getInputStream();
                        ByteArrayOutputStream uit = new ByteArrayOutputStream();
                        if (in != null) {
                            byte[] buf = new byte[16384];
                            int n;
                            while ((n = in.read(buf)) > 0) uit.write(buf, 0, n);
                            in.close();
                        }
                        bankAntwoord(id, status, uit.toString("UTF-8"));
                    } catch (Exception e) {
                        bankAntwoord(id, 0, String.valueOf(e.getMessage()));
                    } finally {
                        if (con != null) con.disconnect();
                    }
                }
            }).start();
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
        // terug van de bank-login (looplijst://bank?code=...&state=...)
        if ("looplijst".equalsIgnoreCase(uri.getScheme())) {
            bankTerug = uri.toString();
            if (meld) {
                getBridge().getWebView().post(new Runnable() {
                    public void run() {
                        getBridge().getWebView().evaluateJavascript("window.dispatchEvent(new Event('looplijstBank'))", null);
                    }
                });
            }
            return;
        }
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
