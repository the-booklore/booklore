package com.adityachandel.booklore.model.dto.kobo;

import java.util.ArrayList;
import java.util.Collection;

public class KoboReadingStateList extends ArrayList<KoboReadingState> {
    public KoboReadingStateList() {
        super();
    }

    public KoboReadingStateList(Collection<KoboReadingState> states) {
        super(states);
    }
}
