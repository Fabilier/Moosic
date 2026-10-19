# Moosic

**Host your own 24/7 Discord Music Bot!**

Moosic is a simple bot that is easy to host and setup.
Once setup, moosic will join the designated voice channel and play songs randomly from songs.txt 24/7.

## Instructions
**First setup your bot by following the guide [Here](https://github.com/Repulser/Moosic/wiki/Setting-up-the-bot-for-the-first-time)**

You can host the bot for free by following [this](https://github.com/Repulser/Moosic/wiki/Hosting-on-heroku-for-free) guide

**Or host it yourself by following [this](https://github.com/Repulser/Moosic/wiki/Running-the-bot) guide.**


## Configuration notes
- `bot.cfg` is created on first start. `DISCORD_TOKEN`, `COMMAND_PREFIX`, `VOLUME` and `VOICE_CHANNEL_ID` environment variables override it, so the token does not have to be stored on disk.
- `songs.txt`: one entry per line (YouTube video/playlist/mix URL or ID). Blank lines and lines starting with `#` are ignored.
- The `!np` command needs the **Message Content** privileged intent, enabled in the Discord developer portal.
- Requires Java 17+. Build with `./gradlew build`, the jar is in `build/libs/moosic-*.jar`.
