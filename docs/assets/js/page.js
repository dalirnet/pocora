const DIGITS = "۰۱۲۳۴۵۶۷۸۹";

function persian(text) {
    return String(text).replace(/\d/g, (digit) => DIGITS[digit]);
}

const observer = new IntersectionObserver(
    (entries) => {
        for (const entry of entries) {
            if (entry.isIntersecting) {
                entry.target.classList.add("shown");
                observer.unobserve(entry.target);
            }
        }
    },
    { threshold: 0.15 },
);
document
    .querySelectorAll(".reveal")
    .forEach((element) => observer.observe(element));

const nav = document.querySelector(".nav");
const onScroll = () => nav.classList.toggle("solid", window.scrollY > 40);
window.addEventListener("scroll", onScroll, { passive: true });
onScroll();

const SEASONS = [
    [[1, 1, 1, 13], ["holidays"], "نوروز و تعطیلات"],
    [
        [1, 14, 2, 31],
        ["school-morning", "school-afternoon"],
        "مدرسه، نوبت صبح یا عصر",
    ],
    [[3, 1, 3, 25], ["exams"], "فصل امتحانات"],
    [[3, 26, 6, 31], ["summer"], "تابستان"],
    [
        [7, 1, 9, 30],
        ["school-morning", "school-afternoon"],
        "مدرسه، نوبت صبح یا عصر",
    ],
    [[10, 1, 10, 20], ["exams"], "فصل امتحانات"],
    [
        [10, 21, 12, 24],
        ["school-morning", "school-afternoon"],
        "مدرسه، نوبت صبح یا عصر",
    ],
    [[12, 25, 12, 30], ["holidays"], "نوروز و تعطیلات"],
];

const MONTHS = [
    "فروردین",
    "اردیبهشت",
    "خرداد",
    "تیر",
    "مرداد",
    "شهریور",
    "مهر",
    "آبان",
    "آذر",
    "دی",
    "بهمن",
    "اسفند",
];

function iranianToday() {
    const parts = new Intl.DateTimeFormat("en-u-ca-persian-nu-latn", {
        month: "numeric",
        day: "numeric",
    }).formatToParts(new Date());
    const value = (type) =>
        Number(parts.find((part) => part.type === type).value);
    return [value("month"), value("day")];
}

function showSeason() {
    const [month, day] = iranianToday();
    const today = month * 100 + day;
    const season = SEASONS.find(
        ([[fromMonth, fromDay, toMonth, toDay]]) =>
            today >= fromMonth * 100 + fromDay &&
            today <= toMonth * 100 + toDay,
    );
    if (!season) return;
    const [[fromMonth, fromDay, toMonth, toDay], presets, text] = season;
    const date = (m, d) => `${persian(d)} ${MONTHS[m - 1]}`;
    document.getElementById("now-day").textContent = persian(day);
    document.getElementById("now-month").textContent = MONTHS[month - 1];
    document.getElementById("now-preset").textContent = text;
    document.getElementById("now-range").textContent =
        `از ${date(fromMonth, fromDay)} تا ${date(toMonth, toDay)}`;
    for (const id of presets)
        document
            .querySelector(`[data-preset="${id}"]`)
            ?.classList.add("suggested");
}

showSeason();

const STEP_MILLISECONDS = 4000;
const stepList = document.getElementById("steps");
const steps = [...stepList.querySelectorAll(".step")];
const stepPhone = document.getElementById("step-phone");
let currentStep = 0;
let playing = !window.matchMedia("(prefers-reduced-motion: reduce)").matches;

function showStep(index) {
    currentStep = index;
    steps.forEach((step, i) => step.classList.toggle("active", i === index));
    const step = steps[index];
    const image = stepPhone.querySelector("img");
    image.src = step.dataset.shot;
    image.alt = step.dataset.alt;
    const child = step.dataset.who === "child";
    stepPhone.classList.toggle("child", child);
    stepPhone.classList.toggle("parent", !child);
    stepPhone.querySelector("figcaption").textContent = child
        ? "پوکورا فرزند"
        : "پوکورا والدین";
}

steps.forEach((step, index) =>
    step.addEventListener("click", () => {
        playing = false;
        stepList.classList.add("paused");
        showStep(index);
    }),
);

setInterval(() => {
    if (playing) showStep((currentStep + 1) % steps.length);
}, STEP_MILLISECONDS);

const questions = [...document.querySelectorAll(".questions details")];
questions.forEach((question) =>
    question.addEventListener("toggle", () => {
        if (question.open)
            questions.forEach(
                (other) => other !== question && other.removeAttribute("open"),
            );
    }),
);

fetch("https://api.github.com/repos/dalirnet/pocora/releases/latest")
    .then((response) => (response.ok ? response.json() : null))
    .then((release) => {
        if (!release) return;
        for (const line of document.querySelectorAll("[data-asset]")) {
            const asset = release.assets?.find(
                (file) => file.name === line.dataset.asset,
            );
            if (asset)
                line.textContent = `${persian(Number((asset.size / 1048576).toFixed(1)))} مگ`;
        }
    })
    .catch(() => {});
