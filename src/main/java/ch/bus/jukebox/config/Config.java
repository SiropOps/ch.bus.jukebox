package ch.bus.jukebox.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import se.michaelthelin.spotify.SpotifyApi;

@Configuration
@PropertySources({@PropertySource(value = "config.properties", ignoreResourceNotFound = true),
    @PropertySource(value = "file:/app/properties/config.properties",
        ignoreResourceNotFound = true)})
public class Config {
  @Value("${spotify.client-id}")
  private String clientId;

  @Value("${spotify.client-secret}")
  private String clientSecret;

  @Value("${spotify.redirect-uri}")
  private String redirectUri;

  @Value("${spotify.refresh-token}")
  private String refreshToken;

  @Bean
  SpotifyApi spotifyApi() {
    SpotifyApi spotifyApi = new SpotifyApi.Builder().setClientId(clientId)
        .setClientSecret(clientSecret).setRedirectUri(URI.create(redirectUri)).build();

    spotifyApi.setRefreshToken(refreshToken);
    return spotifyApi;
  }
}
