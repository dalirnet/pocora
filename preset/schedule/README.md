# Schedule presets

Ready-made weeks a parent picks from, then adjusts. Each file shows one week.

> All presets: [`preset/`](../README.md)

## How to read them

- A day is 48 slots of 30 minutes. Each slot is **Allowed** or **Limited**.
- **Limited is the default.** A preset only says which slots are Allowed.
- The week starts on Saturday. Saturday to Wednesday are school days, Friday is the day off, and Thursday is off for most primary schools.
- A custom block, Limited over Allowed or Allowed over Limited, handles an unusual day without changing the preset.

## Changing the preset

A preset is a starting point. The parent changes single days on top of it, as custom blocks, Allowed or Limited. A custom block is for this week only or for that weekday every week.

**Choosing a different preset removes every custom block on the schedule,** of both kinds. The new preset applies from that moment, for the rest of this week and every week after, and the parent makes the changes again if they are still needed. Before saving, the app shows the new week and names what it removes.

## Presets

The columns show Allowed time per day.

### School year

| Preset | Persian | Sat to Wed | Thu | Fri |
| --- | --- | --- | --- | --- |
| [School, morning shift](./school-morning.md) | مدرسه، نوبت صبح | 3h 30m | 7h | 6h |
| [School, afternoon shift](./school-afternoon.md) | مدرسه، نوبت عصر | 3h | 7h | 6h |
| [Online school](./school-remote.md) | مدرسه غیرحضوری | 8h 30m | 7h | 6h |
| [Exam season](./exams.md) | فصل امتحانات | 1h 30m | 3h | 3h |
| [Ramadan](./ramadan.md) | ماه رمضان | 4h | 7h 30m | 6h |

### Holidays

| Preset | Persian | Sat to Wed | Thu | Fri |
| --- | --- | --- | --- | --- |
| [Summer](./summer.md) | تابستان | 6h | 8h | 8h |
| [Holidays](./holidays.md) | نوروز و تعطیلات | 13h | 13h | 13h |

### Simple rules

| Preset | Persian | Sat to Wed | Thu | Fri |
| --- | --- | --- | --- | --- |
| [One hour a day](./one-hour.md) | روزی یک ساعت | 1h | 2h | 2h |
| [Weekends only](./weekends-only.md) | فقط آخر هفته | none | 5h | 5h |
| [Nights off](./nights-off.md) | شب‌ها خاموش | 15h | 16h 30m | 15h |
| [Watch only](./watch-only.md) | فقط نظارت | 24h | 24h | 24h |

## Through the year

Each preset file has a **Season** section with the dates it fits. The parent app uses them to suggest a preset. All dates are in the Iranian calendar.

```
Far   Ord   Kho   Tir   Mor   Sha   Meh   Aba   Aza   Dey   Bah   Esf
HHHSSSSSSSSSEEEEEUUUUUUUUUUUUUUUUUUUSSSSSSSSSSSSSSSSSSEEEESSSSSSSSSSSSSH

H Holidays   S School   E Exam season   U Summer   one mark = about 5 days
```

| From | To | Suggested preset |
| --- | --- | --- |
| Farvardin 1 | Farvardin 13 | Holidays |
| Farvardin 14 | Ordibehesht 31 | School, morning or afternoon shift |
| Khordad 1 | Khordad 25 | Exam season |
| Khordad 26 | Shahrivar 31 | Summer |
| Mehr 1 | Azar 30 | School, morning or afternoon shift |
| Dey 1 | Dey 20 | Exam season |
| Dey 21 | Esfand 24 | School, morning or afternoon shift |
| Esfand 25 | Esfand 29 | Holidays |

How the suggestion works:

- **The app suggests, the parent decides.** The schedule never changes by itself.
- **It appears a few days early:** from 3 days before a period starts, the app offers the next preset.
- **Ramadan** follows the lunar calendar, with its dates for five years in [`holidays/`](../holidays/README.md). Inside a school period it replaces the school suggestion. Exam season and Holidays come before it.
- **Online school** and the four **simple rules** are never suggested by date. They are always in the list.
- **Watch only** is suggested once, for the first week after pairing.
- **Single public holidays** are custom blocks, not presets. The app knows them from [`holidays/`](../holidays/README.md) and suggests Friday's hours for the day.

## How they were built

- **School hours are Limited.** No preset opens the internet while the child is in class.
- **Homework comes first.** On school days the first block starts after the homework hours, not when the child gets home.
- **School nights end early.** Allowed time stops at least an hour before sleep. A school night is any night before Saturday to Wednesday, so Friday night counts.
- **Thursday night is the long one.** It is the only night with no school the next morning for every child.
- **The family's fixed points stay free:** the Friday lunch, the hours around iftar, holiday visits.
- **Few blocks.** One or two per day, three at most, so a parent can remember the schedule without opening the app.
