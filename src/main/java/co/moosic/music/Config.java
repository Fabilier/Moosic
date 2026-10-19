package co.moosic.music;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Properties;

/**
 * Configuration loaded from bot.cfg (created with default values if missing).
 * Each value can be overridden by an environment variable, e.g. DISCORD_TOKEN, which avoids
 * storing the token in clear text on disk (useful on Heroku and similar hosts).
 */
public class Config {
    public static String discord_token = "your-discord-token";
    public static String command_prefix = "!";
    public static int volume = 30;
    public static String voice_channel_id = "channel-id";

    static void load(File file) throws IOException {
        Properties props = new Properties();
        if (file.exists()) {
            try (InputStream in = Files.newInputStream(file.toPath())) {
                props.load(in);
            }
        } else {
            props.setProperty("discord_token", discord_token);
            props.setProperty("command_prefix", command_prefix);
            props.setProperty("volume", String.valueOf(volume));
            props.setProperty("voice_channel_id", voice_channel_id);
            try (OutputStream out = Files.newOutputStream(file.toPath())) {
                props.store(out, "Moosic configuration (env vars DISCORD_TOKEN, COMMAND_PREFIX, VOLUME, VOICE_CHANNEL_ID take precedence)");
            }
        }
        discord_token = read(props, "discord_token", discord_token);
        command_prefix = read(props, "command_prefix", command_prefix);
        voice_channel_id = read(props, "voice_channel_id", voice_channel_id);
        String vol = read(props, "volume", String.valueOf(volume));
        try {
            volume = Math.max(0, Math.min(150, Integer.parseInt(vol)));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid volume: " + vol, e);
        }
    }

    private static String read(Properties props, String key, String def) {
        String env = System.getenv(key.toUpperCase());
        if (env != null && !env.trim().isEmpty()) return env.trim();
        return props.getProperty(key, def).trim();
    }
}
