package com.mail.session;

import jakarta.activation.DataHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.MessageIDTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmailService {

    @Value("${gmail.username}")
    private String username;

    @Value("${gmail.password}")
    private String password;

    private Session session;
    private Store store;
    private Transport transport;

    @PostConstruct
    public void init() throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", "imap.gmail.com");
        props.put("mail.imaps.port", "993");
        props.put("mail.imaps.starttls.enable", "true");
        props.put("mail.imaps.partialfetch", "true");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.starttls.enable", "true");

        session = Session.getDefaultInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        store = session.getStore("imaps");
        store.connect(username, password);
        transport = session.getTransport("smtp");
        transport.connect(username, password);
    }

    @PreDestroy
    public void cleanup() {
        if (store != null && store.isConnected()) {
            try { store.close(); }
            catch (MessagingException ignored) {}
        }
    }

    private void ensureStoreConnected() throws MessagingException {
        if (store == null || !store.isConnected()) {
            store = session.getStore("imaps");
            store.connect(username, password);
        }
    }

    private void ensureTransportConnected() throws MessagingException {
        if (transport == null || !transport.isConnected()) {
            transport = session.getTransport("smtp");
            transport.connect(username, password);
        }
    }

    private void ensureConnections() throws MessagingException {
        ensureStoreConnected();
        ensureTransportConnected();
    }

    public EmailMessage getEmailById(String messageId, BoxType boxType) {
        Folder mailBox = null;
        try {
            ensureConnections();
            mailBox = getFolder(boxType);

            Message[] found = mailBox.search(new MessageIDTerm(messageId));
            if (found.length == 0) {
                return null;
            }

            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            mailBox.fetch(found, fp);

            return convertToEmailMessageWithAttachments(found[0]);

        } catch (MessagingException | IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            if (mailBox != null && mailBox.isOpen()) {
                try { mailBox.close(false); }
                catch (MessagingException ignored) {}
            }
        }
    }

    private Folder getFolder(BoxType boxType) throws MessagingException {
        Folder mailBox;
        if (boxType == BoxType.INBOX) {
            mailBox = store.getFolder("INBOX");
            mailBox.open(Folder.READ_ONLY);
        } else if (boxType == BoxType.SENT) {
            mailBox = store.getFolder("[Gmail]/Sent Mail");
            if (!mailBox.exists()) {
                mailBox = store.getFolder("Sent");
            }
        } else {
            throw new RuntimeException("Box not found");
        }
        return mailBox;
    }

    public List<EmailMessage> getAllEmails(BoxType boxType) {

        List<EmailMessage> emails = new ArrayList<>();
        Folder mailBox = null;

        try {

            ensureConnections();
            mailBox = getFolder(boxType);

            Message[] messages = mailBox.getMessages();
            if (messages.length == 0) {
                return emails;
            }

            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            fp.add(FetchProfile.Item.FLAGS);
            fp.add("Message-ID");
            mailBox.fetch(messages, fp);

            for (Message msg : messages) {
                emails.add(convertToEmailMessage(msg));
            }

        } catch (MessagingException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            if (mailBox != null && mailBox.isOpen()) {
                try { mailBox.close(false); }
                catch (MessagingException ignored) {}
            }
        }

        return emails;
    }

    public List<EmailMessage> searchEmailsBySubject(String subject, BoxType boxType) {
        List<EmailMessage> emails = new ArrayList<>();
        Folder mailBox = null;

        try {
            ensureConnections();
            mailBox = getFolder(boxType);

            SearchTerm term = new SubjectTerm(subject);
            Message[] found = mailBox.search(term);
            if (found.length == 0) {
                return emails;
            }

            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            mailBox.fetch(found, fp);

            for (Message msg : found) {
                emails.add(convertToEmailMessage(msg));
            }

        } catch (MessagingException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            if (mailBox != null && mailBox.isOpen()) {
                try { mailBox.close(false); }
                catch (MessagingException ignored) {}
            }
        }

        return emails;
    }

    public void sendEmail(
            String to,
            String cc,
            String subject,
            String content,
            List<MultipartFile> attachments
    ) throws MessagingException, IOException {

        ensureConnections();
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(username));

        InternetAddress[] toAddress = InternetAddress.parse(to, false);
        msg.setRecipients(Message.RecipientType.TO, toAddress);

        if (cc != null && !cc.isBlank()) {
            InternetAddress[] ccAddress = InternetAddress.parse(cc, false);
            msg.setRecipients(Message.RecipientType.CC, ccAddress);
        }

        msg.setSubject(subject, "UTF-8");

        Multipart multipart = new MimeMultipart();

        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setContent(content, "text/html; charset=UTF-8");
        multipart.addBodyPart(bodyPart);

        if (attachments != null) {
            for (MultipartFile file : attachments) {
                MimeBodyPart attachPart = new MimeBodyPart();
                attachPart.setFileName(file.getOriginalFilename());
                attachPart.setDataHandler(new DataHandler(
                        new ByteArrayDataSource(file.getInputStream(), file.getContentType())
                ));
                multipart.addBodyPart(attachPart);
            }
        }

        msg.setContent(multipart);
        msg.setSentDate(new java.util.Date());

        transport.sendMessage(msg, msg.getAllRecipients());
    }


    public void replyAll(
            String messageId,
            String content,
            BoxType boxType,
            List<MultipartFile> attachments
    ) throws MessagingException, IOException {
        ensureConnections();
        Folder mailBox = null;
        try {
            mailBox = getFolder(boxType);

            Message[] found = mailBox.search(new MessageIDTerm(messageId));
            if (found.length == 0) {
                return;
            }

            MimeMessage original = (MimeMessage) found[0];
            MimeMessage reply = (MimeMessage) original.reply(true);
            reply.setFrom(new InternetAddress(username));
            reply.setSentDate(new Date());

            Multipart multipart = new MimeMultipart();

            MimeBodyPart bodyPart = new MimeBodyPart();
            bodyPart.setContent(content, "text/html; charset=UTF-8");
            multipart.addBodyPart(bodyPart);

            if (attachments != null) {
                for (MultipartFile file : attachments) {
                    MimeBodyPart attachPart = new MimeBodyPart();
                    attachPart.setDataHandler(new DataHandler(
                            new ByteArrayDataSource(file.getInputStream(), file.getContentType())
                    ));
                    attachPart.setFileName(file.getOriginalFilename());
                    multipart.addBodyPart(attachPart);
                }
            }
            reply.setContent(multipart);

            transport.sendMessage(reply, reply.getAllRecipients());
        } finally {
            if (mailBox != null && mailBox.isOpen()) {
                try { mailBox.close(false); }
                catch (MessagingException ignored) {}
            }
        }
    }

    private EmailMessage convertToEmailMessage(Message message) {
        try {
            return emailToEmailMessage(message);
        } catch (MessagingException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private EmailMessage convertToEmailMessageWithAttachments(Message message) throws MessagingException, IOException {

        EmailMessage email = emailToEmailMessage(message);

        List<Attachment> attachments = new ArrayList<>();
        StringBuilder contentBuilder = new StringBuilder();

        Object content = message.getContent();
        if (content instanceof String) {
            email.setContent((String) content);
        } else if (content instanceof Multipart multipart) {

            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                String contentType = bodyPart.getContentType().toLowerCase();

                if (contentType.contains("text/plain") || contentType.contains("text/html")) {
                    contentBuilder.append(bodyPart.getContent().toString());
                } else if (!contentType.contains("text/html")) {
                    Attachment attachment = getAttachment(bodyPart);
                    attachments.add(attachment);
                }
            }

            email.setContent(contentBuilder.toString());
        }

        email.setAttachments(attachments);
        return email;
    }

    private Attachment getAttachment(BodyPart bodyPart) throws MessagingException, IOException {
        Attachment attachment = new Attachment();
        attachment.setFileName(bodyPart.getFileName());
        attachment.setContentType(bodyPart.getContentType());

        InputStream is = bodyPart.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        attachment.setContent(baos.toByteArray());
        return attachment;
    }

    private EmailMessage emailToEmailMessage(Message message) throws MessagingException {
        EmailMessage email = new EmailMessage();
        email.setMessageId(message.getHeader("Message-ID")[0]);
        email.setSubject(message.getSubject());
        email.setFrom(((InternetAddress) message.getFrom()[0]).getAddress());
        email.setSentDate(message.getSentDate());
        email.setRead(message.isSet(Flags.Flag.SEEN));

        Address[] toAddresses = message.getRecipients(Message.RecipientType.TO);
        if (toAddresses != null) {
            email.setTo(Arrays.stream(toAddresses)
                    .map(address -> ((InternetAddress) address).getAddress())
                    .collect(Collectors.toList()));
        } else {
            email.setTo(new ArrayList<>());
        }

        Address[] ccAddresses = message.getRecipients(Message.RecipientType.CC);
        if (ccAddresses != null) {
            email.setCc(Arrays.stream(ccAddresses)
                    .map(address -> ((InternetAddress) address).getAddress())
                    .collect(Collectors.toList()));
        } else {
            email.setCc(new ArrayList<>());
        }

        return email;
    }

}
