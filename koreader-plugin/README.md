# BookLore KOReader Plugin

A KOReader plugin that syncs your reading statistics to your BookLore server, enabling calendar view and detailed reading analytics.

## Features

- 📊 Syncs complete reading history (page-by-page session data)
- 📚 Syncs book metadata from KOReader
- 🔄 Automatic sync on device suspend (optional)
- 🔐 Secure authentication using KOReader sync credentials
- 📱 Manual sync via menu or gestures

## Installation

1. Copy the `booklore.koplugin` folder to your KOReader plugins directory:
   - **Kobo**: `/mnt/onboard/.adds/koreader/plugins/`
   - **Kindle**: `/mnt/us/koreader/plugins/`
   - **Android**: `/sdcard/koreader/plugins/`

2. Restart KOReader

3. Go to **Tools → BookLore → Configure** and enter:
   - Your BookLore server URL (e.g., `https://booklore.example.com`)
   - Your KOReader sync username 
   - Your KOReader sync password)

## Usage

### Manual Sync
- Go to **Tools → BookLore → Sync now**
- Or set up a gesture for quick sync

### Automatic Sync
- Enable **Tools → BookLore → Sync on suspend**
- Plugin will automatically sync when you close your KOReader device

## What Data is Synced?

The plugin syncs:
- **Reading Sessions**: Every page you've read with timestamp and duration
- This data enables BookLore to show you:
  - Calendar view of your reading activity
  - Which days you read specific books
  - Reading time statistics
  - Progress over time

## Requirements

- BookLore server with KOReader statistics support
- KOReader user account in BookLore
- WiFi connection (for sync)

## Privacy

- All data is sent directly to your BookLore server
- No third-party services are used
- Credentials are stored locally on your device

## Troubleshooting

### Sync fails
- Check your server URL is correct (no trailing slash)
- Verify your KOReader credentials in BookLore
- Ensure WiFi is connected
- Check logs: **Tools → More tools → Logs**

### Books or stats not matching
- Ensure books exist in your BookLore library. The import process ignores books that don't exist in your BookLore library
- Books are matched by MD5 hash, so you need to pull in the book from your BookLore server
- Import books to BookLore before syncing statistics

## Based on KOInsight

This plugin is inspired by and based on the excellent [KOInsight](https://github.com/GeorgeSG/KoInsight) project by GeorgeSG.

## License

Same license as KOReader (AGPL-3.0)
