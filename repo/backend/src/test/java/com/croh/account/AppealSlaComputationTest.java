package com.croh.account;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AppealSlaComputationTest {

    private final BlacklistService service = new BlacklistService(null, null, null, null);

    @Test
    void computeDueDate_fromMonday_returns3BusinessDaysLater() {
        // Monday April 6, 2026 -> Thursday April 9, 2026
        LocalDate monday = LocalDate.of(2026, 4, 6);
        LocalDate due = service.computeDueDate(monday);
        assertEquals(LocalDate.of(2026, 4, 9), due);
    }

    @Test
    void computeDueDate_fromWednesday_returns3BusinessDaysLater() {
        // Wednesday April 8, 2026 -> Monday April 13, 2026 (skips Sat+Sun)
        LocalDate wednesday = LocalDate.of(2026, 4, 8);
        LocalDate due = service.computeDueDate(wednesday);
        assertEquals(LocalDate.of(2026, 4, 13), due);
    }

    @Test
    void computeDueDate_fromThursday_skipsWeekend() {
        // Thursday April 9, 2026 -> Tuesday April 14, 2026
        LocalDate thursday = LocalDate.of(2026, 4, 9);
        LocalDate due = service.computeDueDate(thursday);
        assertEquals(LocalDate.of(2026, 4, 14), due);
    }

    @Test
    void computeDueDate_fromFriday_skipsWeekend() {
        // Friday April 10, 2026 -> Wednesday April 15, 2026
        LocalDate friday = LocalDate.of(2026, 4, 10);
        LocalDate due = service.computeDueDate(friday);
        assertEquals(LocalDate.of(2026, 4, 15), due);
    }

    @Test
    void computeDueDate_neverLandsOnWeekend() {
        // Test a range of start dates
        for (int i = 0; i < 14; i++) {
            LocalDate start = LocalDate.of(2026, 4, 1).plusDays(i);
            LocalDate due = service.computeDueDate(start);
            assertNotEquals(DayOfWeek.SATURDAY, due.getDayOfWeek(),
                    "Due date should not be Saturday for start " + start);
            assertNotEquals(DayOfWeek.SUNDAY, due.getDayOfWeek(),
                    "Due date should not be Sunday for start " + start);
        }
    }
}
