# 卡片背景

A wallet card (邮箱 / 手机号 / 微信 QQ) can wear a picture instead of its colour. The colour stays (map, glow,
threads, shadows); only the big wallet card face changes. Map mini cards keep the colour.

## Data (synced, all clients keep unknown keys as before)

Identity gets two optional string keys, written only when set:

- `bg`: id of an **official** background shipped with the app (e.g. `"b1"`). Ids are never reused.
- `pic`: id of the user's **own** picture = first 32 lowercase hex chars of SHA-256 of the stored JPEG bytes.

At most one of the two is set. Picking an official one clears `pic`, picking an own picture clears `bg`,
「无」 clears both. A change to either bumps `updatedAt` like any other edit.

What a card shows: own picture if `pic` is set **and the file exists on this device**; else official `bg` if this
client knows that id; else the colour. Unknown / missing never errors.

## Official backgrounds

The pictures live once, in the repo root folder `backgrounds/<id>.jpg` (the web serves it; ios.yml copies it
into the app bundle as `backgrounds/`, like `icons/`; Android's Gradle build adds it as an assets dir so it is
`assets/backgrounds/<id>.jpg`). One registry per client, same ids, same order, same names:
- web: `BGS` in app.js
- iOS: `OfficialBackgrounds.all` (native/Backgrounds.swift)
- Android: `OfficialBackgrounds.all` (Backgrounds.kt)

Empty for now; the pictures come later. With an empty registry the picker only shows 「无」 and 「自己的图片」.

## Own pictures: storage (per device)

Processed on pick: center-cropped to the card ratio 1.586:1, scaled to 1080 px wide (web 900), JPEG quality 0.82.
Stored by id, so two cards with the same picture share one file:
- web: `localStorage['atlas.pic.<id>']` = data URL
- iOS: `Application Support/pics/<id>.jpg`
- Android: `filesDir/pics/<id>.jpg`

On launch, files/keys whose id no card references any more are deleted (not at delete time, so 撤销 still works).
Own pictures are not part of export / backup files.

## Own pictures: sync (WebDAV only, iOS + Android)

Gist / Gitee never carry pictures (file size), and the web is gist-only, so the web never syncs pictures.

On WebDAV, after the data file is read-merged-written successfully:
- for every `pic` referenced by the merged data:
  - local file exists and this id is not in the "uploaded" set for this server → PUT
    `<server>/AccountAtlas/pics/<id>.bin`; on 404/409 MKCOL `AccountAtlas/pics/` once and retry. Add id to the set.
  - local file missing → GET `<server>/AccountAtlas/pics/<id>.bin`; 404 → skip quietly (the other device hasn't
    uploaded yet); otherwise decrypt and store, then refresh the cards.
- Uploaded set: persisted per sync config, cleared when the sync config changes.
- Picture failures never turn the sync status into an error; they are retried on the next run.
- `.bin` = raw bytes `nonce(12) || ciphertext || tag(16)`, AES-256-GCM with the same derived sync key as the data
  file. Content-addressed, so a picture is never overwritten; no remote cleanup for now.

## UI

Edit identity form, after 卡面 (colours): section 「卡片背景」 with a grid of card-shaped tiles:
「无」, each official background, the current own picture (if any), and 「自己的图片 / 换一张」 (photo picker).
The form's live card preview shows the choice. Saved with the form (cancel changes nothing).

Hint under the grid:
- web: 「自己选的图片只保存在这个浏览器里，网页版不同步图片。」
- apps, sync is WebDAV: 「自己选的图片会加密同步到你的 WebDAV。」
- apps, otherwise: 「自己选的图片只保存在这台设备上。同步方式改成 WebDAV 后，图片也能同步到其他设备。」
(prefix 「官方背景会跟着同步；」 when the registry is not empty)

Text on a picture card stays white; a scrim darkens the top (~38% black → clear by 38%) and the bottom
(clear from 48% → ~55% black) so the label, count and value stay readable on any picture.
