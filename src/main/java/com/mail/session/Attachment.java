package com.mail.session;

import lombok.Data;

@Data
public class Attachment {
    private String fileName;
    private byte[] content;
    private String contentType;
}