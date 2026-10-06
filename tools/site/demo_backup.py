#!/usr/bin/env python3
"""
Writes two PhotoCal backups with demo data for the screenshots of the site (site/img):
photocal-demo-it.json and photocal-demo-en.json, same numbers with food names in Italian or
English. About 40 days of diary, 4 months of weight, water and saved foods, ending on --today.

Restore one in Settings -> Backup and data -> Restore backup -> Replace everything, on an emulator only:
it deletes the diary of the phone.

Usage:  python3 tools/site/demo_backup.py [--today 2026-10-01] [--out DIR]
"""
import argparse
import datetime as dt
import json
import pathlib
import random

# Italian name, English name, grams, per 100 g: kcal, protein, carbs, fat, fibre, sugars, salt,
# source, unit, servings, piece label (it, en)
FOODS = {
    "BREAKFAST": [
        ("Yogurt greco 0%", "Greek yogurt 0%", 170, 57, 10.3, 3.8, 0.2, 0, 3.8, 0.1, "BARCODE", None, None, None),
        ("Fiocchi d'avena", "Rolled oats", 40, 372, 13, 59, 7, 10, 1, 0, "MANUAL", None, None, None),
        ("Caffè espresso", "Espresso", 30, 2, 0.1, 0.3, 0, 0, 0, 0, "MANUAL", "ESPRESSO_CUP", 1, None),
        ("Frollini al cacao", "Cocoa shortbread", 25, 480, 7, 68, 19, 4, 26, 0.5, "BARCODE", "PIECE", 3, ("biscotti", "biscuits")),
    ],
    "LUNCH": [
        ("Pasta al pomodoro", "Pasta with tomato sauce", 280, 140, 4.6, 26, 2.2, 1.6, 3, 0.4, "PHOTO", None, None, None),
        ("Insalata mista", "Mixed salad", 120, 20, 1.5, 3, 0.3, 2, 2, 0, "PHOTO", None, None, None),
        ("Pane integrale", "Wholemeal bread", 50, 224, 8, 41, 2, 6.5, 2, 1.2, "MANUAL", None, None, None),
        ("Mela", "Apple", 180, 52, 0.3, 14, 0.2, 2.4, 10, 0, "MANUAL", None, None, None),
    ],
    "SNACK": [
        ("Mandorle", "Almonds", 25, 579, 21, 22, 50, 12, 4, 0, "MANUAL", None, None, None),
        ("Banana", "Banana", 120, 89, 1.1, 23, 0.3, 2.6, 12, 0, "MANUAL", None, None, None),
    ],
    "DINNER": [
        ("Petto di pollo alla griglia", "Grilled chicken breast", 150, 165, 31, 0, 3.6, 0, 0, 0.2, "PHOTO", None, None, None),
        ("Patate al forno", "Roast potatoes", 200, 93, 2.5, 21, 0.1, 2.2, 1, 0.3, "PHOTO", None, None, None),
        ("Zucchine grigliate", "Grilled courgettes", 150, 17, 1.2, 3.1, 0.3, 1, 2.5, 0, "PHOTO", None, None, None),
        ("Olio extravergine", "Extra virgin olive oil", 10, 899, 0, 0, 99.9, 0, 0, 0, "MANUAL", None, None, None),
    ],
}
FAVORITES = {"Yogurt greco 0%", "Fiocchi d'avena", "Mandorle"}
HOURS = {"BREAKFAST": 8, "LUNCH": 13, "SNACK": 17, "DINNER": 20}
ZONE = dt.timezone(dt.timedelta(hours=2))
EPOCH = dt.date(1970, 1, 1)


def epoch_day(day: dt.date) -> int:
    return (day - EPOCH).days


def millis(day: dt.date, hour: int, minute: int = 0) -> int:
    return int(dt.datetime(day.year, day.month, day.day, hour, minute, tzinfo=ZONE).timestamp() * 1000)


