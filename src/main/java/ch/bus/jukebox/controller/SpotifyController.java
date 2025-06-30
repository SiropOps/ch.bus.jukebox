package ch.bus.jukebox.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ch.bus.jukebox.service.SpotifyService;

@RestController
@RequestMapping("/api")
public class SpotifyController {

  private final SpotifyService spotifyService;

  public SpotifyController(SpotifyService spotifyService) {
    this.spotifyService = spotifyService;
  }

  @GetMapping("/search")
  public List<Map<String, String>> search(@RequestParam String q) throws Exception {
    return spotifyService.searchTracks(q);
  }

  @PostMapping("/queue")
  public void queue(@RequestBody Map<String, String> payload) {
    this.spotifyService.addToQueue(payload.get("uri"));
  }

  @GetMapping("/queue")
  public List<Map<String, String>> getQueue() {
    return this.spotifyService.getQueue();
  }

  @PostMapping("/next")
  public void nextTrack() {
    this.spotifyService.playNext();
  }

  @GetMapping("/now-playing")
  public Map<String, Object> getNowPlaying() {
    return this.spotifyService.getNowPlaying();
  }
}
