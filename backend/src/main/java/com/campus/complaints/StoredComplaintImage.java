package com.campus.complaints;

public record StoredComplaintImage(String reference, String contentType, byte[] bytes) {
}
