# Quota presets

How much data a child can use in one mark. Each file shows one level.

> All presets: [`preset/`](../README.md)

## How to read them

- A **mark** is one 30-minute slot of the schedule.
- The quota is set per child and applies to every **Allowed** mark. Limited marks have no internet, so they use nothing.
- The parent picks a level, not a number of megabytes.
- When a mark's quota is used up, internet stops until the next mark. The child is told at 90%.
- The parent app shows each level per hour (two marks), which reads more easily. The limit still applies to each mark.

## Presets

| Preset | Persian | Per mark | Per hour | Most per month, School morning shift |
| --- | --- | --- | --- | --- |
| [Light](./light.md) | کم | 25 MB | 50 MB | about 6.5 GB |
| [Medium](./medium.md) | متوسط | 100 MB | 200 MB | about 26 GB |
| [High](./high.md) | زیاد | 250 MB | 500 MB | about 65 GB |
| [No limit](./no-limit.md) | بدون سقف | none | none | not limited |

## The month at a glance

The quota and the schedule together give one number a parent can compare with their data package:

```
most per month = quota per mark x Allowed marks in a month
```

- Each preset file lists this number for every schedule preset.
- The parent app shows it next to the level while the parent is choosing.
- Changing the schedule preset changes it, because the number of Allowed marks changes.
- It is a ceiling. The app also shows what was really used this month, read from Android's traffic history.

## What uses how much

Rough amounts for 30 minutes of use. They vary a lot by app and quality.

| Activity | About |
| --- | --- |
| Chat and messages | 5 to 10 MB |
| School app, messages and files | 10 to 20 MB |
| Browsing | 20 to 30 MB |
| Online game | 20 to 50 MB |
| Music | 30 to 50 MB |
| Social app with a video feed | 100 to 300 MB |
| Video, low quality | about 150 MB |
| Video, normal quality | about 250 MB |
| Video, high quality | 500 MB or more |
| Video call or live class | 150 to 250 MB |
