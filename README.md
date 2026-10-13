# Spotless

Åpner Spotify-lenker (spor, album, spillelister) i YouTube Music. Krever verken Spotify-konto eller Google-innlogging.

> **Status:** Virker per oktober 2026. Appen bygger på uoffisielle grensesnitt hos både Spotify og YouTube Music, og vil sannsynligvis trenge oppdateringer etter hvert som de endrer seg. Se [Når det slutter å virke](#når-det-slutter-å-virke).

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

## Når det slutter å virke

Kjør `./gradlew testDebugUnitTest -Dlive=true --rerun`. Testene viser hvilken del som brakk:

| Symptom | Sannsynlig årsak | Hvor du fikser |
|---|---|---|
| Feilmelding om `__NEXT_DATA__` eller tom tracklist | Spotify har endret embed-siden | `EmbedParser.kt` |
| Ingen treff, eller HTTP-feil fra YT Music | InnerTube-svaret eller klientversjonen er endret | `YtmParser.kt`, `YtmClient.kt` |
| Matcher feil sang | Scoringen trenger justering | `Matcher.kt` |
| Kø åpnes ikke, bare første spor | `watch_videos` er endret eller sperret | `Resolver.anonymousListId` |

Offline-testene kjører mot lagrede svar i `app/src/test/resources/`. Når formatene endrer seg, bytter du ut fixturene med nye svar og oppdaterer parserne til testene går gjennom.

## Bygg

```sh
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleRelease                         # app/build/outputs/apk/release/
./gradlew testDebugUnitTest                       # offline-tester mot fixtures
./gradlew testDebugUnitTest -Dlive=true --rerun   # ende-til-ende mot ekte tjenester
```

Release-bygget signeres med debug-nøkkelen (for sideload). Sett opp din egen `signingConfig` om du vil distribuere det.

## Lisens

MIT, se [LICENSE](LICENSE).
