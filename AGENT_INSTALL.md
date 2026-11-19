# Installing Veil: instructions for the AI agent

You are helping a person install **Veil**, a custom Android TV home screen, from the source in `source/`.
This copy is a modified Veil: next to the original features it can use a **Plex** server as the source for
Continue watching / Recently added, and it has a **Request** screen for a **Seerr** server (see `README.md`).
Read this whole file before you start. `README.md` is the person's overview. Point them to it for what
Veil does and why it needs each permission.

Work in **phases** and finish each one before starting the next. Explain what you're about to do in plain
language. **Ask before every change on the TV.**

---

## 0. Rules (these came from real incidents; follow them strictly)

1. **Never remove, disable or "debloat" system packages**, including the TV's own launcher. Removing Android
   core/mainline modules (adservices, sdksandbox, ondevicepersonalization, healthconnect...) boot-looped a TV
   during Veil's development and forced a factory reset. Veil sits *beside* the stock launcher. It never
   replaces it.
2. **Every change on the TV must be reversible.** Back up first (phase 2) and keep the undo script up to date.
3. **Ask before every TV change** (installing, granting, settings). Say what it does and how it's undone.
4. **Don't press buttons on the TV** (`adb shell input keyevent ...`) while the person is using it. Ask them to
   use the remote, or ask permission to take control for a measured test.
5. **Never `am force-stop dev.veil.launcher`.** Android then switches off its Home-button accessibility
   service. To restart Veil, press Home on the remote, or reinstall.
6. **The keys belong to the person.** Ask them for *their own* keys. Never invent, reuse or look up anyone
   else's. Never print keys back in full, commit them, or put them anywhere except `source/keys.properties`.
7. After writing settings or uninstalling anything, **wait ~30 s before any reboot** (Android saves
   package/settings state with a delay), and check again after it boots.
8. Edit source files with UTF-8-safe tools. On Windows, don't round-trip files through PowerShell 5.1
   `Get-Content`/`Set-Content` (it garbles characters like · ° … —).

---

## 1. Check the TV and the computer

**TV.** The person enables developer options (Settings → Device Preferences → About → click *Build* 7×),
then **USB debugging** / **Network debugging** (the name varies by brand). Connect with
`adb connect <TV-IP>[:5555]`. On newer TVs use *Wireless debugging* → `adb pair`, then `adb connect`.
Accept the prompt on the TV.

Collect and show the person:
```sh
adb shell getprop ro.build.version.release      # Android version: must be 9 or newer
adb shell getprop ro.build.version.sdk          # API level: must be 28 or higher
adb shell getprop ro.product.model
adb shell getprop ro.product.manufacturer
adb shell cat /proc/meminfo | head -1           # RAM
adb shell wm size                               # screen resolution (UI is designed for 1920x1080)
adb shell dumpsys SurfaceFlinger | grep -i "GLES"   # GPU + OpenGL ES version (visualizer needs ES 3.0)
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell pm list packages | grep -i -E "nuvio|stremio|spotify|plexapp"
```
Stop and tell the person if the TV runs older than Android 9. If OpenGL ES is below 3.0, Veil still
works; the visualizer is simply unavailable, and music mode uses the album-art screen.

**Computer.** It needs:
- **JDK 17** (e.g. Eclipse Temurin 17).
- **Android SDK** command-line tools with `platforms;android-34`, `build-tools;34.0.0` and `platform-tools`
  (adb). No Android Studio needed. Write the SDK location to `source/local.properties` as
  `sdk.dir=/path/to/sdk`; on Windows escape it like `sdk.dir=C\:\\path\\to\\sdk`.
- **Gradle** is included: use `source/gradlew` (or `gradlew.bat` on Windows). It downloads Gradle 8.7 by itself.

---

## 2. Back up the TV's current settings (before *any* change)

Save the output to `veil-backup.txt` next to this file:
```sh
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell cmd role get-role-holders --user 0 android.app.role.HOME      # Android 10+
adb shell settings get secure screensaver_components
adb shell settings get secure screensaver_enabled
adb shell settings get secure enabled_accessibility_services
adb shell settings get secure accessibility_enabled
adb shell settings get secure enabled_notification_listeners
adb shell settings get global private_dns_mode
adb shell settings get global private_dns_specifier
adb shell pm list packages -s > veil-system-packages.txt                # reference only; never act on it
```
Then **write an undo script** for this TV, for the person's OS (`undo-veil.sh` or `undo-veil.ps1`), from
those values. It should:
- **Home:** give the home role back to the original launcher. On Android 10+:
  `cmd role add-role-holder --user 0 android.app.role.HOME <original package>`. On Android 9:
  `cmd package set-home-activity --user 0 <original component>`.
