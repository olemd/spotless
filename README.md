# Spotless

Åpner Spotify-lenker (spor, album, spillelister) i YouTube Music. Krever verken Spotify-konto eller Google-innlogging.

## Slik virker det

1. **Fange lenken:** `open.spotify.com`, `spotify.link`, `spoti.fi`, `spotify:`-URI-er, eller *Del → Spotless*.
2. **Lese Spotify:** Den offentlige embed-siden (`open.spotify.com/embed/...`) har tittel, artister og varighet i `__NEXT_DATA__`. Web API-et brukes ikke, siden det krever Premium i dev mode.
3. **Matche:** Anonymt søk mot YT Music sitt interne API (InnerTube, `WEB_REMIX`). Treffene scores på tittellikhet, artist, varighet og type (lydspor > offisiell video > brukeropplasting), med straff for «sped up», «live», «cover» o.l.
4. **Åpne:** Ett spor åpnes som `music.youtube.com/watch?v=…`. Flere spor går via `youtube.com/watch_videos?video_ids=…`, som gir en anonym kø-ID (`TLGG…`) som åpnes i YT Music.

## Oppsett på telefonen

1. Installer `spotless.apk` (sideload).
2. Åpne appen og trykk **Åpne «Åpne som standard»**. Velg **Legg til lenker** og huk av alle tre domenene. Android 12+ krever dette for apper som ikke eier domenet.
3. Har du Spotify installert, må du skru av det samme for Spotify.

*Del → Spotless* fungerer uten noe av dette.

## Innstillinger (med defaults)

| Innstilling | Default | Merknad |
|---|---|---|
| Maks kø | 50 | YouTube sin anonyme kø tar maks 50 |
| Region | NO | Påvirker hvilke utgivelser som finnes |
| Cache | på | Spotify-ID → video-ID i SQLite; kan tømmes i appen |

## Kjente begrensninger

- **Uoffisielt hele veien:** Både Spotify-embed og InnerTube kan endres uten varsel. Parserne er isolert (`EmbedParser`, `YtmParser`) og har tester mot lagrede svar.
- Spillelister kuttes på **100 spor** (embed) og deretter **50** (kø).
- Uten ISRC er matching heuristikk. Testet: 10/10 på et album, 49/50 på en 50-spors pop-liste.
- En 50-spors liste tar ~10 s første gang; gjentatte spor hentes fra cache.
- Artist- og podkastlenker gir et YT Music-søk, ikke en 1:1-oversettelse.

## Bygg

```sh
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleRelease                         # app/build/outputs/apk/release/
./gradlew testDebugUnitTest                       # offline-tester mot fixtures
./gradlew testDebugUnitTest -Dlive=true --rerun   # ende-til-ende mot ekte tjenester
```

Release-bygget signeres med debug-nøkkelen (for sideload). Sett opp din egen `signingConfig` om du vil distribuere det.
