# Birthday Adapter

Birthday Adapter is the first implementation to really display all contact birthdays automatically in your standard Android calendar without hassle.

Birthday Adapter provides birthdays, anniversaries, and other events from your contacts as a real calendar, which is displayed in your standard Android calendar application. To my knowledge, this is the first implementation that implements birthdays as a real calendar integrated in the Android calendar. All other apps only displays their own lists but have no real integration! 

For more information visit http://code.google.com/p/birthday-adapter

# Build using Ant

1. Add a file ``local.properties`` in the folder ``Birthday-Adapter`` folder with the following lines:
``sdk.dir=/opt/android-sdk``. Alter these lines to your locations of the Android SDK!
2. Execute ```ant clear```
3. Execute ```ant debug```

# Contribute

Fork Birthday Adapter and do a Pull Request. I will merge your changes back into the main project.

# Libraries

All JAR-Libraries are provided in this repository under ``libs``, all Android Library projects are under ``android-libs``.

# Translations

Translations are hosted on Transifex, which is configured by ``.tx/config``

1. To pull newest translations install transifex client (e.g. aptitude install transifex-client)
2. Config Transifex client with ``~/.transifexrc``
3. Go into root folder of git repo
4. execute ```tx pull``` (```tx pull -a``` to get all languages)

see http://help.transifex.net/features/client/index.html#user-client

# Coding Style

## Code
* Indentation: 4 spaces, no tabs
* Maximum line width for code and comments: 100
* Opening braces don't go on their own line
* Field names: Non-public, non-static fields start with m.
* Acronyms are words: Treat acronyms as words in names, yielding !XmlHttpRequest, getUrl(), etc.

See http://source.android.com/source/code-style.html

## XML
* XML Maximum line width 999
* XML: Split multiple attributes each on a new line (Eclipse: Properties -> XML -> XML Files -> Editor)
* XML: Indent using spaces with Indention size 4 (Eclipse: Properties -> XML -> XML Files -> Editor)

See http://www.androidpolice.com/2009/11/04/auto-formatting-android-xml-files-with-eclipse/

# Licenses of included source code

## Libraries
* ColorPickerPreference  
  https://github.com/attenzione/android-ColorPickerPreference  
  Apache License v2

* CalendarProvider from Original Android 4 Source  
  Apache License v2

* HTMLCleander  
  http://htmlcleaner.sourceforge.net/  
  BSD License

* HtmlSpanner  
  Apache License v2

## Images

* icon.svg  
  Based on Tango Icon Library and Tango Pidgin Icon Theme  
  http://tango.freedesktop.org/  
  Public Domain (Tango Icon Library) and GPL (Tango Pidgin Icon Theme)