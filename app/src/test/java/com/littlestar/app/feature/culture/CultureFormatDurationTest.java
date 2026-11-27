package com.littlestar.app.feature.culture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CultureFormatDurationTest {

    @Test
    public void formatsSecondsIntoMinutesAndZeroPaddedSeconds() {
        assertEquals("0:00", CultureListActivity.formatDuration(0));
        assertEquals("0:08", CultureListActivity.formatDuration(8));
        assertEquals("0:33", CultureListActivity.formatDuration(33));
        assertEquals("1:05", CultureListActivity.formatDuration(65));
        assertEquals("5:51", CultureListActivity.formatDuration(351));
    }
}
