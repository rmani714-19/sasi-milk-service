package com.sasimilkservice.user.spi;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Published SPI of the {@code user} module: resolve user ids to a display name. */
public interface UserDirectory {

    Optional<String> displayName(Long userId);

    /** Bulk lookup — ids with no matching user are simply absent from the map. */
    Map<Long, String> displayNames(Collection<Long> userIds);
}
