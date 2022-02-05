[![](https://jitpack.io/v/Topis-Lavalink-Plugins/Source-Managers.svg)](https://jitpack.io/#Topis-Lavalink-Plugins/Source-Managers)

# Source-Managers

A collection [Lavaplayer](https://github.com/sedmelluq/lavaplayer) Source Managers. 
* [Spotify](https://www.spotify.com) playlists/albums/songs/artists(top tracks)/search results
* [Apple Music](https://www.apple.com/apple-music/) playlists/albums/songs/artists/search results

`*tracks are played via YouTube or other configurable sources`

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
  implementation 'com.github.Topis-Lavalink-Plugins:Source-Managers:x.y.z'
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
    <artifactId>Source-Managers</artifactId>
    <version>x.y.z</version>
  </dependency>
</dependencies>
```

## Usage


### Spotify
```java
AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

// create a new config
SpotifyConfig spotifyConfig = new SpotifyConfig();
spotifyConfig.setClientId(System.getenv("spotifyClientId"));
spotifyConfig.setClientSecret(System.getenv("spotifyClientSecret"));
spotifyConfig.setCountryCode("US");

// create a new SpotifySourceManager with the default providers, SpotifyConfig and AudioPlayerManager and register it
playerManager.registerSourceManager(new SpotifySourceManager(null, spotifyConfig, playerManager));
```

### Apple Music
```java
AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

// create a new AppleMusicSourceManager with the standard providers, countrycode and AudioPlayerManager and register it
playerManager.registerSourceManager(new AppleMusicSourceManager(null, "us", playerManager));
```

#### All supported URL types are:

### Spotify
* spsearch:animals architects
* https://open.spotify.com/track/0eG08cBeKk0mzykKjw4hcQ
* https://open.spotify.com/album/7qemUq4n71awwVPOaX7jw4
* https://open.spotify.com/playlist/37i9dQZF1DXaZvoHOvRg3p
* https://open.spotify.com/artist/3ZztVuWxHzNpl0THurTFCv

### Apple Music
* amsearch:animals architects
* https://music.apple.com/cy/album/animals/1533388849?i=1533388859
* https://music.apple.com/cy/album/for-those-that-wish-to-exist/1533388849
* https://music.apple.com/us/playlist/architects-essentials/pl.40e568c609ae4b1eba58b6e89f4cd6a5
* https://music.apple.com/cy/artist/architects/182821355


---
