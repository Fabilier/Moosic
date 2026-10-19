package co.moosic.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.util.concurrent.TimeUnit;

public class MessageHandler extends ListenerAdapter {
    private final TrackScheduler scheduler;
    private final String prefix;

    MessageHandler(TrackScheduler scheduler, String prefix) {
        this.scheduler = scheduler;
        this.prefix = prefix.toLowerCase();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent e) {
        if (e.getAuthor().isBot() || !e.isFromGuild()) return;
        if (!e.getMessage().getContentRaw().trim().toLowerCase().equals(prefix + "np")) return;
        if (!e.getGuild().getSelfMember().hasPermission(e.getGuildChannel(),
                Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS)) return;

        AudioTrack track = scheduler.getPlayingTrack();
        if (track == null) {
            e.getChannel().sendMessage("Nothing is playing right now.").queue();
            return;
        }
        AudioTrackInfo info = track.getInfo();
        String duration = info.isStream ? "LIVE" : formatDuration(info.length);
        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor("Now Playing", info.uri, null)
                .setColor(Color.GREEN)
                .addField("Song Name", info.title, true)
                .addField("Channel", info.author, true)
                .addField("Song Progress", String.format("`%s / %s`", formatDuration(track.getPosition()), duration), true);
        if (info.uri != null) {
            embed.addField("Song Link", "[Link](" + info.uri + ")", true);
        }
        String thumbnail = info.artworkUrl;
        if (thumbnail == null && info.uri != null && info.uri.contains("youtube.com")) {
            thumbnail = String.format("https://img.youtube.com/vi/%s/hqdefault.jpg", info.identifier);
        }
        if (thumbnail != null) embed.setThumbnail(thumbnail);
        e.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    /** mm:ss, or h:mm:ss from one hour. */
    static String formatDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        if (hours > 0) return String.format("%d:%02d:%02d", hours, minutes, seconds);
        return String.format("%02d:%02d", minutes, seconds);
    }
}
