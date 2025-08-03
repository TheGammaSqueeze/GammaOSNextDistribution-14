# Copyright 2020 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""A super minimal module that allows rendering of readable text/html.

Usage should be relatively straightforward. You wrap things you want to write
out in some of the nice types defined here, and then pass the result to one of
render_text_pieces/render_html_pieces.

In HTML, the types should all nest nicely. In text, eh (nesting anything in
Bold is going to be pretty ugly, probably).

Lists and tuples may be used to group different renderable elements.

Example:

render_text_pieces([
  Bold("Daily to-do list:"),
  UnorderedList([
    "Write code",
    "Go get lunch",
    ["Fix ", Bold("some"), " of the bugs in the aforementioned code"],
    [
      "Do one of the following:",
      UnorderedList([
        "Nap",
        "Round 2 of lunch",
        ["Look at ", Link("https://google.com/?q=memes", "memes")],
      ]),
    ],
    "What a rough day; time to go home",
  ]),
])

Turns into

**Daily to-do list:**
  - Write code
  - Go get lunch
  - Fix **some** of the bugs in said code
  - Do one of the following:
    - Nap
    - Round 2 of lunch
    - Look at memes
  - What a rough day; time to go home

...And similarly in HTML, though with an actual link.

The rendering functions should never mutate your input.
"""


import collections
import html
import typing as t


Bold = collections.namedtuple("Bold", ["inner"])
LineBreak = collections.namedtuple("LineBreak", [])
Link = collections.namedtuple("Link", ["href", "inner"])
UnorderedList = collections.namedtuple("UnorderedList", ["items"])
# Outputs different data depending on whether we're emitting text or HTML.
Switch = collections.namedtuple("Switch", ["text", "html"])

line_break = LineBreak()

# Note that these build up their values in a funky way: they append to a list
# that ends up being fed to `''.join(into)`. This avoids quadratic string
# concatenation behavior. Probably doesn't matter, but I care.

# Pieces are really a recursive type:
# Union[
#   Bold,
#   LineBreak,
#   Link,
#   List[Piece],
#   Tuple[...Piece],
#   UnorderedList,
#   str,
# ]
#
# It doesn't seem possible to have recursive types, so just go with Any.
Piece = t.Any  # pylint: disable=invalid-name


def _render_text_pieces(
    piece: Piece, indent_level: int, into: t.List[str]
) -> None:
    """Helper for |render_text_pieces|. Accumulates strs into |into|."""
    if isinstance(piece, LineBreak):
        into.append("\n" + indent_level * " ")
        return

    if isinstance(piece, str):
        into.append(piece)
        return

    if isinstance(piece, Bold):
        into.append("**")
        _render_text_pieces(piece.inner, indent_level, into)
        into.append("**")
        return

    if isinstance(piece, Link):
        # Don't even try; it's ugly more often than not.
        _render_text_pieces(piece.inner, indent_level, into)
        return

    if isinstance(piece, UnorderedList):
        for p in piece.items:
            _render_text_pieces([line_break, "- ", p], indent_level + 2, into)
        return

    if isinstance(piece, Switch):
        _render_text_pieces(piece.text, indent_level, into)
        return

    if isinstance(piece, (list, tuple)):
        for p in piece:
            _render_text_pieces(p, indent_level, into)
        return

    raise ValueError("Unknown piece type: %s" % type(piece))


def render_text_pieces(piece: Piece) -> str:
    """Renders the given Pieces into text."""
    into: t.List[str] = []
    _render_text_pieces(piece, 0, into)
    return "".join(into)


def _render_html_pieces(piece: Piece, into: t.List[str]) -> None:
    """Helper for |render_html_pieces|. Accumulates strs into |into|."""
    if piece is line_break:
        into.append("<br />\n")
        return

    if isinstance(piece, str):
        into.append(html.escape(piece))
        return

    if isinstance(piece, Bold):
        into.append("<b>")
        _render_html_pieces(piece.inner, into)
        into.append("</b>")
        return

    if isinstance(piece, Link):
        into.append('<a href="' + piece.href + '">')
        _render_html_pieces(piece.inner, into)
        into.append("</a>")
        return

    if isinstance(piece, UnorderedList):
        into.append("<ul>\n")
        for p in piece.items:
            into.append("<li>")
            _render_html_pieces(p, into)
            into.append("</li>\n")
        into.append("</ul>\n")
        return

    if isinstance(piece, Switch):
        _render_html_pieces(piece.html, into)
        return

    if isinstance(piece, (list, tuple)):
        for p in piece:
            _render_html_pieces(p, into)
        return

    raise ValueError("Unknown piece type: %s" % type(piece))


def render_html_pieces(piece: Piece) -> str:
    """Renders the given Pieces into HTML."""
    into: t.List[str] = []
    _render_html_pieces(piece, into)
    return "".join(into)
