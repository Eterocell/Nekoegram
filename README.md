# This project is archived since the original Nekogram already fullfill all my need. If you are using any Nekoegram builds, consider migrate to the official [Nekogram](https://nekogram.app/) client.

---

# Nekoegram
Nekoegram is a third-party Telegram client with not many but useful modifications, based on [Nekogram](https://gitlab.com/Nekogram/Nekogram).

- Source code: https://github.com/Eterocell/Nekoegram
- Downloads: https://github.com/Eterocell/Nekoegram/releases/latest
- Feedback: https://github.com/Eterocell/Nekoegram/issues

## New features compare to original Nekogram

- ~"Mark as read" cell for all tabs~ (Telegram already implemented this since 9.5.5)

## API, Protocol documentation

Telegram API manuals: https://core.telegram.org/api

MTProto protocol manuals: https://core.telegram.org/mtproto

## Compilation Guide

1. Download the Nekoegram source code ( `git clone https://github.com/Eterocell/Nekoegram.git` )
1. Fill out storeFile, storePassword, keyAlias, keyPassword in local.properties to access your release.keystore
1. Go to https://console.firebase.google.com/, create two android apps with application IDs com.eterocell.nekoegram and com.eterocell.nekoegram.beta, turn on firebase messaging and download `google-services.json`, which should be copied into `TMessagesProj` folder.
1. Open the project in the Studio (note that it should be opened, NOT imported).
1. Fill out values in `TMessagesProj/src/main/java/com/eterocell/nekoegram/Extra.java` – there’s a link for each of the variables showing where and which data to obtain.
1. Generate `TMessagesProj/jni/integrity/genuine.h` - https://github.com/brevent/genuine
1. You are ready to compile Nekoegram.
