package com.campus.complaints;

import java.time.LocalDateTime;

public record ComplaintNoteResponse(
        Long id,
        String authorName,
        UserRole authorRole,
        String content,
        ComplaintNoteType type,
        LocalDateTime createdAt) {

    public static ComplaintNoteResponse from(ComplaintWorkNote note) {
        return new ComplaintNoteResponse(
                note.getId(),
                note.getAuthor().getFullName(),
                note.getAuthor().getRole(),
                note.getNote(),
                note.getRecordType(),
                note.getCreatedAt());
    }
}
