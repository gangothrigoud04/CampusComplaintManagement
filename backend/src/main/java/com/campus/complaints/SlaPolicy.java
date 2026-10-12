package com.campus.complaints;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class SlaPolicy {

    private final Map<ComplaintPriority, Integer> targetHours;
    private final int dueSoonPercent;

    public SlaPolicy(
            @Value("${app.sla.hours.urgent:4}") int urgentHours,
            @Value("${app.sla.hours.high:24}") int highHours,
            @Value("${app.sla.hours.medium:72}") int mediumHours,
            @Value("${app.sla.hours.low:120}") int lowHours,
            @Value("${app.sla.due-soon-percent:20}") int dueSoonPercent) {
        if (urgentHours <= 0 || highHours <= 0 || mediumHours <= 0 || lowHours <= 0
                || urgentHours > 32767 || highHours > 32767 || mediumHours > 32767 || lowHours > 32767) {
            throw new IllegalArgumentException("SLA target hours must be between 1 and 32767.");
        }
        if (dueSoonPercent <= 0 || dueSoonPercent > 100) {
            throw new IllegalArgumentException("SLA due-soon percentage must be between 1 and 100.");
        }
        targetHours = Map.of(
                ComplaintPriority.URGENT, urgentHours,
                ComplaintPriority.HIGH, highHours,
                ComplaintPriority.MEDIUM, mediumHours,
                ComplaintPriority.LOW, lowHours);
        this.dueSoonPercent = dueSoonPercent;
    }

    public int targetHours(ComplaintPriority priority) {
        return targetHours.get(priority);
    }

    public Duration dueSoonWindow(int targetHours) {
        return Duration.ofMinutes((long) targetHours * 60 * dueSoonPercent / 100);
    }

    public int dueSoonPercent() {
        return dueSoonPercent;
    }

    public int escalationLevel(long overdueHours) {
        if (overdueHours < 0) {
            return 0;
        }
        if (overdueHours >= 48) {
            return 3;
        }
        if (overdueHours >= 24) {
            return 2;
        }
        return 1;
    }
}
