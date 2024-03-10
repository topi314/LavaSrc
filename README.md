[![](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmaven.topi.wtf%2Freleases%2Fcom%2Fgithub%2FTopiSenpai%2FLavaSrc%2Flavasrc%2Fmaven-metadata.xml)](https://maven.topi.wtf/#/releases/com/github/TopiSenpai/LavaSrc/lavasrc)

# LavaSrc

A collection of additional [Lavaplayer v2](https://github.com/sedmelluq/lavaplayer), [LavaSearch](https://github.com/topi314/LavaSearch) & [LavaLyrics](https://github.com/topi314/LavaLyrics) Audio Source Managers and [Lavalink v4](https://github.com/lavalink-devs/Lavalink) Plugin.
* [Spotify*](https://www.spotify.com) playlists/albums/songs/artists(top tracks)/search results/[LavaSearch](https://github.com/topi314/LavaSearch)/[LavaLyrics](https://github.com/topi314/LavaLyrics)
* [Apple Music*](https://www.apple.com/apple-music/) playlists/albums/songs/artists/search results/[LavaSearch](https://github.com/topi314/LavaSearch)(Big thx to [ryan5453](https://github.com/ryan5453) for helping me)
* [Deezer](https://www.deezer.com) playlists/albums/songs/artists/search results/[LavaSearch](https://github.com/topi314/LavaSearch)/[LavaLyrics](https://github.com/topi314/LavaLyrics)(Big thx to [ryan5453](https://github.com/ryan5453) and [melike2d](https://github.com/melike2d) for helping me)
* [Yandex Music](https://music.yandex.ru) playlists/albums/songs/artists/podcasts/search results(Thx to [AgutinVBoy](https://github.com/agutinvboy) for implementing it)
* [Flowery TTS](https://flowery.pw/docs/flowery/synthesize-v-1-tts-get) (Thx to [bachtran02](https://github.com/bachtran02) for implementing it)
* [YouTube](https://youtube.com) & [YouTubeMusic](https://music.youtube.com/) [LavaSearch](https://github.com/topi314/LavaSearch)/[LavaLyrics](https://github.com/topi314/LavaLyrics)  (Thx to [DRSchlaubi](https://github.com/DRSchlaubi) for helping me)

`*tracks are searched & played via YouTube or other configurable sources`

> [!IMPORTANT]
> For LavaSrc v3 (Lavaplayer v1 & Lavalink v3) look [here](https://github.com/topi314/LavaSrc/tree/v3-legacy) 

## Summary

* [Lavaplayer Usage](#lavaplayer-usage)
* [Lavalink Usage](#lavalink-usage)
* [Supported URLs and Queries](#supported-urls-and-queries)

## Lavaplayer Usage

Replace x.y.z with the latest version number

Snapshot builds are available in https://maven.topi.wtf/snapshots with the short commit hash as the version

### Using in Gradle:

<details>
<summary>Gradle</summary>

### 
```gradle
repositories {
  maven {
    url "https://maven.topi.wtf/releases"
  }
}

dependencies {
  implementation "com.github.topi314.lavasrc:lavasrc:x.y.z"
  implementation "com.github.topi314.lavasrc:lavasrc-protocol:x.y.z"
}
```
</details>

### Using in Maven:

<details>
<summary>Maven</summary>

```xml
<repositories>
  <repository>
    <id>TopiWTF-releases</id>
    <name>Topis Maven Repo</name>
    <url>https://maven.topi.wtf/releases</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.github.topi314.lavasrc</groupId>
    <artifactId>lavasrc</artifactId>
    <version>x.y.z</version>
  </dependency>
  <dependency>
      <groupId>com.github.topi314.lavasrc</groupId>
      <artifactId>lavasrc-protocol-jvm</artifactId>
      <version>x.y.z</version>
  </dependency>
</dependencies>
```
</details>

### Usage

For all supported urls and queries see [here](#supported-urls-and-queries)

#### Spotify

To get a Spotify clientId & clientSecret you must go [here](https://developer.spotify.com/dashboard) and create a new application.

```java
AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

// create a new SpotifySourceManager with the default providers, clientId, clientSecret, spDc, countryCode and AudioPlayerManager and register it
playerManager.registerSourceManager(new SpotifySourceManager(null, clientId, clientSecret, spDc, countryCode, playerManager));
```

<details>
<summary>How to get sp dc cookie</summary>

1. Go to https://open.spotify.com
2. Open DevTools and go to the Application tab
3. Copy the value of the `sp_dc` cookie

</details>

#### Apple Music
```java
AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

// create a new AppleMusicSourceManager with the standard providers, apple music api token, countrycode and AudioPlayerManager and register it
playerManager.registerSourceManager(new AppleMusicSourceManager(null, mediaAPIToken , "us", playerManager));
```

<details>
<summary>How to get media api token without Apple developer account</summary>

1. Go to https://music.apple.com
2. Open DevTools and go to the Debugger tab
3. Search with this regex `"(?<token>(ey[\w-]+)\.([\w-]+)\.([\w-]+))"` in all `index-*.js` files
4. Copy the token from the source code

</details>

Alternatively, you can
follow [this guide](https://developer.apple.com/help/account/configure-app-capabilities/create-a-media-identifier-and-private-key/)

#### Deezer

```java
AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

// create a new DeezerSourceManager with the master decryption key and register it
playerManager.registerSourceManager(new DeezerSourceManager("...");
```

#### Yandex Music

<details>
<summary>How to get access token</summary>

1. (Optional) Open DevTools in your browser and on the Network tab enable trotlining.
2. Go to https://oauth.yandex.ru/authorize?response_type=token&client_id=23cabbbdc6cd418abb4b39c32c41195d
3. Authorize and grant access
4. The browser will redirect to the address like `https://music.yandex.ru/#access_token=AQAAAAAYc***&token_type=bearer&expires_in=31535645`.
   Very quickly there will be a redirect to another page, so you need to have time to copy the link. ![image](https://user-images.githubusercontent.com/68972811/196124196-a817b828-3387-4f70-a2b2-cdfdc71ce1f2.png)
5. Your accessToken, what is after `access_token`.

Token expires in 1 year. You can get a new one by repeating the steps above.

## Important information
Yandex Music is very location-dependent. You should either have a premium subscription or be located in one of the following countries:
- Azerbaijan
- Armenia
- Belarus
- Georgia
- Kazakhstan
- Kyrgyzstan
- Moldova
- Russia
- Tajikistan
- Turkmenistan
- Uzbekistan

Else you will only have access to podcasts.
</details>

```java
AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

// create a new YandexMusicSourceManager with the access token and register it
playerManager.registerSourceManager(new YandexMusicSourceManager("...");
```

#### Flowery Text-to-Speech

Get list of all voices and languages supported [here](https://api.flowery.pw/v1/tts/voices)

```java
AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

// create a new FloweryTTSSourceManager with a valid voice and register it
playerManager.registerSourceManager(new FloweryTTSSourceManager("..."));
```

---

## Lavalink Usage

This plugin requires Lavalink `v4` or greater

To install this plugin either download the latest release and place it into your `plugins` folder or add the following into your `application.yml`

> **Note**
> For a full `application.yml` example see [here](application.example.yml)

Replace x.y.z with the latest version number
```yaml
lavalink:
  plugins:
    - dependency: "com.github.topi314.lavasrc:lavasrc-plugin:x.y.z"
      repository: "https://maven.lavalink.dev/releases" # this is optional for lavalink v4.0.0-beta.5 or greater
      snapshot: false # set to true if you want to use snapshot builds (see below)
```

Snapshot builds are available in https://maven.lavalink.dev/snapshots with the short commit hash as the version

### Configuration

For all supported urls and queries see [here](#supported-urls-and-queries)

To get your Spotify clientId, clientSecret go [here](https://developer.spotify.com/dashboard/applications) & then copy them into your `application.yml` like the following.

To get your Spotify spDc cookie go [here](#spotify)

To get your Apple Music api token go [here](#apple-music)

To get your Yandex Music access token go [here](#yandex-music)

(YES `plugins` IS AT ROOT IN THE YAML)
```yaml
plugins:
  lavasrc:
    providers: # Custom providers for track loading. This is the default
      # - "dzisrc:%ISRC%" # Deezer ISRC provider
      # - "dzsearch:%QUERY%" # Deezer search provider
      - "ytsearch:\"%ISRC%\"" # Will be ignored if track does not have an ISRC. See https://en.wikipedia.org/wiki/International_Standard_Recording_Code
      - "ytsearch:%QUERY%" # Will be used if track has no ISRC or no track could be found for the ISRC
      #  you can add multiple other fallback sources here
    sources:
      spotify: false # Enable Spotify source
      applemusic: false # Enable Apple Music source
      deezer: false # Enable Deezer source
      yandexmusic: false # Enable Yandex Music source
      flowerytts: false # Enable Flowery TTS source
      youtube: true # Enable YouTube search source (https://github.com/topi314/LavaSearch)
    spotify:
      clientId: "your client id"
      clientSecret: "your client secret"
      # spDc: "your sp dc cookie" # the sp dc cookie used for accessing the spotify lyrics api
      countryCode: "US" # the country code you want to use for filtering the artists top tracks. See https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
      playlistLoadLimit: 6 # The number of pages at 100 tracks each
      albumLoadLimit: 6 # The number of pages at 50 tracks each
    applemusic:
      countryCode: "US" # the country code you want to use for filtering the artists top tracks and language. See https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
      mediaAPIToken: "your apple music api token" # apple music api token
      # or specify an apple music key
      keyID: "your key id"
      teamID: "your team id"
      musicKitKey: |
        -----BEGIN PRIVATE KEY-----
        your key
        -----END PRIVATE KEY-----      
      playlistLoadLimit: 6 # The number of pages at 300 tracks each
      albumLoadLimit: 6 # The number of pages at 300 tracks each
    deezer:
      masterDecryptionKey: "your master decryption key" # the master key used for decrypting the deezer tracks. (yes this is not here you need to get it from somewhere else)
    yandexmusic:
      accessToken: "your access token" # the token used for accessing the yandex music api. See https://github.com/TopiSenpai/LavaSrc#yandex-music
    flowerytts:
      voice: "default voice" # (case-sensitive) get default voice from here https://api.flowery.pw/v1/tts/voices
      translate: false # whether to translate the text to the native language of voice
      silence: 0 # the silence parameter is in milliseconds. Range is 0 to 10000. The default is 0.
      speed: 1.0 # the speed parameter is a float between 0.5 and 10. The default is 1.0. (0.5 is half speed, 2.0 is double speed, etc.)
      audioFormat: "mp3" # supported formats are: mp3, ogg_opus, ogg_vorbis, aac, wav, and flac. Default format is mp3
    youtube:
      countryCode: "US" # the country code you want to use for searching lyrics via ISRC. See https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
```

### Plugin Info

LavaSrc adds the following fields to tracks & playlists in Lavalink

#### Track

| Field            | Type    | Description                    |
|------------------|---------|--------------------------------|
| albumName        | ?string | The name of the album          |
| albumArtUrl      | ?string | The url of the album art       |
| artistUrl        | ?string | The url of the artist          |
| artistArtworkUrl | ?string | The url of the artist artwork  |
| previewUrl       | ?string | The url of the preview         |
| isPreview        | bool    | Whether the track is a preview |

#### Playlist

| Field       | Type                             | Description                                |
|-------------|----------------------------------|--------------------------------------------|
| type        | [Playlist Type](#playlist-types) | The type of the playlist                   |
| url         | ?string                          | The url of the playlist                    |
| artworkUrl  | ?string                          | The url of the playlist artwork            |
| author      | ?string                          | The author of the playlist                 |
| totalTracks | ?int                             | The total number of tracks in the playlist |

#### Playlist Types

| Type            | Description                                |
|-----------------|--------------------------------------------|
| album           | The playlist is an album                   |
| playlist        | The playlist is a playlist                 |
| artist          | The playlist is an artist                  |
| recommendations | The playlist is a recommendations playlist |

---

## Supported URLs and Queries

### Spotify
* `spsearch:animals architects` (check out [Spotify Search Docs](https://developer.spotify.com/documentation/web-api/reference/search) for advanced search queries like isrc & co)
* `sprec:seed_artists=3ZztVuWxHzNpl0THurTFCv,4MzJMcHQBl9SIYSjwWn8QW&seed_genres=metalcore&seed_tracks=5ofoB8PFmocBXFBEWVb6Vz,6I5zXzSDByTEmYZ7ePVQeB`
  (check out [Spotify Recommendations Docs](https://developer.spotify.com/documentation/web-api/reference/get-recommendations) for the full query parameter list)
* https://open.spotify.com/track/0eG08cBeKk0mzykKjw4hcQ
* https://open.spotify.com/album/7qemUq4n71awwVPOaX7jw4
* https://open.spotify.com/playlist/7HAO9R9v203gkaPAgknOMp
* https://open.spotify.com/artist/3ZztVuWxHzNpl0THurTFCv

(including new regional links like https://open.spotify.com/intl-de/track/0eG08cBeKk0mzykKjw4hcQ)

### Apple Music
* `amsearch:animals architects`
* https://music.apple.com/cy/album/animals/1533388849?i=1533388859
* https://music.apple.com/cy/album/for-those-that-wish-to-exist/1533388849
* https://music.apple.com/us/playlist/architects-essentials/pl.40e568c609ae4b1eba58b6e89f4cd6a5
* https://music.apple.com/cy/artist/architects/182821355

### Deezer
* `dzsearch:animals architects`
* `dzisrc:USEP42058010`
* https://deezer.page.link/U6BTQ2Q1KpmNt2yh8
* https://www.deezer.com/track/1090538082
* https://www.deezer.com/album/175537082
* https://www.deezer.com/playlist/8164349742
* https://www.deezer.com/artist/159126

### Yandex Music
* `ymsearch:animals architects`
* https://music.yandex.ru/album/13886032/track/71663565
* https://music.yandex.ru/album/13886032
* https://music.yandex.ru/track/71663565
* https://music.yandex.ru/users/yamusic-bestsongs/playlists/701626
* https://music.yandex.ru/artist/701626

### Flowery TTS
You can ready about all the available options [here](https://flowery.pw/docs/flowery/synthesize-v-1-tts-get),
a list of available voices is [here](https://api.flowery.pw/v1/tts/voices)

* `ftts://hello%20world`
* `ftts://hello%20world?audio_format=ogg_opus&translate=False&silence=1000&speed=1.0`
---
