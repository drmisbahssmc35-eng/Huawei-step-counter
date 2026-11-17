# Voice Typer for Android

Speak, and your words are typed out and **copied to the clipboard automatically**, ready to paste into WhatsApp, Messenger, Gmail or any other app. Made for the Samsung Galaxy A56, and works on any phone running Android 8 or newer.

## Download

Phone link: https://github.com/drmisbahssmc35-eng/Huawei-step-counter/releases/download/voice-typer-apk/VoiceTyper.apk

Open it and allow installing from your browser when Android asks. On Samsung this is under *Settings → Security and privacy → Install unknown apps*.

## Two speech engines

| Engine | Best for | Needs |
| --- | --- | --- |
| **Google · live** | Free. Words appear while you speak. Keeps listening through pauses until you tap stop. Adds punctuation automatically on Android 13 and newer. | The Google app (already on Samsung phones) |
| **Whisper AI · best** | The highest accuracy, close to Claude's voice typing. Very good with Bangla and with Bangla and English mixed together. Text appears after you tap stop. | A free Groq API key, or an OpenAI key |

### Turn on Whisper AI (free with Groq)

1. Go to https://console.groq.com, sign in, then open **API Keys → Create API Key** and copy the key.
2. In Voice Typer, tap ⚙ → paste the key → **Save**.
3. Choose **Whisper AI · best** at the top of the screen.

## Fastest way to use it

* **Quick Settings tile:** swipe down twice, tap ✎ (edit) and drag the **Voice Typer** tile into your tiles. Tapping it from any app starts recording right away.
* **Long-press the app icon → Dictate** does the same.
* Tap the red stop button. The text is copied at once, so you can go back and paste.

Other features: choose the language (Auto, English, বাংলা, Hindi, Urdu or Arabic), edit the text before copying, Share, and **History** (🕘), which keeps your last 30 transcripts; tap one to copy it again.

## Build it yourself

Every push that changes `voice-typer/` runs the **Build Voice Typer APK** workflow. To build locally:

```bash
cd voice-typer && ./gradlew assembleRelease
```
