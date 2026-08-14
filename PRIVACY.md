# Privacy Policy — Echoframe

Last updated: 2026-08-06

Echoframe is an Android app that acts as your device's digital assistant. When you invoke
the assistant, it captures what is on your screen and what is being said around you, and
saves it as a note on your phone. This policy describes exactly what the app collects, where
it is stored, and who else can see it. It reflects what the current code actually does.

## Who controls your data

You do. Everything Echoframe captures is written to the app's private storage on your own
device. The developer of Echoframe operates no servers, no accounts, and no backend, and
receives none of your captures.

## What Echoframe collects, and when

Echoframe only collects anything at the moment **you** invoke it as the assistant (for
example a long-press of the power button or your device's assistant gesture). It does not
run in the background, and it does not listen or watch when it has not been invoked.

At that moment it captures:

- **Ambient audio from the microphone** (`RECORD_AUDIO`). Recording starts when the
  assistant overlay appears and stops when you tap "Save Note" or the overlay is dismissed.
  It is saved as an uncompressed 16 kHz mono WAV file. Anything audible to the microphone
  during that window — including other people's voices and background sound — is recorded.
- **A screenshot of whatever was on your screen** when the assistant was invoked. This is
  provided to Echoframe by Android as part of the assistant role. It can contain anything
  that was visible at that instant, including private messages, banking details, health
  information, passwords in plain view, or other people's information.
- **The package name of the app you were using** (for example `com.example.browser`), so
  the note can show where it came from.
- **The web address (URL) of the page you were viewing**, when the app you were in shares
  one with the assistant — typically a browser or a browser-based screen.
- **Note metadata** derived from the above: a timestamp, the recording duration, an
  auto-generated title, an automatically assigned category, the extracted note text, plus
  any tags or favourite marks you add yourself.

Echoframe does **not** collect your contacts, calendar, location, files, accounts, device
identifiers, or advertising ID, and it does not ask for those permissions.

## Where it is stored

- Media files (screenshot, audio, extracted text) are written to the app's private internal
  storage directory. Other apps on the device cannot read them.
- Note metadata is stored in a local database on the device.
- Android's cloud backup is explicitly disabled for this app (`allowBackup="false"`), so
  captures are not copied into a Google account backup by the app.

Nothing is encrypted beyond the whole-device encryption Android already applies. Anyone who
can unlock your phone and open Echoframe can read your captures.

## How captures are processed

Screenshot processing happens on your device using Google's ML Kit:

- **Text recognition** reads text visible in the screenshot.
- **Image labelling** assigns general descriptive labels (for example "Text", "Screenshot",
  "Food").

The recognised text and labels become the body of the note.

**This processing is fully offline.** Echoframe uses the *bundled* ML Kit libraries
(`com.google.mlkit:text-recognition`, `com.google.mlkit:image-labeling`). Their recognition
models ship inside the app itself — you can verify this: the installed package contains the
`libmlkit_google_ocr_pipeline` native library and the `mlkit-google-ocr-models` /
`mlkit_label_default_model` asset files. Nothing is downloaded at first use, no request is
made to Google Play Services to obtain a model, and recognition never requires a network
connection. Your audio, screenshots and notes are not uploaded to Google or anyone else.

Because ML Kit and Android's WorkManager are part of the app, the merged app manifest
includes the `INTERNET` and `ACCESS_NETWORK_STATE` permissions. Echoframe's own code makes
no network requests of any kind, and image analysis works with the device offline or in
aeroplane mode.

Your recorded audio is currently stored as a file only; the app does not transcribe it and
does not send it anywhere.

## When data leaves your device

Only when you deliberately send it. Echoframe includes actions that hand a note to another
app you choose:

- **Share** — sends the note as Markdown text through Android's share sheet to an app you pick.
- **Copy** — puts the note text on your clipboard.
- **Detected actions** — opens a link in your browser, dials a phone number, creates a
  calendar event, or opens an address in a maps app.

Once a note is handed to another app, that app's own privacy policy applies. Echoframe has
no control over it.

## Notifications

Echoframe shows a notification while it analyses a capture in the background
(`POST_NOTIFICATIONS`). Notifications are local to your device.

## Data retention and deletion

- Captures are kept **until you delete them**. There is no automatic expiry.
- Deleting a capture in the app removes its screenshot, audio file, extracted text and
  database entry from the device.
- Uninstalling Echoframe, or using Android's "Clear storage" for the app, deletes all
  captures.

## Analytics, crash reporting, advertising

There is none. Echoframe contains no analytics SDK, no crash-reporting SDK, no advertising
SDK, and no tracking of any kind. No usage data is collected or transmitted.

## Children

Echoframe is not directed at children and collects no information about age. Because it can
record ambient audio and capture on-screen content, it is not intended for use by children.

## Your responsibilities when recording others

Recording audio of other people may require their consent depending on where you live. You
are responsible for using Echoframe lawfully.

## Changes to this policy

If the app's data handling changes, this document will be updated and the date at the top
revised.

## Contact

<!-- TODO (required before publishing): replace with a real contact address. Google Play
     requires a working contact route, and the URL where this policy is hosted must be
     entered in the Play Console listing. -->

Questions about this policy or your data: **[ADD CONTACT EMAIL HERE]**
