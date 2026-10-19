package co.moosic.music;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TrackScheduler extends AudioEventAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(TrackScheduler.class);
    private static final long MAX_BACKOFF_MS = 60_000;

    private final AudioPlayer player;
    private final AudioPlayerManager playerManager;
    private final JDA jda;
    private final List<String> songs = new CopyOnWriteArrayList<>();
    /** Songs not played yet in the current shuffled round. Guarded by itself. */
    private final Deque<String> queue = new ArrayDeque<>();
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "moosic-retry");
        t.setDaemon(true);
        return t;
    });
    private volatile int consecutiveFailures = 0;
    private String lastSong;

    TrackScheduler(JDA jda, AudioPlayer player, AudioPlayerManager playerManager, Path songsFile) throws IOException {
        this.jda = jda;
        this.player = player;
        this.playerManager = playerManager;
        songs.addAll(parseSongs(Files.readAllLines(songsFile, StandardCharsets.UTF_8)));
        processTracks();
        if (player.getPlayingTrack() == null) {
            nextTrack();
        }
    }

    /** Trims lines and ignores blank lines and lines starting with '#'. */
    static List<String> parseSongs(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String s = line.trim();
            if (!s.isEmpty() && !s.startsWith("#") && !result.contains(s)) result.add(s);
        }
        return result;
    }

    AudioTrack getPlayingTrack() {
        return player.getPlayingTrack();
    }

    private void processTracks() throws IOException {
        LOG.info("Processing {} songs", songs.size());
        for (String song : new ArrayList<>(songs)) {
            if (Patterns.isPlaylist(song)) {
                parsePlaylist(song);
            }
        }
        if (songs.isEmpty()) {
            throw new IOException("No supported songs found in songs.txt");
        }
        LOG.info("{} songs loaded, starting", songs.size());
    }

    private void parsePlaylist(String song) {
        LOG.info("Resolving {}", song);
        try {
            playerManager.loadItem(song, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    // a single track given as a non-YouTube url: keep the entry as is
                }

                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    songs.remove(song);
                    for (AudioTrack tr : playlist.getTracks()) {
                        String uri = tr.getInfo().uri;
                        if (!songs.contains(uri)) songs.add(uri);
                    }
                    LOG.info("Parsed playlist with {} songs", playlist.getTracks().size());
                }

                @Override
                public void noMatches() {
                    LOG.warn("No match for {}, ignoring", song);
                    songs.remove(song);
                }

                @Override
                public void loadFailed(FriendlyException exception) {
                    LOG.warn("Could not load {}: {}", song, exception.getMessage());
                    songs.remove(song);
                }
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            LOG.error("Failed to parse " + song, e);
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        LOG.warn("Track exception: {}", exception.getMessage());
        trackFailed();
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        trackFailed();
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            nextTrack();
        }
    }

    /**
     * Picks the next song from a shuffled round: every song is played once before any repeats,
     * and the same song is never played twice in a row.
     */
    private String getNextSong() {
        synchronized (queue) {
            if (queue.isEmpty()) {
                List<String> round = new ArrayList<>(songs);
                if (round.isEmpty()) return null;
                Collections.shuffle(round);
                if (round.size() > 1 && round.get(0).equals(lastSong)) {
                    Collections.swap(round, 0, round.size() - 1);
                }
                queue.addAll(round);
            }
            lastSong = queue.poll();
            return lastSong;
        }
    }

    private void loadTrack(String song) {
        playerManager.loadItem(song, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                LOG.info("Loaded! {}", track.getInfo().title);
                consecutiveFailures = 0;
                player.startTrack(track, false);
                jda.getPresence().setActivity(Activity.playing("▶ " + track.getInfo().title));
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                trackFailed();
            }

            @Override
            public void noMatches() {
                LOG.warn("No match for {}", song);
                trackFailed();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                LOG.warn("Could not load {}: {}", song, exception.getMessage());
                trackFailed();
            }
        });
    }

    private void nextTrack() {
        String song = getNextSong();
        if (song == null) {
            LOG.error("Song list is empty, nothing to play");
            return;
        }
        loadTrack(song);
    }

    /** Retries with an exponential backoff so a broken source does not cause a busy loop. */
    private void trackFailed() {
        int failures = ++consecutiveFailures;
        long delay = failures <= 1 ? 0 : Math.min(MAX_BACKOFF_MS, 1000L << Math.min(failures - 2, 6));
        retryExecutor.schedule(this::nextTrack, delay, TimeUnit.MILLISECONDS);
    }

    void shutdown() {
        retryExecutor.shutdownNow();
    }
}
