# Birthday Adapter

[<img src="https://f-droid.org/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">](https://f-droid.org/app/org.birthdayadapter)

Birthday Adapter is the first implementation to really display all contact birthdays automatically in your standard Android calendar without hassle.

Birthday Adapter provides birthdays, anniversaries, and other events from your contacts as a real calendar, which is displayed in your standard Android calendar application. To my knowledge, this is the first implementation that implements birthdays as a real calendar integrated in the Android calendar. All other apps only displays their own lists but have no real integration! 

## Screenshots

<table style="border: none;">
  <tr>
    <th>
        <a href="metadata/en-US/images/phoneScreenshots/01.png" target="_blank">
        <img src='metadata/en-US/images/phoneScreenshots/01.png' width='200px' alt='brings your contacts events to your calendar app' /> </a>
    </th>
    <th>
        <a href="metadata/en-US/images/phoneScreenshots/02.png" target="_blank">
        <img src='metadata/en-US/images/phoneScreenshots/02.png' width='200px' alt='supports all events of your contacts' /> </a>
    </th>
    <th>
        <a href="metadata/en-US/images/phoneScreenshots/03.png" target="_blank">
        <img src='metadata/en-US/images/phoneScreenshots/03.png' width='200px' alt='compatible with all calendar apps' /> </a>
    </th>
    <th>
        <a href="metadata/en-US/images/phoneScreenshots/04.png" target="_blank">
        <img src='metadata/en-US/images/phoneScreenshots/04.png' width='200px' alt='filters events by account and contact group' /> </a>
    </th>
  </tr>
  <tr>
    <th>
        <a href="metadata/en-US/images/phoneScreenshots/05.png" target="_blank">
        <img src='metadata/en-US/images/phoneScreenshots/05.png' width='200px' alt='add reminders, as many as needed' /> </a>
    </th>
    <th>
        <a href="metadata/en-US/images/phoneScreenshots/06.png" target="_blank">
        <img src='metadata/en-US/images/phoneScreenshots/06.png' width='200px' alt='customize your event labels' /> </a>
    </th>
    <th>
        <a href="metadata/en-US/images/phoneScreenshots/07.png" target="_blank">
        <img src='metadata/en-US/images/phoneScreenshots/07.png' width='200px' alt='quickly spot special birthdays' /> </a>
    </th>
    <th>
        <a href="metadata/en-US/images/phoneScreenshots/08.png" target="_blank">
        <img src='metadata/en-US/images/phoneScreenshots/08.png' width='200px' alt='dark mode supported' /> </a>
    </th>
  </tr>
</table>

## Build with Gradle

1. Have Android SDK "tools", "platform-tools", and "build-tools" directories in your PATH (http://developer.android.com/sdk/index.html)
2. Open the Android SDK Manager (shell command: ``android``). Expand the Extras directory and install "Android Support Repository"
3. Export ANDROID_HOME pointing to your Android SDK
4. Execute ``./gradlew build``

Different productFlavors are build with gradle:
- ``full``
- ``free`` without settings

## Contribute

Fork Birthday Adapter and do a Pull Request. I will merge your changes back into the main project.

## Translations

Translations are hosted on Weblate.

Help translating at https://hosted.weblate.org/engage/birthday-adapter/

<a href="https://hosted.weblate.org/engage/birthday-adapter/">
<img src="https://hosted.weblate.org/widget/birthday-adapter/multi-auto.svg" alt="Translation status" />
</a>

## Coding Style

### Code
* Indentation: 4 spaces, no tabs
* Maximum line width for code and comments: 100
* Opening braces don't go on their own line
* Field names: Non-public, non-static fields start with m.
* Acronyms are words: Treat acronyms as words in names, yielding !XmlHttpRequest, getUrl(), etc.

See http://source.android.com/source/code-style.html

### XML
* XML Maximum line width 999
* XML: Split multiple attributes each on a new line (Eclipse: Properties -> XML -> XML Files -> Editor)
* XML: Indent using spaces with Indention size 4 (Eclipse: Properties -> XML -> XML Files -> Editor)

See http://www.androidpolice.com/2009/11/04/auto-formatting-android-xml-files-with-eclipse/

## Licenses
Birthday Adapter is licensed under the GPLv3+.
The file LICENSE includes the full license text.

### Details
Birthday Adapter is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Birthday Adapter is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Birthday Adapter.  If not, see <http://www.gnu.org/licenses/>.

### Libraries
* AndroidX Libraries
  https://developer.android.com/jetpack/androidx
  Apache License 2.0

* Gemini support in Android Studio

### Images

* icon.svg
  Based on Tango Icon Library and Tango Pidgin Icon Theme
  http://tango.freedesktop.org/
  Public Domain (Tango Icon Library) and GPL (Tango Pidgin Icon Theme)
