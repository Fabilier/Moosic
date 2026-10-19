package co.moosic.music;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParsingTest {
    @Test
    void plainVideoIsNotAPlaylist() {
        assertFalse(Patterns.isPlaylist("dQw4w9WgXcQ"));
        assertFalse(Patterns.isPlaylist("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertFalse(Patterns.isPlaylist("https://youtu.be/dQw4w9WgXcQ"));
    }

    @Test
    void embeddedPlaylistAndMixAreDetected() {
        assertTrue(Patterns.isPlaylist("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLabc123"));
        assertTrue(Patterns.isPlaylist("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ"));
    }

    @Test
    void unknownInputIsResolvedByPlayer() {
        assertTrue(Patterns.isPlaylist("https://soundcloud.com/a/b"));
    }

    @Test
    void songFileParsingSkipsBlankCommentsAndDuplicates() {
        assertEquals(Arrays.asList("a", "b"),
                TrackScheduler.parseSongs(Arrays.asList("  a ", "", "# comment", "b", "a")));
        assertEquals(Collections.emptyList(), TrackScheduler.parseSongs(Collections.singletonList("   ")));
    }

    @Test
    void durationFormat() {
        assertEquals("00:00", MessageHandler.formatDuration(0));
        assertEquals("03:05", MessageHandler.formatDuration(185_000));
        assertEquals("1:02:03", MessageHandler.formatDuration(3_723_000));
    }
}
