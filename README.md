# Spotless

Opens Spotify links (tracks, albums, playlists) in YouTube Music. No Spotify account or Google sign-in required.

> **Status:** Works as of October 2026. The app relies on unofficial interfaces at both Spotify and YouTube Music, and will likely need updates as they change. See [When it stops working](#when-it-stops-working).

## How it works

1. **Catch the link:** `open.spotify.com`, `spotify.link`, `spoti.fi`, `spotify:` URIs, or *Share → Spotless*.
2. **Read Spotify:** The public embed page (`open.spotify.com/embed/...`) contains title, artists and duration in `__NEXT_DATA__`. The Web API is not used, since it requires Premium in development mode.
3. **Match:** Anonymous search against YT Music's internal API (InnerTube, `WEB_REMIX`). Results are scored on title similarity, artist, duration and type (audio track > official video > user upload), with penalties for "sped up", "live", "cover" and similar.
4. **Open:** A single track opens as `music.youtube.com/watch?v=…`. Multiple tracks go through `youtube.com/watch_videos?video_ids=…`, which yields an anonymous queue ID (`TLGG…`) that opens in YT Music.

## Setup on the phone

1. Install `spotless.apk` (sideload).
2. Open the app and tap **Open "Open by default"**. Choose **Add link** and tick all three domains. Android 12+ requires this for apps that don't own the domain.
3. If you have Spotify installed, turn the same setting off for Spotify.

*Share → Spotless* works without any of this.

## Settings (with defaults)

| Setting | Default | Note |
|---|---|---|
| Max queue | 50 | YouTube's anonymous queue takes at most 50 |
| Region | device locale (fallback `US`) | Affects which releases are available |
| Cache | on | Spotify ID → video ID in SQLite; can be cleared in the app |

## Known limitations

- Playlists are truncated at **100 tracks** (embed) and then **50** (queue).
- Without ISRC, matching is heuristic. Tested: 10/10 on an album, 49/50 on a 50-track pop playlist.
- A 50-track playlist takes ~10 s the first time; repeated tracks come from the cache.
- Artist and podcast links give a YT Music search, not a 1:1 translation.

## When it stops working

Run `./gradlew testDebugUnitTest -Dlive=true --rerun`. The tests show which part broke:

| Symptom | Likely cause | Where to fix |
|---|---|---|
| Error about `__NEXT_DATA__` or empty track list | Spotify changed the embed page | `EmbedParser.kt` |
| No matches, or HTTP errors from YT Music | InnerTube response or client version changed | `YtmParser.kt`, `YtmClient.kt` |
| Matches the wrong song | Scoring needs tuning | `Matcher.kt` |
| Queue doesn't open, only the first track | `watch_videos` changed or got blocked | `Resolver.anonymousListId` |

The offline tests run against recorded responses in `app/src/test/resources/`. When the formats change, replace the fixtures with fresh responses and update the parsers until the tests pass.

## Build

```sh
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleRelease                         # app/build/outputs/apk/release/
./gradlew testDebugUnitTest                       # offline tests against fixtures
./gradlew testDebugUnitTest -Dlive=true --rerun   # end-to-end against real services
```

The release build is signed with the debug key (for sideloading). Set up your own `signingConfig` if you want to distribute it.

## License

MIT, see [LICENSE](LICENSE).
