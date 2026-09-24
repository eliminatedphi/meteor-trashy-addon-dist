# BIBLIOTHECA ARCANA

A management utility for maps in a certain block game. Formerly known as mapman.

This folder contains the source code for the native application portion of Bibliotheca Arcana.
It is intended to be used with the BA Integration Meteor module in game.

The legacy workflow is still supported for now, but won't be described in the document.

## Building the native application

It's a cmake project. Simply follow common build instructions for cmake projects, for example:

```
cmake -DCMAKE_BUILD_TYPE=RelWithDebInfo -B build
cmake --build build -j $(nproc)
```

(Run these commands in the same directory of this file.)

You will need the development files for Qt 6, sqlite and zlib to build Bibliotheca Arcana.

Bibliotheca Arcana is only tested on Linux. It likely will not build on other platforms.
However the code itself is fairly portable and should not take too much work to be ported
to use abstractions of other platforms, e.g. winsock2.

## Features

 - Import and organize your maps in the library.
 - Automatically highlight inventory slots that holds a map that's not in the library.
   (Works for bundles and shulkers too!)
 - Automatically highlight item frames that holds a map that's not in the library.
   (Works for bundles and shulkers too!)
 - Highlight the currently selected map in game (item frames and / or inventory slots).
 - Automatically import maps placed in item frames as you explore the world (can be disabled).

## Terminology

 - "Slice" or "map slice" refers to an individual filled map item in the game or the content
   it carries.
 - "Art" or "map art" refers to a bunch of slices arranged in a certain way to depict a complete
   image. Additionally, each art can be assigned a title and an author (or authors if you wish).

## Subwindows

 - Map listings shows all slices currently in the library.
   - Use the filter box to search individual maps. You may search for map ids here too.
 - Map art listings shows all arts in the library.
   - Set which slice to display in each position by dragging the slice on the left side of the
   map listings window onto it. Currently map slices cannot be rotated.
   - When you select an art on the left side of this window, item frames and / or inventory slots
   containing slices of this art will be highlighted in game. You can choose what to highlight in
   the Integration menu. This feature requires the BA integration module to be active in your
   client. Highlighting item frames also requires the ESP module to be enabled. Highlighting
   inventory slots requires the Slot Highlight module to be enabled. The default highlight color
   can be changed in the Integration menu.
   - Click + to add an new entry.
   - Click - to delete the currently selected entry.
   - Click Save to save changes made to the current art.
   - Use the filter box to search for arts with a certain title. If you want to search for authors
   too, check the "Search authors" box.

## Menu items

 - File
   - Create / Load MapDB: create a new map database or load an existing one. Database is always
   saved as soon as you make a change, so be sure to back up your mapdb file accordingly.
   - Close MapDB: close the current MapDB. Buggy and untested.
   - Export Current Art: export the currently selected art in Map art listings window as a PNG.
   - Export All Arts: export all saved arts in the library to a folder as PNG files. Exported
   files are named `<art id>_<art name>.png`.
   - Find unused slices: highlight any slice that doesn't belong to an art in the map listings
   window. The color is configurable in the Integration menu.
   - Load Map Dump: for legacy workflow only. Do not use.
   - Compare Map Tally: for legacy workflow only. Do not use.
   - Quit: Feeling tired?
 - Integration
   - Set default highlight color: see the section on Map art listings window.
   - Set not collected color: any framed maps in the world / map items in inventory / containers
   that has such maps that are not in the library will be highlighted with this color.
   - Set unused slices color: see Find unused slices.
   - Highlight item frames: enable / disable all highlighting of item frames from BA.
   - Highlight inventory slots: enable / disable all highlighting of inventory slots from BA.
   - Enable auto import: If enabled, maps inside any item frames that comes into your view
   distance will be imported automatically.
   - Import all framed maps now: Import maps from all item frames containing a map within your
   view distance.
 - Windows: show / hide subwindows.
 - Help: completely useless.

## General workflow

 - Put your collection up in item frames in game.
 - Stand still in game with the item frames around you. Use "Import all framed maps now" in the
 menu to import all map slices within your view distance. Repeat if needed.
 - In the Map art listings window, click "+". Select appropriate size for the art. Drag each slice
 onto its position inside the display. Enter title and author information. Click save. Repeat for
 everything in your collection.

## Notes

 - Currently BA only uses map id to uniquely identify a map. It makes no attempt to recognize
 duplicate maps using their color data. Therefore it will not work on servers that don't send a
 stable map id.
