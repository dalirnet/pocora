# Apps presets

Which apps have internet in an Allowed mark. Each file shows one list.

> All presets: [`preset/`](../README.md)

## How to read them

- Every **Allowed** mark has an apps preset. Apps on the list have internet in that mark, all others do not.
- A preset is a set of **groups**, not a list of single apps, so it fits whatever is installed on the child's phone.
- The child has one apps preset for the whole schedule. A block can use a different one, such as **School only** for class hours.
- Apps without internet still open. Pocora holds back the internet, not the app.

## Presets

### By age

| Preset | Persian | Age | For |
| --- | --- | --- | --- |
| [Kids](./kids.md) | کودک | 8 to 9 | For a young child with a first tablet or a hand-me-down phone. |
| [Everyday](./everyday.md) | روزمره | 10 to 12 | For a child who uses the phone for school, family chat, games and video. The default for most children. |
| [Teen](./teen.md) | نوجوان | 13 to 16 | For a teenager who needs the web for school and daily life, in a family that holds back social networks. |

### By purpose

| Preset | Persian | Age | For |
| --- | --- | --- | --- |
| [School only](./school.md) | فقط مدرسه | 6 to 16 | For class time. Only the apps school runs on. |
| [Study](./study.md) | درس | 6 to 16 | For homework hours and exam weeks. |
| [Reachable](./reachable.md) | در دسترس | 6 to 16 | For when the child is out: at a class, at a friend's, on the way home. |
| [No games](./no-games.md) | بدون بازی | 6 to 16 | For school nights and for weeks when games have taken over. |

### Open

| Preset | Persian | Age | For |
| --- | --- | --- | --- |
| [Everything](./everything.md) | همه برنامه‌ها | Any | For families who limit by time and quota, not by app. |

## At a glance

```
             Kids      Everyday  Teen      School    Study     Reach     No games  All
System       #         #         #         #         #         #         #         #
School       #         #         #         #         #         .         #         #
Learning     #         #         #         .         #         .         #         #
Kids         #         #         #         .         .         .         #         #
Messaging    .         #         #         .         .         #         #         #
Daily tools  .         #         #         .         .         #         #         #
Music        .         #         #         .         .         .         #         #
Video        .         #         #         .         .         .         #         #
Games        .         #         #         .         .         .         .         #
Stores       .         #         #         .         .         .         #         #
Browser      .         .         #         .         .         .         #         #
Social       .         .         .         .         .         .         #         #
Other        .         .         #         .         .         .         #         #

# has internet   . does not
```

## Groups

Every app on the child's phone belongs to exactly one group. The full lists, with each app's Android id, are in [`groups/`](./groups/README.md).

| Group | Persian | Apps |
| --- | --- | --- |
| [System](./groups/system.md) | سیستم | Phone, messages, keyboard, clock, Android and app-store services, software update, Pocora itself |
| [School](./groups/school.md) | مدرسه | Shad, Google Meet, Adobe Connect |
| [Learning](./groups/learning.md) | آموزش | Hamyar, Jahesh, Paadars, Gajino, Learnit, Duolingo, Fastdic, Google Translate, Taaghche, Quran HablolMatin |
| [Kids](./groups/kids.md) | کودک | Digitoon, Afarinak, Aparat Kids, Happiness Train, story and alphabet apps |
| [Messaging](./groups/messaging.md) | پیام‌رسان | WhatsApp, iGap, Eitaa, Bale, Soroush Plus, Gap |
| [Daily tools](./groups/daily-tools.md) | ابزار روزمره | Balad, Neshan, Google Maps, Snapp, Tapsi, BadeSaba, Havashenas |
| [Music](./groups/music.md) | موسیقی | IranSeda, Beeptunes, Aparat Music, Castbox |
| [Video](./groups/video.md) | ویدیو | Telewebion, Aparat, Filimo, Anten, Namava, Filmnet |
| [Games](./groups/games.md) | بازی | Every app Android marks as a game: Amirza, Quiz of Kings, Fandogh, Clash of Clans, Clash Royale, Mench, Clutch, Subway Surfers, Minecraft |
| [Stores](./groups/stores.md) | فروشگاه برنامه | Bazaar, Myket, Google Play |
| [Browser](./groups/browser.md) | مرورگر | Chrome, Firefox, Samsung Internet, Mi Browser |
| [Social](./groups/social.md) | شبکه اجتماعی | Rubika, Chatzy, Wisgoon, Virasty |
| [Other](./groups/other.md) | سایر | Shopping (Divar, Digikala, Torob), banking, sport news, and every app that is in no other group |

How an app gets its group:

1. **Known apps.** Pocora ships a list of apps common in Iran, each with its group.
2. **Android's own category.** An app that is not on the list takes the category its developer declared: game, video, music, social and so on.
3. **Other.** An app with neither goes to Other.

Rules that hold in every preset:

- **System always has internet** in an Allowed mark. Without it the phone itself misbehaves.
- **VPN apps never do.** Android refuses them while Pocora is on, and installing one is an alert.
- **Single apps can be fixed.** On the child's app list the parent can mark an app as always in or always out. That beats the preset and stays when the preset changes.
- **New apps** take their group the moment they are installed, so nothing slips in through a store.

## How they were built

- **Groups a parent already thinks in:** school, games, video, chat.
- **Three ages, one step each.** Kids adds nothing risky. Everyday adds messaging, games and video. Teen adds the browser.
- **The browser and social networks come last.** They are the two groups where a child meets strangers and unlimited content.
- **Purpose lists are short.** School only, Study and Reachable each leave almost everything out, so the mark has one job.
