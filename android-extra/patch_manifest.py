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
        </activity>"""
assert '</activity>' in s
s = s.replace('</activity>', filters, 1)
open(p, 'w', encoding='utf-8').write(s)
print('manifest aangepast')
