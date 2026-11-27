# Tastenhalter – MIDI-Steuerung für Orgel-Selbstspieleinrichtungen

Android-App zum Ansteuern einer Orgel über einen MIDI-Decoder, der Note-On/Note-Off in Magnetausgänge umsetzt –
zum Stimmen (Töne und Intervalle halten), zum Testen der Magnete und zum Abspielen von MIDI-Dateien.

Kotlin · Jetpack Compose · Android 10+ · nur `android.media.midi`, keine Google-Play-Dienste nötig.

## Download

Die fertige APK liegt unter [Releases](../../releases). Auf dem Handy herunterladen, öffnen und die Installation
aus dieser Quelle erlauben.

## Funktionen

- **Verbindung**: USB-MIDI (per USB-OTG, z. B. Interface oder Funk-MIDI-Empfänger) und Bluetooth-LE-MIDI.
  Geräte anzeigen, verbinden, Status, Prüfton a'. USB-MIDI wird beim Einstecken automatisch verbunden (abschaltbar).
- **Orgel-Einstellungen**: tiefster/höchster Ton, maximale Anzahl gleichzeitiger Töne und MIDI-Kanal –
  passend zum eigenen Decoder. Voreinstellung: C–g''' (MIDI 36–91, 56 Töne), 15 Töne, Kanal 1.
- **Klaviatur** über den ganzen Umfang mit Haltemodus (Tippen = an, nochmal Tippen = aus), Oktav-Navigation, Zoom.
- **Stimmen**: Ton halten, voriger/nächster Ton, Intervall dazu (Terz, Quarte, Quinte, Oktave, Duodezime,
  Doppeloktave), chromatischer Durchlauf (auf-/abwärts, Tondauer 0,1–10 s, Endlosschleife).
- **Lieder**: .mid öffnen (auch „Öffnen mit“ aus dem Dateimanager), Spuren/Kanäle auswählen, alles auf den
  eingestellten Kanal, Noten außerhalb des Umfangs werden oktaviert, Transponieren, Tempo 25–200 %,
  Start/Pause/Stopp, Spulen, Polyphonie-Grenze. 17 gemeinfreie Choräle und Kirchenlieder sind mitgeliefert.
- **NOT-AUS** immer sichtbar: Note Off für alle Töne des Umfangs (+ „All Notes Off“), nach 300 ms wiederholt.
  Wird außerdem ausgelöst bei Stopp, Liedende, Trennen, Verbindungsverlust, jedem (Wieder-)Verbinden,
  Ändern der Orgel-Einstellungen und beim Verlassen der App (Home, Bildschirm aus, Dateiauswahl).
- Bildschirm bleibt an, solange ein Lied läuft, ein Durchlauf läuft oder ein Ton klingt.

Die Oberfläche ist auf Deutsch, Tonnamen in Helmholtz-Schreibweise (C, c, c', c'' …; b = Ton zwischen a und h).

## Mitgelieferte Lieder

Alle Sätze stammen aus dem [Mutopia Project](https://www.mutopiaproject.org) und sind dort als
**Public Domain** (gemeinfrei) gekennzeichnet. Die Dateien liegen unter
[`app/src/main/assets/lieder`](app/src/main/assets/lieder); die Liste mit Quellen steht in
[`lieder.tsv`](app/src/main/assets/lieder/lieder.tsv).

| Lied | Komponist / Satz |
|---|---|
| Lobe den Herren | Melodie: Stralsund 1665, Satz anonym |
| Ein feste Burg ist unser Gott | Martin Luther |
| Jesus, meine Zuversicht | Johann Crüger |
| Lobt Gott, ihr Christen allzugleich | Nikolaus Herman, Satz anonym |
| Vom Himmel hoch, da komm ich her | Melodie: Leipzig 1539, Satz anonym |
| Es ist ein Ros entsprungen | Melodie: Köln 1599, Satz anonym |
| Stille Nacht, heilige Nacht | Franz Xaver Gruber |
| Mit Freuden zart | Melodie: Böhmische Brüder 1566, Satz anonym |
| Allein Gott in der Höh sei Ehr (aus „Paulus“) | Felix Mendelssohn Bartholdy |
| O Haupt voll Blut und Wunden (Matthäus-Passion) | Johann Sebastian Bach |
| Aus meines Herzens Grunde (BWV 269) | Johann Sebastian Bach |
| Ich dank dir, lieber Herre (BWV 347) | Johann Sebastian Bach |
| Gott, wie groß ist deine Güte (BWV 462) | Johann Sebastian Bach |
| Ermuntre dich, mein schwacher Geist (BWV 454) | Johann Sebastian Bach |
| Großer Gott, wir loben dich (op. 135a Nr. 10) | Max Reger |
| Jesu, meine Freude (Orgelbüchlein, BWV 610) | Johann Sebastian Bach |
| Vom Himmel hoch (Orgelbüchlein, BWV 606) | Johann Sebastian Bach |

Weitere Lieder: `.mid`-Datei in den Ordner legen und eine Zeile in `lieder.tsv` ergänzen – nur Dateien mit
freier Lizenz.

## Bauen

Benötigt JDK 17 und das Android-SDK (Platform 35).

```
gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Die Release-APK wird mit dem lokalen Debug-Schlüssel signiert, damit sie sich direkt installieren lässt.

## Sicherheit

Die App schaltet bei jedem Stopp, Verbindungswechsel und beim Verlassen alle Töne ab. Bei einer Funkstrecke
können trotzdem einzelne MIDI-Befehle verloren gehen – vor dem Einsatz an der Orgel Prüfton und NOT-AUS testen.
Benutzung auf eigene Verantwortung.

## Lizenz

Code: [MIT](LICENSE). Die mitgelieferten MIDI-Dateien sind gemeinfrei (Public Domain, siehe oben).
