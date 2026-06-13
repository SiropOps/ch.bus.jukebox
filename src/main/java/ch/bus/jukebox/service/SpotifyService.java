package ch.bus.jukebox.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import se.michaelthelin.spotify.model_objects.special.PlaybackQueue;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRefreshRequest;
import se.michaelthelin.spotify.requests.data.search.SearchItemRequest;

@Service
public class SpotifyService {

  private static Logger log = LoggerFactory.getLogger(SpotifyService.class);

  private Instant EXPIRES_AT;
  private String ACCESS_TOKEN;
  private List<Map<String, String>> CACHED_QUEUE = new ArrayList<>();
  private Instant LAST_QUEUE_FETCH_TIME = Instant.EPOCH;
  private final long CACHE_DURATION_SECONDS = 15;

  private final SpotifyApi spotifyApi;

  public SpotifyService(SpotifyApi spotifyApi) {
    this.spotifyApi = spotifyApi;
  }


  private void ensureValidAccessToken() {
    if (Optional.ofNullable(EXPIRES_AT).isEmpty()
        || EXPIRES_AT.isBefore(Instant.now().minusSeconds(60))) {
      try {
        AuthorizationCodeRefreshRequest request = spotifyApi.authorizationCodeRefresh().build();
        AuthorizationCodeCredentials credentials = request.execute();

        EXPIRES_AT = Instant.now().plusSeconds(credentials.getExpiresIn());
        ACCESS_TOKEN = credentials.getAccessToken();

        // Optionnel : enregistrer le nouveau refresh_token s’il a changé
        log.info("Access token refreshed.");
      } catch (Exception e) {
        throw new RuntimeException("Failed to refresh access token", e);
      }
    }

    spotifyApi.setAccessToken(ACCESS_TOKEN);
  }

  public List<Map<String, String>> searchTracks(String query) {
    this.ensureValidAccessToken();
    try {
      final SearchItemRequest searchRequest =
          spotifyApi.searchItem(query, "track").limit(10).build();
      final Paging<Track> tracks = searchRequest.execute().getTracks();

      return Arrays.stream(tracks.getItems()).map(track -> {
        Map<String, String> result = new HashMap<>();
        result.put("name", track.getName());
        result.put("artist", track.getArtists()[0].getName());
        result.put("uri", track.getUri());

        // Ajouter l'image de couverture si disponible
        if (track.getAlbum() != null && track.getAlbum().getImages() != null
            && track.getAlbum().getImages().length > 0) {
          result.put("image", track.getAlbum().getImages()[0].getUrl());
        } else {
          result.put("image", ""); // fallback
        }

        return result;
      }).collect(Collectors.toList());

    } catch (Exception e) {
      throw new RuntimeException("Erreur lors de la recherche Spotify", e);
    }
  }


  public void addToQueue(String uri) {
    this.ensureValidAccessToken();
    CACHED_QUEUE.clear();

    try {
      spotifyApi.addItemToUsersPlaybackQueue(uri).build().execute();

      if (!this.isPlayerPlaying()) {
        this.playTrack();
      }

      this.getQueue();

    } catch (Exception e) {
      throw new RuntimeException("Erreur ajout Spotify queue", e);
    }
  }

  public boolean isPlayerPlaying() {
    try {
      CurrentlyPlayingContext playback =
          spotifyApi.getInformationAboutUsersCurrentPlayback().build().execute();

      return playback != null && Boolean.TRUE.equals(playback.getIs_playing());

    } catch (Exception e) {
      throw new RuntimeException("Erreur lors de la vérification du statut du player", e);
    }
  }

  public void playTrack() {
    this.ensureValidAccessToken();
    try {

      spotifyApi.startResumeUsersPlayback().build().execute();

      log.info("▶️ Lecture du morceau ");

    } catch (Exception e) {
      throw new RuntimeException("❌ Impossible de lancer la lecture", e);
    }
  }

  public void playNext() {
    this.ensureValidAccessToken();
    CACHED_QUEUE.clear();
    try {
      spotifyApi.skipUsersPlaybackToNextTrack().build().execute();

      log.info("⏭ Morceau suivant demandé à Spotify.");

      this.getQueue();

    } catch (Exception e) {
      throw new RuntimeException("❌ Impossible de passer au morceau suivant", e);
    }

  }

  public List<Map<String, String>> getQueue() {
    Instant now = Instant.now();
    if (CACHED_QUEUE.isEmpty()
        || now.isAfter(LAST_QUEUE_FETCH_TIME.plusSeconds(CACHE_DURATION_SECONDS))) {
      this.ensureValidAccessToken();
      try {
        PlaybackQueue queue = spotifyApi.getTheUsersQueue().build().execute();

        List<Map<String, String>> tracks = queue.getQueue().stream().map(track -> {
          Map<String, String> map = new HashMap<>();
          map.put("name", track.getName());
          map.put("artist", track.getArtists()[0].getName());
          map.put("uri", track.getUri());
          map.put("image", track.getAlbum().getImages()[0].getUrl());
          return map;
        }).collect(Collectors.toList());

        CACHED_QUEUE = tracks;
        LAST_QUEUE_FETCH_TIME = now;

      } catch (Exception e) {
        log.warn("❌ Impossible de charger la file Spotify : {}", e.getMessage());
        return Collections.emptyList();
      }
    }
    return CACHED_QUEUE;
  }


  public Map<String, Object> getNowPlaying() {
    this.ensureValidAccessToken();
    try {
      CurrentlyPlaying currentlyPlaying =
          spotifyApi.getUsersCurrentlyPlayingTrack().build().execute();

      if (currentlyPlaying == null || currentlyPlaying.getItem() == null) {
        Map<String, Object> result = new HashMap<>();
        result.put("title", null);
        result.put("artist", null);
        result.put("image", null);
        result.put("isPlaying", false);
        return result;
      }

      Track track = (Track) currentlyPlaying.getItem();
      String title = track.getName();
      String artist = Arrays.stream(track.getArtists()).map(ArtistSimplified::getName)
          .collect(Collectors.joining(", "));
      String image = track.getAlbum() != null && track.getAlbum().getImages() != null
          && track.getAlbum().getImages().length > 0
              ? track.getAlbum().getImages()[0].getUrl()
              : null;

      Map<String, Object> result = new HashMap<>();
      result.put("title", title);
      result.put("artist", artist);
      result.put("image", image);
      result.put("isPlaying", currentlyPlaying.getIs_playing());
      return result;
    } catch (Exception e) {
      throw new RuntimeException("❌ Impossible de récupérer le morceau en cours", e);
    }
  }

}
