# star-cell-shading

A mod for galimulator 5.0.2 which replaces the vanilla way of drawing star regions
with custom shader code to give them a more curvy look. With that it is a departure
of the traditional vector-graphic artstyle of vanilla galimulator into a different
realm.

At the end of the day this mod can be described as one that creates excessive bloom,
where as the emission color of the bloom is the color of the region (which depends
on the current active map mode) and the bloom of differing colors are substracted
from each other, creating a region of black nothingness where different colors meet.
As a upside, this allows one to quickly find frontlines.

If wanted, this mod can be tweaked or disabled within the mod settings menu.
On some MapModes, this mod is automatically disabled, especially wherein information
would be lost - as would be the case in the faction or religion views.

This mod requires OpenGL 3.1 to be active for performance reasons as the
mod makes use of `glPrimitiveRestartIndex`. As vanilla galimulator only
requires OpenGL 2.0 ES to be present, this may cause slight issueson certain hardware
or drivers.

Performance wise, this mod can improve on the vanilla performance as it does not
make use of any excessive texture swaps nor excessive object allocations, but will
be quite bad as it will create a full-size framebuffer for each rendered color.
If a lot of colors are present (e.g. because an empire is rioting), framerate may
tank.
