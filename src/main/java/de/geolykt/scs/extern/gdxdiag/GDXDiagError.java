package de.geolykt.scs.extern.gdxdiag;

import org.jetbrains.annotations.NotNull;

public class GDXDiagError extends Error {

    private static final long serialVersionUID = -233747715286342801L;

    public GDXDiagError(@NotNull String message) {
        super(message);
    }
}
