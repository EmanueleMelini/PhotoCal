#!/usr/bin/env python3
"""Announces a new version with a push notification in each app language (Firebase Cloud Messaging).

Usage: notify_update.py <version> [--debug] [--dry-run]

One message per language topic (news-it, news-en), with the same title and text as the app's
"New version" reminder (reminder_update_title / reminder_update_text in the strings files), so the
two never drift apart. Tapping it opens the update dialog (launch_action=show_update, read by
LaunchRequest). --debug sends every language to news-debug instead, which only debug builds join.
--dry-run prints the messages without sending them.

Needs FIREBASE_PROJECT_ID and FCM_ACCESS_TOKEN (OAuth token of a service account allowed to send
messages; in GitHub Actions it comes from Workload Identity Federation).
"""
import json
import os
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

from release_notes import RES, android_unescape

# Language tag -> strings file; the tag is also the topic suffix (NewsTopics.languageTopic)
LANGUAGES = {"it": RES / "values/strings.xml", "en": RES / "values-en/strings.xml"}
DEBUG_TOPIC = "news-debug"
# ReminderNotifier.CHANNEL_UPDATES: users can mute updates apart from the other news
CHANNEL = "updates"


def string(strings: Path, name: str) -> str:
    element = ET.parse(strings).getroot().find(f"string[@name='{name}']")
    if element is None:
        sys.exit(f"No '{name}' string in {strings}")
    return android_unescape("".join(element.itertext()))


def message(topic: str, strings: Path, version: str) -> dict:
    return {
        "message": {
            "topic": topic,
            "notification": {
                "title": string(strings, "reminder_update_title").replace("%1$s", version),
                "body": string(strings, "reminder_update_text"),
            },
            "data": {"launch_action": "show_update"},
            "android": {"notification": {"channel_id": CHANNEL}},
        }
    }


def send(project: str, token: str, body: dict) -> None:
    request = urllib.request.Request(
        f"https://fcm.googleapis.com/v1/projects/{project}/messages:send",
        data=json.dumps(body).encode(),
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            print(f"Sent to {body['message']['topic']}: {json.load(response)['name']}")
    except urllib.error.HTTPError as e:
        sys.exit(f"FCM refused the message for {body['message']['topic']}: {e.code} {e.read().decode()}")


def main() -> None:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) != 1:
        sys.exit("usage: notify_update.py <version> [--debug] [--dry-run]")
    version = args[0].removeprefix("v")
    debug, dry_run = "--debug" in sys.argv, "--dry-run" in sys.argv
    messages = [message(DEBUG_TOPIC if debug else f"news-{tag}", strings, version) for tag, strings in LANGUAGES.items()]
    if dry_run:
        print(json.dumps(messages, ensure_ascii=False, indent=2))
        return
    project, token = os.environ.get("FIREBASE_PROJECT_ID"), os.environ.get("FCM_ACCESS_TOKEN")
    if not project or not token:
        sys.exit("FIREBASE_PROJECT_ID and FCM_ACCESS_TOKEN are needed (or use --dry-run)")
    for body in messages:
        send(project, token, body)


if __name__ == "__main__":
    main()
