package com.mail.session;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class EmailMessage {
    private String messageId;
    private String subject;
    private String from;
    private List<String> to;
    private List<String> cc;
    private String content;
    private Date sentDate;
    private boolean isRead;
    private List<Attachment> attachments;
}
