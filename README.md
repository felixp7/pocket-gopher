# Pocket Gopher

The [Gopher protocol](http://en.wikipedia.org/wiki/Gopher_(protocol)) was a precursor to the World Wide Web. Gopher appeared shortly before the Web and was quickly obsoleted by it. Nowadays it is kept alive by a handful of enthusiasts, for its very real qualities and of course some amount of nostalgia.

Pocket Gopher is a client for this protocol running on Java ME devices. I wrote it because it was ridiculously easy, and because there isn't any other that I could find, at least not as of 2010-07-11.

## Features

- Supports directories, text files, searches, images and Web links (via the device Web browser).
- Session history, basic back function and caching.
- Paginated display for large directories and text files.
- Navigate by URL or host/port/type/selector (thanks to Nuno J. Silva <gopher://sdf-eu.org/1/users/njsg>).
- Works on any port, not just 70 (surprisingly enough, that's worth mentioning).
- Forward compatible with Gopher+.

## Known bugs

Sometimes directories have an extraneous blank page at the end.
