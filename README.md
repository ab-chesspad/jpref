# JPref
JPref is Java program to play Russian Preference (преферанс).  

[documentation](http://jpref.elementfx.com/)  

[документация](http://jpref.elementfx.com/ru)  

The program can be built with IntelliJ or Maven. There are three data files in etc/doc directory, utyatsky-1, utyatsky-2 and
tricks-src that are the sources for JPref resource files. The script prebuild is used to build them in case the sources get
updated.

Installation on Android phone:
1. Open https://github.com/ab-chesspad/jpref/raw/refs/heads/main/DPref/app/build/outputs/apk/debug/app-debug.apk in Chrome (or any browser) on the phone.
2. Chrome may block the download because the link is plain http. Tap Keep or Download anyway. If there's no such option, use the adb method below or serve the file over https.
3. When the download finishes, tap the notification, or open the file in Files → Downloads.
4. On first install Android shows "For your security, your phone is not allowed to install unknown apps from this source". Tap Settings, turn on Allow from this source, then go back and tap Install.
    - To set this up ahead of time: Settings → Apps → Special app access → Install unknown apps → Chrome → Allow. The exact path varies by manufacturer.
5. Play Protect may warn about an unknown developer. Tap More details → Install anyway.


