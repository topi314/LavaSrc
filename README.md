[![](https://jitpack.io/v/Topis-Lavalink-Plugins/Spotify-SourceManager.svg)](https://jitpack.io/#Topis-Lavalink-Plugins/Spotify-SourceManager)

# Spotify-SourceManager

A [Lavaplayer](https://github.com/sedmelluq/lavaplayer) SourceManager to lazy load [Spotify](https://www.spotify.com) playlists/albums/songs/artists(top tracks)/search results from [YouTube](https://youtube.com) or other sources

## Installation

Replace x.y.z with the latest version number

### Using in Gradle:
```gradle
repositories {
  maven {
    url 'https://jitpack.io'
  }
}

dependencies {
  implementation 'com.github.Topis-Lavalink-Plugins:Spotify-SourceManager:x.y.z'
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
    <groupId>com.github.Topis-Lavalink-Plugins</groupId>
    <artifactId>Spotify-SourceManager</artifactId>
    <version>x.y.z</version>
  </dependency>
</dependencies>
```

## Usage

```java
AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

// create a new config
SpotifyConfig spotifyConfig = new SpotifyConfig();
spotifyConfig.setClientId(System.getenv("spotifyClientId"));
spotifyConfig.setClientSecret(System.getenv("spotifyClientSecret"));
spotifyConfig.setCountryCode("US");

// create a new SpotifySourceManager with the SpotifyConfig and AudioPlayerManager and register it
playerManager.registerSourceManager(new SpotifySourceManager(spotifyConfig, playerManager));
```

#### All supported Spotify URL types are:

* spsearch:animals architects
* https://open.spotify.com/track/0eG08cBeKk0mzykKjw4hcQ
* https://open.spotify.com/album/7qemUq4n71awwVPOaX7jw4
* https://open.spotify.com/playlist/37i9dQZF1DXaZvoHOvRg3p
* https://open.spotify.com/artist/3ZztVuWxHzNpl0THurTFCv

---
