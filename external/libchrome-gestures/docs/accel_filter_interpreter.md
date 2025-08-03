# Acceleration Filter Interpreter

`AccelFilterInterpreter` scales pointer motion, to improve the
usability of mice and touchpads.

# Theory of operation
Mice and touchpads must simultaneously support two different
use cases, working with motion that is constrained to a small
surface area.
* For touchpads, the area is constrained by the touchpad itself.
* For mice, the area isn't constrained by the sensor, but by
  the comfortable range of motion of human hands.

If the system maps a given amount of motion (e.g. 1mm) of/on
the sensor to the same amount of motion of the pointer on the
screen, the user will be able to make fine motions suitable
for uses like
* selecting small amounts of text
* moving an on-screen object a small number of pixels to
  finely align that object with other items on the screen.

However, such a mapping makes it difficult to navigate across
the whole width or height of the display. The user might have
to, e.g., swipe three or four times across the touchpad to
move the cursor from the left edge of the display to the right
edge.

Conversely, choosing a linear mapping that optimizes for large
motions sacrifices the ease of making small motions.

To resolve the dilemma, `AccelFilterInterpreter` handler maps
sensor motion to pointer motion using not just the distance of
the motion, but also the speed of the motion.

For touchpads:
* At low speeds, the motion is mapped linearly, to preserve
  fine control.
* At medium speeds, the motion is mapped quadratically, to
  allow rapid motion across the screen.
* At high speeds, motion is mapped linearly, using the slope
  at the end of the medium speed region.

# Notes
1. The name of this interpreter uses the word "acceleration" in
   the lay sense of "making something faster". However, this
   interpreter does not implement the physics (inertia) associated
   with the technical sense of the word "acceleration". Said
   differently: the output of this handler depends primarily
   on the current input event, not the history of input events.
2. The "primarily" hedge in point 1 hides the fact that the
   speed of motion for a given event is computed based on the
   delta between the event's timestamp, and the previous event's
   timestamp.