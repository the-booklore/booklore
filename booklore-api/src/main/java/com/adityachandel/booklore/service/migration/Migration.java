package com.adityachandel.booklore.service.migration;

public interface Migration {
    String getKey();

    String getDescription();

    void execute();
}