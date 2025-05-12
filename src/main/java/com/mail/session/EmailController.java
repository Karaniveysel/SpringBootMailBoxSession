package com.mail.session;


import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    @GetMapping
    public ResponseEntity<List<EmailMessage>> getAllEmails() {
        return ResponseEntity.ok(emailService.getAllEmails());
    }

    @GetMapping("/search")
    public ResponseEntity<List<EmailMessage>> searchEmails(@RequestParam String subject) {
        return ResponseEntity.ok(emailService.searchEmailsBySubject(subject));
    }

    @GetMapping("/{messageId}")
    public ResponseEntity<EmailMessage> getEmailById(@PathVariable String messageId) {
        EmailMessage email = emailService.getEmailById(messageId);
        if (email != null) {
            return ResponseEntity.ok(email);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{messageId}/attachments/{fileName}")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable String messageId, @PathVariable String fileName) {
        EmailMessage email = emailService.getEmailById(messageId);

        if (email != null && email.getAttachments() != null) {
            for (Attachment attachment : email.getAttachments()) {
//                if (attachment.getFileName().equals(fileName)) {
                    HttpHeaders headers = new HttpHeaders();


                    headers.setContentType(MediaType.parseMediaType(attachment.getContentType()));


                    headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");

                    headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

                    return new ResponseEntity<>(attachment.getContent(), headers, HttpStatus.OK);
                }
//            }
        }

        return ResponseEntity.notFound().build();
    }

    @PostMapping("/emails/send")
    public ResponseEntity<?> sendEmail(
            @RequestParam String to,
            @RequestParam(required = false) String cc,
            @RequestParam String subject,
            @RequestParam String content,
            @RequestParam(required = false) List<MultipartFile> attachments) {

        try {
            emailService.sendEmail(to, cc, subject, content, attachments);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("E-posta gönderilirken hata oluştu: " + e.getMessage());
        }
    }

    @PostMapping("/emails/{messageId}/reply-all")
    public ResponseEntity<?> replyAll(
            @PathVariable String messageId,
            @RequestParam String content,
            @RequestParam(required = false) List<MultipartFile> attachments) {

        try {
            emailService.replyAll(messageId, content, attachments);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Yanıt gönderilirken hata oluştu: " + e.getMessage());
        }
    }
}
