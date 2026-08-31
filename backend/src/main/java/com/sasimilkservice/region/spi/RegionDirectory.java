package com.sasimilkservice.region.spi;

import java.util.Optional;

/** Published SPI of the {@code region} module for cross-module lookups. */
public interface RegionDirectory {

    Optional<RegionView> findById(Long id);

    boolean isActive(Long id);

    record RegionView(Long id, String code, String name, boolean active) {
    }
}
