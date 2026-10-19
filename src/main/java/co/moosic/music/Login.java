package co.moosic.music;

import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class Login {
    private static final Logger LOG = LoggerFactory.getLogger(Login.class);

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception e) {
            LOG.error("Startup failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        Config.load(new File("bot.cfg"));

        // MESSAGE_CONTENT is a privileged intent: enable it in the Discord developer portal.
        JDA jda = JDABuilder.createDefault(Config.discord_token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .build().awaitReady();
        LOG.info("Use this url to add me:\nhttps://discord.com/oauth2/authorize?client_id={}&scope=bot&permissions=3147776",
                jda.getSelfUser().getId());

        VoiceChannel channel = jda.getVoiceChannelById(Config.voice_channel_id);
        if (channel == null) {
            jda.shutdownNow();
            throw new IllegalStateException("Could not find the channel, make sure the ID is correct and that the bot can see it.");
        }

        AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
        playerManager.getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH);

        AudioPlayer player = playerManager.createPlayer();
        player.setVolume(Config.volume);
        AudioManager audioManager = channel.getGuild().getAudioManager();
        audioManager.setSendingHandler(new AudioPlayerSendHandler(player));
        audioManager.openAudioConnection(channel);
        LOG.info("Joined designated voice channel {}", channel.getName());

        TrackScheduler scheduler = new TrackScheduler(jda, player, playerManager, Paths.get("songs.txt"));
        player.addListener(scheduler);
        jda.addEventListener(new MessageHandler(scheduler, Config.command_prefix));
        jda.addEventListener(new RejoinListener(channel.getId()));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdown();
            player.destroy();
            audioManager.closeAudioConnection();
            jda.shutdown();
            playerManager.shutdown();
        }, "moosic-shutdown"));
    }

    /** Rejoins the designated channel when the bot is kicked or moved out of it. */
    private static class RejoinListener extends ListenerAdapter {
        private final String channelId;

        RejoinListener(String channelId) {
            this.channelId = channelId;
        }

        @Override
        public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
            if (!event.getEntity().equals(event.getGuild().getSelfMember())) return;
            if (event.getChannelJoined() != null && event.getChannelJoined().getId().equals(channelId)) return;
            event.getJDA().getRateLimitPool().schedule(() -> {
                VoiceChannel channel = event.getJDA().getVoiceChannelById(channelId);
                if (channel == null) return;
                LOG.info("Not in the designated channel anymore, rejoining");
                try {
                    channel.getGuild().getAudioManager().openAudioConnection(channel);
                } catch (Exception e) {
                    LOG.warn("Could not rejoin: {}", e.getMessage());
                }
            }, 5, TimeUnit.SECONDS);
        }
    }
}