def backup(today: dt.date, lang: int) -> dict:
    """[lang] is 0 for Italian, 1 for English; the same seed gives the same numbers."""
    rnd = random.Random(7)
    entries = []
    for back in range(40):
        day = today - dt.timedelta(days=back)
        if back > 0 and rnd.random() < 0.08:
            continue  # a few days not logged
        # Today is still going: no dinner yet
        for meal in ("BREAKFAST", "LUNCH", "SNACK") if back == 0 else FOODS:
            for food in FOODS[meal]:
                if back > 0 and rnd.random() < 0.2:
                    continue
                scale = 1.0 if back == 0 else rnd.uniform(0.8, 1.3)
                *names, grams, kcal, protein, carbs, fat, fibre, sugars, salt, source, unit, servings, label = food
                grams = grams if unit else round(grams * scale)
                r = grams / 100
                entries.append(dict(
                    date=epoch_day(day), meal=meal, name=names[lang], grams=grams, kcal=round(kcal * r, 1),
                    proteinG=round(protein * r, 1), carbsG=round(carbs * r, 1), fatG=round(fat * r, 1),
                    fiberG=round(fibre * r, 1), sugarsG=round(sugars * r, 1), saltG=round(salt * r, 2),
                    source=source, createdAt=millis(day, HOURS[meal], rnd.randint(0, 40)),
                    unit=unit, servings=servings, pieceLabel=label[lang] if label else None,
                ))
    weights = []
    for back in range(0, 120, 3):
        day = today - dt.timedelta(days=back)
        weights.append(dict(date=epoch_day(day), weightKg=round(74.0 + back * 0.045 + rnd.uniform(-0.4, 0.4), 1),
                            createdAt=millis(day, 7, 30)))
    water = []
    for back in range(40):
        day = today - dt.timedelta(days=back)
        water.append(dict(date=epoch_day(day), ml=1200 if back == 0 else rnd.choice([1400, 1600, 1800, 2000, 2200]),
                          updatedAt=millis(day, 18)))
    saved = []
    for foods in FOODS.values():
        for food in foods:
            *names, grams, kcal, protein, carbs, fat, fibre, sugars, salt, source, unit, servings, label = food
            saved.append(dict(
                name=names[lang], barcode=f"80{10000000000 + len(saved) * 7919}" if source == "BARCODE" else None,
                source=source, kcalPer100=kcal, proteinPer100=protein, carbsPer100=carbs, fatPer100=fat,
                fiberPer100=fibre, sugarsPer100=sugars, saltPer100=salt, grams=grams, unit=unit, servings=servings,
                pieceGrams=grams / servings if unit == "PIECE" else None, pieceLabel=label[lang] if label else None,
                favorite=names[0] in FAVORITES, useCount=rnd.randint(3, 25),
                lastUsedAt=millis(today - dt.timedelta(days=rnd.randint(0, 5)), 12),
            ))
    settings = dict(
        kcalGoal=1900, proteinGoalG=110, carbsGoalG=220, fatGoalG=60, waterGoalMl=2000, glassMl=200,
        bottleMl=750, bottleName=("Borraccia", "Bottle")[lang], name="Alex",
        sex="UNSPECIFIED", birthYear=1992, heightCm=172, activity="LIGHT", weightGoal="LOSE_SLOWLY",
        # Fixed greens, as on the site, instead of the wallpaper colours
        themeMode="SYSTEM", dynamicColor=False, useCrea=True, reminders=[], healthWrite=True, healthAddBurned=False,
    )
    # exportedAt is in seconds (BackupManager), the other instants in milliseconds
    return dict(format="photocal-backup", version=1, appVersion="demo", exportedAt=millis(today, 9) // 1000,
                entries=entries, weights=weights, water=water, savedFoods=saved, settings=settings)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--today", type=dt.date.fromisoformat, default=dt.date.today())
    parser.add_argument("--out", type=pathlib.Path, default=pathlib.Path("."))
    args = parser.parse_args()
    for lang, tag in enumerate(("it", "en")):
        path = args.out / f"photocal-demo-{tag}.json"
        path.write_text(json.dumps(backup(args.today, lang), ensure_ascii=False), encoding="utf-8")
        print(path)


if __name__ == "__main__":
    main()
