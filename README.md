# How to make the NKT JustSay app (APK) — step by step

You only need a web browser and a GitHub account. No coding, no installing anything.
It takes about 15 minutes the first time.

You need 2 files from Claude:
- **NKT-JustSay-App.zip** — the app
- **NKT-JustSay-signing-key.zip** — the app's secret key (keep it private)

---

## Part 1 — Unzip the files

1. Find **NKT-JustSay-App.zip** on your computer. Right-click it → **Extract All** (Windows) or double-click it (Mac).
2. You now have a folder called **NKT-JustSay-App**. Open it. Inside you see: `app`, `.github`, `build.gradle`, `README.md`, and a few more.
   - On a Mac, `.github` is hidden. Press **Cmd + Shift + .** (dot) in Finder to show it. It must be uploaded too.
3. Also unzip **NKT-JustSay-signing-key.zip**. Inside is **signing-secrets.txt**. Keep it open; you'll copy from it in Part 3.

## Part 2 — Put the app on GitHub

1. Go to **github.com** and sign in.
2. Click the **+** at the top right → **New repository**.
3. Name it **nkt-justsay-app**. Choose **Private**. Tick **Add a README file**. Click **Create repository**.
4. On the new page, click **Add file** → **Upload files**.
5. Go back to the open **NKT-JustSay-App** folder. Select **everything inside it** (Ctrl + A, or Cmd + A on a Mac).
   Drag it all onto the GitHub page.
   - Drag what is *inside* the folder, not the folder itself.
   - If GitHub asks to replace README.md, that's fine.
6. Wait until the files finish uploading. Click the green **Commit changes** button.

GitHub now starts building straight away. That first build has no secret key yet; it's fine, ignore it.

## Part 3 — Add the secret key (do this once)

This makes every future version install over the old one, like a normal app update.

1. In your repository, click **Settings** (top menu, far right).
2. On the left, click **Secrets and variables** → **Actions**.
3. Click **New repository secret**.
4. Open **signing-secrets.txt**. It has 4 names, each with a value under it. For each one:
   - **Name**: copy the name (for example `NKT_KEYSTORE_BASE64`)
   - **Secret**: copy the value under it (the first one is one very long line; copy all of it)
   - Click **Add secret**
5. Do it 4 times: `NKT_KEYSTORE_BASE64`, `NKT_KEYSTORE_PASSWORD`, `NKT_KEY_ALIAS`, `NKT_KEY_PASSWORD`.

Never upload signing-secrets.txt or the .jks file to the repository.

## Part 4 — Put in your page address

1. Click the **Code** tab of your repository.
2. Open the folders: `app` → `src` → `main` → `java` → `com` → `nkt` → `justsay` → click **MainActivity.java**.
3. Click the **pencil** icon (top right of the file) to edit.
4. Near the top, find this line:
   `private static final String START_URL = "https://REPLACE-WITH-YOUR-PAGE-ADDRESS/";`
5. Replace only the part inside the quotes with your page's address, for example `https://yourname.github.io/justsay/`.
   - Keep the quotes `" "` and the `;` at the end.
   - The address must start with `https://`.
6. Click the green **Commit changes…** button, then **Commit changes** again.

This starts a new build with your address and your secret key.

## Part 5 — Download the APK

1. Click the **Actions** tab.
2. You'll see **Build APK** runs. The newest one is at the top. A yellow dot means it's still building (about 3–5 minutes).
3. When it turns into a green tick ✓, click it.
4. Scroll down to **Artifacts**. Click **NKT-JustSay-apk**. A zip downloads.
5. Unzip it. Inside is **NKT-JustSay.apk**. That's your app.

A red ✗ means something went wrong. Click the run, then the red step, copy the error text and send it to Claude.

## Part 6 — Install it on the phone

1. Send **NKT-JustSay.apk** to the phone: WhatsApp it to yourself, Google Drive, email or a USB cable.
2. On the phone, tap the file.
3. If the phone says it can't install apps from unknown sources: tap **Settings**, turn on **Allow from this source**, then go back.
4. Tap **Install**, then **Open**.
5. Tap **LET ME IN**. The first time you tap the mic, the phone asks for microphone permission; tap **Allow**.

## Making a new version later

1. Change what you need on GitHub (pencil icon → **Commit changes**).
2. Open `app/build.gradle`, raise `versionCode 1` to `versionCode 2` (then 3, 4…), commit.
3. Download the new APK from **Actions** (Part 5) and install it over the old one. No uninstall needed.

---

## What the app does

- Opens your page full screen. No status bar or navigation bar (swipe from the edge to see them briefly).
- Never shows the page address. Any problem shows only **Can't connect** and a **TRY AGAIN** button; it also retries on its own when the internet comes back.
- Links that lead away from your page do nothing. Tapping the YouTube logo or title won't open YouTube.
- The mic works, using the phone's own speech recognizer.
- Same colours and lettering as the page, with the NKT JustSay icon.

Orbitron font: SIL Open Font License, included in `app/src/main/assets/fonts/OFL.txt`.
