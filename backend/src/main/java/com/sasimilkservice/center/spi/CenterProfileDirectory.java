package com.sasimilkservice.center.spi;

/** Published SPI of the {@code center} module: the business name and contact lines printed on bills and reports. */
public interface CenterProfileDirectory {

    CenterProfile profile();

    record CenterProfile(String name, String address, String mobileNumber) {
    }
}