- **Screensaver:** restore `screensaver_components` to the saved value.
- **Accessibility:** remove *only* `dev.veil.launcher/dev.veil.launcher.HomeButtonService` from
  `enabled_accessibility_services`, keeping any other services there. Set `accessibility_enabled` to 0 only if
  nothing else is left.
- **Notifications:** `cmd notification disallow_listener dev.veil.launcher/dev.veil.launcher.MediaListener`
- **DNS:** restore `private_dns_mode` and `private_dns_specifier` to the saved values. If a value was `null`,
  use `settings delete global <name>`.
- **Finish:** wait 30 s, then print the home role holder so the person can see it worked.
- Mention at the end: `adb uninstall dev.veil.launcher` removes Veil completely.

Show the person the script before you go on.

---

## 3. The person's keys

Copy `source/keys.properties.example` to `source/keys.properties` and fill it in **with the person**.
Walk them through getting each key:

- **TMDB_KEY** (required, for artwork): themoviedb.org account → Settings → API → request a key
  (Developer, personal use). Use the **v3 "API Key"**, not the long read-access token.
- **Trakt, Simkl or MDBList** (for *Continue watching*): **nothing to set up.** Leave the `TRAKT_*`,
  `SIMKL_CLIENT_ID` and `MDBLIST_CLIENT_ID` lines empty and the build uses Veil's shared apps
  (`source/trakt.properties`, `simkl.properties`, `mdblist.properties`; public on purpose, TV sign-in apps
  have no secret). Ask which tracker they use. They sign in later **on the TV**: "Connect a tracker" on Home
  lets them pick Trakt, Simkl or MDBList and shows a QR code and a short code for their phone. Their account
  never goes into the source. One tracker is connected at a time.
  Continue watching opens items in **Nuvio** if it is installed, otherwise **Stremio** (there is no menu
  setting for this). The player must sync its progress to the same tracker account (Nuvio syncs to Trakt; check the
  player's settings for Simkl or MDBList).
  - Only if they want **their own Trakt app**: trakt.tv → Settings → Your API Apps → New application, any
    name, redirect URI `urn:ietf:wg:oauth:2.0:oob`. They can enter its Client ID on the TV (Connect Trakt →
    *Use your own Trakt app*). Veil saves it on the TV and also writes it to
    `/sdcard/Android/data/dev.veil.launcher/files/trakt.properties`. When you rebuild, copy it into
    `keys.properties` so it's built in too: `adb shell cat /sdcard/Android/data/dev.veil.launcher/files/trakt.properties`.
    Leave `TRAKT_CLIENT_SECRET` empty: new Trakt apps are PKCE-only.
- **NextDNS** (optional): **ask the person whether they want to use NextDNS** for the ad-blocking switch.
  Explain the choice first: with NextDNS (free account at nextdns.io, their own block lists) Veil also shows
  how many trackers were blocked today and this week. Without it, the switch uses AdGuard DNS (no account,
  no counter). Both block system-wide only when they turn the switch on.
  - If **yes**: fill in `NEXTDNS_PROFILE` (the ID on the my.nextdns.io setup page, e.g. `abc123`) and
    `NEXTDNS_API_KEY` (bottom of the account page). Then tell them to set **Settings → Logs → Retention**
    in NextDNS to **1 week or longer**. With a shorter retention NextDNS deletes the older days, so "this
    week" always equals "today". The change only keeps data from then on, so the weekly count fills in over
    the following days.
  - If **no**: leave both empty.
- **Plex** (optional, for Continue watching and Recently added from the person's own Plex server): **nothing
  to put in `keys.properties`.** Ask whether they use Plex. If so, they pick **Plex** on the TV under
  *Connect a tracker* and type the 4-character code at plex.tv/link. The **Plex app has to be installed**
  on the TV to play anything. Veil opens Plex first and starts the title after a few seconds; the wait is
  the *Plex → Wait before playing* slider in the Settings menu (default 5 s).
- **SEERR_URL and SEERR_API_KEY** (optional, for the **Request** screen): **ask the person whether they run
  a Seerr (Overseerr / Jellyseerr) server.** Explain it first: Veil gets a **Request** button next to the
  Settings button, where they can search, browse by streaming service, see scores and details, and request
  a movie, a show or specific seasons. Without it the button is hidden. They give `SEERR_URL` (the https
  address of their server, no path) and `SEERR_API_KEY` (Seerr > Settings > General). Never print the key
  back in full. Seerr approves requests made with an admin API key on its own, so a request may start a
  download at once. Never send a test request without asking first. If the address only exists on their
  home network, that is fine: Veil asks the router's DNS for it, also while ad blocking is on.
- **WEATHER_CITY** (optional): a city name. It can also be set later on the TV in the Settings menu.
- **UPDATE_CHECK**: **ask the person** whether Veil may check for updates. If yes (the default), once a
  day Veil asks GitHub's public API for the date of its latest code change and, if it's newer than their
  build, shows a small dot on the "Settings" button and an "Update available" row in the Settings menu (never a
  popup). Nothing about them is sent; GitHub only sees an IP address. If no, set `UPDATE_CHECK=false`. It
  can be switched later in the Settings menu.

Changing keys means rebuilding (they're compiled into the app). Never commit or share `keys.properties`;
it's already listed in `source/.gitignore`.

---

## 4. Build and install

```sh
cd source
./gradlew assembleRelease          # Windows: gradlew.bat assembleRelease
```
Install only after `BUILD SUCCESSFUL`. On Windows, R8 sometimes locks `classes.dex`; just run the build
again. The app is at `source/app/build/outputs/apk/release/app-release.apk`. It's signed with this
computer's debug key, so later updates must be built on the same computer. A build from a different
computer needs an uninstall first, which also signs them out of their tracker.

Ask, then run:
```sh
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell pm grant dev.veil.launcher android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant dev.veil.launcher android.permission.RECORD_AUDIO
adb shell cmd notification allow_listener dev.veil.launcher/dev.veil.launcher.MediaListener
```
Home-button helper: **append** to the existing list, ':'-separated, without replacing it:
```sh
adb shell settings get secure enabled_accessibility_services     # e.g. "null" or "a/b:c/d"
adb shell settings put secure enabled_accessibility_services "<existing>:dev.veil.launcher/dev.veil.launcher.HomeButtonService"
adb shell settings put secure accessibility_enabled 1
```
(If the existing value is `null` or empty, put just Veil's entry.) Veil also re-enables this itself if
Android ever turns it off.

Make Veil the home screen:
```sh
adb shell cmd role add-role-holder --user 0 android.app.role.HOME dev.veil.launcher        # Android 10+
adb shell cmd package set-home-activity --user 0 dev.veil.launcher/.MainActivity           # Android 9 (or as fallback)
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME   # check
```
Veil Ambient as the screensaver (the TV's own timeout setting is untouched):
```sh
adb shell settings put secure screensaver_components dev.veil.launcher/dev.veil.launcher.VeilDream
```
`RECORD_AUDIO` is used by the visualizer (the TV's **output** loudness) and by **voice search** on the
Request screen (the microphone, the remote's, only after the person presses *Voice*; the TV's speech
recognizer, on a Shield Google's, turns it into text and normally sends the audio to its servers). Tell the
person both before you grant it.

Fresh installs need all the grants above. Updates (`install -r`) keep them.

**About the Home button:** on Google TV, Home goes to Google's launcher first, and Veil swaps it out (a brief
flash). That's expected and accepted. On other TVs Home usually opens Veil directly. Don't disable the stock
launcher to "fix" the flash (rule 1).

**Double press of Home** opens the TV's own recent-apps screen (switch to a running app, or close it with
*Dismiss*); this is experimental and may not work on every TV. A single press waits about a third of a
second to tell the two apart. If a TV misbehaves, the helper is `HomeButtonService.kt`.

---

## 5. First run and checks (the person uses the remote)

Guide them through, and confirm each works:
1. **Home** shows Veil. Press Home inside another app: it comes back to Veil.
2. **Connect a tracker**: they pick Trakt, Simkl or MDBList, scan the QR code (or enter the code on the
   service's site), and Continue watching fills in. Then press a show to check it opens the right
   episode in Nuvio (or Stremio, when Nuvio isn't installed).
3. **Artwork and logos** load. If they're missing, the TMDB key is wrong.
4. **Apps row**: Veil starts with TizenTube, Nuvio and Spotify pinned. Any of them that isn't installed is
   simply left out (the row always ends with *All apps*). Help them pin their own apps: *All apps* → hold
   OK on an app → *Pin to Home*. Pinned apps can be moved left/right the same way.
5. **Settings menu** (OK on the "Settings" button top left): theme, autoscroll, weather city, music
   mode (album art + lyrics, or visualizer), visualizer settings.
6. **Privacy pill**: turning it on enables encrypted DNS. Browse a bit to check sites still load. Pause/off work.
7. **Music**: play something in Spotify or similar, then open Ambient (Settings menu → *Start Veil Ambient*, or wait
   for the screensaver). Album art and lyrics, or the visualizer, should react.
8. **Plex** (only if they use it): *Connect a tracker* → Plex, enter the code at plex.tv/link, and Continue
   watching fills in. The pill next to the row title switches to **Recently added** and back. OK on a card
   should start the title in the Plex app after the wait; if Plex says "Server niet bereikbaar / Failed to
   fetch play queue response", raise *Wait before playing* in the Settings menu and try again (Plex then needs
   to be closed and reopened, because it stays stuck after that error).
9. **Request** (only with a Seerr server): the **Request** button next to *veil* opens the screen. Check
   trending loads, the Movies / TV shows, Popular / Latest, streaming-service and *In my library* filters,
   that more titles load by themselves when scrolling to the bottom (36 at a time), a title's details window (description, genres, cast, scores) with the
   actions on its right, and **Choose seasons…** on a show, and **Voice** next to the search box (press it, say a title, and the
   search should run when they stop speaking). Don't press a request button while testing
   unless the person agrees: it creates a real request.
10. **Home double press** (optional): two quick presses of the remote's Home button should show the TV's
    recent apps.

Useful logs: `adb logcat -s VeilViz VeilAudio AndroidRuntime`
- `VeilViz` logs the visualizer's fps every 10 s.
- `VeilAudio` logs beat-tracking stats every 5 s (bpm, how many hits landed on the beat).
- `AndroidRuntime` shows crashes.

---

## 6. Performance on this TV (it's likely slower than the TV Veil was built on)

Veil was tuned on a Google TV Streamer (PowerVR GE9215 GPU). Most TVs are similar or slower. **Measure;
don't guess.** Ask permission first if you'll press remote buttons yourself.

```sh
adb shell dumpsys gfxinfo dev.veil.launcher reset
# ...person uses Home for ~20 s: move between cards, open the Settings menu and the privacy panel...
adb shell dumpsys gfxinfo dev.veil.launcher | grep -E "Total frames|Janky frames|90th|95th"
```
- Home idle is expected to draw only ~30 fps (the slow backdrop drift) with almost no jank.
- Moving between cards around 5% jank is normal.
- Visualizer: `VeilViz` should report ~30 fps.

### Lite mode (only for you, the agent; use only if needed)

There is a build switch for slow TVs. **It is off by default. Keep it off unless measurements show the TV
can't keep up**, for example Home or card navigation consistently above ~15% janky frames, or the visualizer
well below 25 fps. If so, explain the trade-off to the person and ask before turning it on. It changes:
- the visualizer renders at 640x360 instead of 960x540, with a smaller internal buffer (softer, about half the work);
- the slow pan/zoom of the Home backdrop is switched off (the artwork stays still).

To turn it on, add `LITE_MODE=true` to `source/keys.properties`, rebuild, `adb install -r`, then measure
again to confirm it helped. To turn it off, remove the line (or set `false`) and rebuild. Don't mention it in
`README.md`.

Before lite mode, or instead of it, the person can lower **Trails** and **Glow** in the visualizer settings.

---

## 7. Updating Veil

When the person asks to update Veil (the Settings menu says "Update available"), on **the same computer** that
built it (the signing key lives there):

1. **Get the new code:** `git pull` in this repo. If they changed files themselves, show them what
   `git pull` would overwrite and ask first. If they downloaded a ZIP instead of cloning, download the new
   ZIP and copy their `source/keys.properties` and `source/local.properties` into it.
2. **Read what changed** (`git log --oneline` since their version) and tell them in a sentence or two.
   If `keys.properties.example` gained new lines, ask about those keys (the old ones stay as they are).
3. **Build:** `./gradlew assembleRelease` with their existing `keys.properties` (Windows: run again if
   `classes.dex` is locked).
4. **Find the TV:** the wireless-debugging port changes after a reboot, so reconnect with
   `adb mdns services` / `adb connect <ip:port>` if needed.
5. **Ask, then install:** `adb install -r app/build/outputs/apk/release/app-release.apk`.
   An update keeps everything: permissions, home screen, screensaver, tracker sign-in and settings. No grants
   need redoing. Never uninstall to "fix" an update; if it fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`,
   see Troubleshooting and ask before uninstalling (that resets everything).
6. **Check:** Home still opens Veil, and the dot on the "Settings" button is gone.

The *Update available* dot in this version follows https://github.com/Jaarden/Veil-Custom (`REPO` in
`UpdateCheck.kt`), so `git pull` should come from that repository.

The section-0 rules still apply: don't press buttons on the TV while they use it, no force-stop.

---

## 8. If you change the code

Rules that keep Veil smooth on weak TV GPUs:
- **Pre-render anything full-screen.** Live full-screen gradients, `RenderEffect` blur and non-standard
  blend modes (Overlay/Screen) cost 15–35 ms a frame each on TV GPUs. Anything that animates on Home sits on
  pre-rendered images (see `HomeShade`, `FrostCache`, `SlideshowShade`).
- **Read animation values in `graphicsLayer {}` / draw blocks**, not during composition, or the whole screen
  recomposes every frame.
- **Keep `clearAndSetSemantics {}` on the app, dream and dialog roots.** Veil's Home helper is an
  accessibility service, so Compose thinks a screen reader may be on and would rebuild its semantics tree on
  every animated frame (~70% of the main thread).
- **Fade with `CompositingStrategy.ModulateAlpha`**, not offscreen layers.
- **Profile:** Veil is `profileable`.
  `adb shell simpleperf record --app dev.veil.launcher --duration 8 -g -o /data/local/tmp/p.data`, then
  `simpleperf report --sort symbol --children`. Decode names with `app/build/outputs/mapping/release/mapping.txt`.
- The UI is laid out for 1920x1080 at density 2 (dp-based). Other resolutions scale.
- Unit tests: `./gradlew testReleaseUnitTest`.

## Troubleshooting

- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**: it was built on another computer (different signature). Ask,
  then `adb uninstall dev.veil.launcher` and install again. All grants must then be redone.
- **Home button stops opening Veil**: check `enabled_accessibility_services` still contains Veil's entry and
  `accessibility_enabled` is 1 (rule 5: no force-stop).
- **No "now playing" or lyrics**: redo `cmd notification allow_listener ...`.
- **Visualizer missing or flat**: re-grant `RECORD_AUDIO`. If the menu says "not supported on this TV", the
  GPU lacks OpenGL ES 3.0.
- **The TV's ADB port changes after a reboot** (wireless debugging): reconnect with `adb mdns services` /
  `adb connect`.
- **Plex shows "Server niet bereikbaar" / "Failed to fetch play queue response"**: Plex was asked to play
  before it had connected to the server. Raise *Wait before playing* in the Settings menu, close Plex
  (Home double press → *Dismiss*, or restart it) and try again.
- **Request says "Couldn't reach Seerr"**: check `SEERR_URL` (https, no path) and `SEERR_API_KEY`, that the
  TV can reach the server (`adb shell ping <host>`), and that its certificate is valid. Rebuild after
  changing keys. Look for `VeilSeerr` in `adb logcat`.
- **Voice search says it needs microphone access / does nothing**: re-grant `RECORD_AUDIO`
  (`adb shell pm grant dev.veil.launcher android.permission.RECORD_AUDIO`). It also needs the TV's
  speech recognizer (`adb shell cmd package query-services --brief -a android.speech.RecognitionService`)
  and an internet connection. Look for `VeilVoice` in `adb logcat`.
- **No Request button**: `SEERR_URL` or `SEERR_API_KEY` was empty when the app was built.
