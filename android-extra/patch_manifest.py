p = 'android/app/src/main/AndroidManifest.xml'
s = open(p, encoding='utf-8').read()
filters = """<intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="application/pdf" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="application/pdf" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="looplijst" android:host="bank" />
            </intent-filter>
        </activity>"""
assert '</activity>' in s
s = s.replace('</activity>', filters, 1)
# telefoon-agenda lezen en schrijven (koppeling Privé-agenda)
rechten = '''    <uses-permission android:name="android.permission.READ_CALENDAR" />
    <uses-permission android:name="android.permission.WRITE_CALENDAR" />
    <uses-permission android:name="android.permission.READ_SYNC_SETTINGS" />
'''
if 'READ_CALENDAR' not in s:
    assert '<application' in s
    s = s.replace('<application', rechten + '    <application', 1)
open(p, 'w', encoding='utf-8').write(s)
print('manifest aangepast')
