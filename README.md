[![](https://jitpack.io/v/TopiSenpai/LavaSrc.svg)](https://jitpack.io/#TopiSenpai/LavaSrc)

# LavaSrc

A collection of additional [Lavaplayer](https://github.com/sedmelluq/lavaplayer) Audio Source Managers and Lavalink Plugin.
* [Spotify*](https://www.spotify.com) playlists/albums/songs/artists(top tracks)/search results
* [Apple Music*](https://www.apple.com/apple-music/) playlists/albums/songs/artists/search results(Big thx to [aleclol](https://github.com/aleclol) for helping me)
* [Deezer](https://www.deezer.com) playlists/albums/songs/artists/search results(Big thx to [ryan5453](https://github.com/ryan5453) and [melike2d](https://github.com/melike2d) for helping me)

`*tracks are searched & played via YouTube or other configurable sources`

## Summary

* [Lavaplayer Usage](#lavalink-usage)
* [Lavalink Usage](#lavalink-usage)
* [Supported URLs and Queries](#supported-urls-and-queries)

## Lavaplayer Usage

Replace x.y.z with the latest version number

### Using in Gradle:
```gradle
repositories {
  maven {
    url "https://jitpack.io"
  }
}

dependencies {
  implementation "com.github.TopiSenpai.LavaSrc:lavasrc:x.y.z"
}
```

### Using in Maven:
```xml
<repositories>
  <repository>
    <id>jitpack</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.github.TopiSenpai.LavaSrc</groupId>
    <artifactId>lavasrc</artifactId>
    <version>x.y.z</version>
  </dependency>
</dependencies>
```

### Usage

For all supported urls and queries see [here](#supported-urls-and-queries)

#### Spotify

To get a Spotify clientId & clientSecret you must go [here](https://developer.spotify.com/dashboard) and create a new application.

```java
AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

// create a new SpotifySourceManager with the default providers, clientId, clientSecret and AudioPlayerManager and register it
playerManager.registerSourceManager(new SpotifySourceManager(null, clientId, clientSecret, playerManager));
```

#### Apple Music
```java
AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

// create a new AppleMusicSourceManager with the standard providers, countrycode and AudioPlayerManager and register it
playerManager.registerSourceManager(new AppleMusicSourceManager(null, "us", playerManager));
```

#### Deezer
```java
AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

// create a new DeezerSourceManager with the master decryption key and register it
playerManager.registerSourceManager(new DeezerSourceManager("...", playerManager));
```

---

## Lavalink Usage

To install this plugin either download the latest release and place it into your `plugins` folder or add the following into your `application.yml`

Lavalink supports plugins only in v3.5 and above


Replace x.y.z with the latest version number
```yaml
lavalink:
  plugins:
    - dependency: "com.github.TopiSenpai.LavaSrc:lavasrc-plugin:x.y.z"
      repository: "https://jitpack.io"
```

### Configuration

For all supported urls and queries see [here](#supported-urls-and-queries)

To get your Spotify clientId & clientSecret go [here](https://developer.spotify.com/dashboard/applications) & then copy them into your `application.yml` like the following.

(YES `plugins` IS AT ROOT IN THE YAML)
```yaml
plugins:
  lavasrc:
    providers: # Custom providers for track loading. This is the default
      - "ytsearch:\"%ISRC%\"" # Will be ignored if track does not have an ISRC. See https://en.wikipedia.org/wiki/International_Standard_Recording_Code
      - "ytsearch:%QUERY%" # Will be used if track has no ISRC or no track could be found for the ISRC
    # - "dzisrc:%ISRC%" # Deezer ISRC provider
    # - "scsearch:%QUERY%" you can add multiple other fallback sources here
    sources:
      spotify: true # Enable Spotify source
      applemusic: true # Enable Apple Music source
      deezer: true # Enable Deezer source
    spotify:
      clientId: "your client id"
      clientSecret: "your client secret"
    applemusic:
      countryCode: "US" # the country code you want to use for filtering the artists top tracks and language. See https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
    deezer:
      masterDecryptionKey: "your master decryption key" # the master key used for decrypting the deezer tracks. (yes this is not here you need to get it from somewhere else)
```

---

## Supported URLs and Queries

### Spotify
* `spsearch:animals architects`
* `sprec:seed_artists=3ZztVuWxHzNpl0THurTFCv,4MzJMcHQBl9SIYSjwWn8QW&seed_genres=metalcore&seed_tracks=5ofoB8PFmocBXFBEWVb6Vz,6I5zXzSDByTEmYZ7ePVQeB`
  (check out [Spotify Recommendations Docs](https://developer.spotify.com/documentation/web-api/reference/browse/get-recommendations/) for the full query parameter list)
* https://open.spotify.com/track/0eG08cBeKk0mzykKjw4hcQ
* https://open.spotify.com/album/7qemUq4n71awwVPOaX7jw4
* https://open.spotify.com/playlist/37i9dQZF1DXc6IFF23C9jj
* https://open.spotify.com/artist/3ZztVuWxHzNpl0THurTFCv

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

---
