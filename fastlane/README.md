# Fastlane metadata

Structured store metadata, in the layout F-Droid and IzzyOnDroid read
automatically. Nothing here is used by the app or the build.

    metadata/android/en-US/
      title.txt              max 50 chars
      short_description.txt  max 80 chars
      full_description.txt   max 4000 chars
      changelogs/<versionCode>.txt   max 500 chars each, one per release
      images/phoneScreenshots/       1.png, 2.png, ... in display order

`changelogs/14.txt` matches `versionCode = 14` in `app/build.gradle.kts`. Each
release needs a new file named for its versionCode, or the listing shows no
changelog for that build.

## Still missing

`images/phoneScreenshots/` is empty. Screenshots have to come off a real device
so they can't be generated here. Four are enough: the dashboard, a scenario with
its keywords, the group and message editor, and the response view after replies
have come in. Take them on a phone with fake contacts, since these images end up
public.
