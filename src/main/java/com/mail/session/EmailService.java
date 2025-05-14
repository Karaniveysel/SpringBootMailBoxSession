package com.mail.session;

import jakarta.activation.DataHandler;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;
import jakarta.mail.util.ByteArrayDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Service
public class EmailService {

    @Value("${gmail.username}")
    private String username;

    @Value("${gmail.password}")
    private String password;

    private Session getEmailSession() {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", "imap.gmail.com");
        properties.put("mail.imaps.port", "993");
        properties.put("mail.imaps.starttls.enable", "true");
        properties.put("mail.imaps.partialfetch", "false");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.host", "smtp.gmail.com");
        properties.put("mail.smtp.port", "587");
        properties.put("mail.smtp.starttls.enable", "true");

        return Session.getDefaultInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }

    public List<EmailMessage> getAllEmails() {
        List<EmailMessage> emails = new ArrayList<>();
        try {
            Session session = getEmailSession();
            Store store = session.getStore("imaps");
            store.connect("imap.gmail.com", username, password);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            Message[] msgs = inbox.getMessages();

            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            fp.add(FetchProfile.Item.FLAGS);
            fp.add("Message-ID");
            inbox.fetch(msgs, fp);

            Message[] messages = inbox.getMessages();
            for (Message message : messages) {
                EmailMessage email = convertToEmailMessage(message);
                emails.add(email);
            }

            inbox.close(false);
            store.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return emails;
    }

    public List<EmailMessage> searchEmailsBySubject(String subject) {
        List<EmailMessage> emails = new ArrayList<>();
        try {
            Session session = getEmailSession();
            Store store = session.getStore("imaps");
            store.connect("imap.gmail.com", username, password);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            SearchTerm searchTerm = new SubjectTerm(subject);
            Message[] messages = inbox.search(searchTerm);

            for (Message message : messages) {
                EmailMessage email = convertToEmailMessage(message);
                emails.add(email);
            }

            inbox.close(false);
            store.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return emails;
    }

    public EmailMessage getEmailById(String messageId) {
        try {
            Session session = getEmailSession();
            Store store = session.getStore("imaps");
            store.connect("imap.gmail.com", username, password);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            Message[] messages = inbox.getMessages();
            for (Message message : messages) {
                if (message.getHeader("Message-ID")[0].equals(messageId)) {
                    EmailMessage email = convertToEmailMessageWithAttachments(message);
                    inbox.close(false);
                    store.close();
                    return email;
                }
            }

            inbox.close(false);
            store.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void sendEmail(String to, String cc, String subject, String content, List<MultipartFile> attachments) throws Exception {
        Session session = getEmailSession();

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(username));

        String[] toAddresses = to.split(",");
        InternetAddress[] toAddressArray = new InternetAddress[toAddresses.length];
        for (int i = 0; i < toAddresses.length; i++) {
            toAddressArray[i] = new InternetAddress(toAddresses[i].trim());
        }
        message.setRecipients(Message.RecipientType.TO, toAddressArray);

        if (cc != null && !cc.isEmpty()) {
            String[] ccAddresses = cc.split(",");
            InternetAddress[] ccAddressArray = new InternetAddress[ccAddresses.length];
            for (int i = 0; i < ccAddresses.length; i++) {
                ccAddressArray[i] = new InternetAddress(ccAddresses[i].trim());
            }
            message.setRecipients(Message.RecipientType.CC, ccAddressArray);
        }

        message.setSubject(subject);

        Multipart multipart = new MimeMultipart();

        MimeBodyPart contentPart = new MimeBodyPart();
        contentPart.setContent(content, "text/html; charset=utf-8");
        multipart.addBodyPart(contentPart);

        if (attachments != null && !attachments.isEmpty()) {
            for (MultipartFile file : attachments) {
                MimeBodyPart attachmentPart = new MimeBodyPart();
                attachmentPart.setDataHandler(new DataHandler(new ByteArrayDataSource(file.getInputStream(), file.getContentType())));
                attachmentPart.setFileName(file.getOriginalFilename());
                multipart.addBodyPart(attachmentPart);
            }
        }

        message.setContent(multipart);

        Transport.send(message);
    }


    public void replyAll(String messageId, String content, List<MultipartFile> attachments) throws Exception {
        Session session = getEmailSession();
        Store store = session.getStore("imaps");
        store.connect("imap.gmail.com", username, password);

        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);

        Message[] messages = inbox.getMessages();
        for (Message message : messages) {
            if (message.getHeader("Message-ID")[0].equals(messageId)) {
                Message replyMessage = message.reply(true);

                Multipart multipart = new MimeMultipart();

                MimeBodyPart contentPart = new MimeBodyPart();
                contentPart.setContent(content, "text/html; charset=utf-8");
                multipart.addBodyPart(contentPart);

                if (attachments != null && !attachments.isEmpty()) {
                    for (MultipartFile file : attachments) {
                        MimeBodyPart attachmentPart = new MimeBodyPart();
                        attachmentPart.setDataHandler(new DataHandler(new ByteArrayDataSource(file.getInputStream(), file.getContentType())));
                        attachmentPart.setFileName(file.getOriginalFilename());
                        multipart.addBodyPart(attachmentPart);
                    }
                }

                replyMessage.setContent(multipart);
                Transport.send(replyMessage);
                break;
            }
        }

        inbox.close(false);
        store.close();
    }

    private EmailMessage convertToEmailMessage(Message message) throws Exception {
        return emailToEmailMessage(message);
    }

    private EmailMessage convertToEmailMessageWithAttachments(Message message) throws Exception {

        EmailMessage email = emailToEmailMessage(message);

        List<Attachment> attachments = new ArrayList<>();
        StringBuilder contentBuilder = new StringBuilder();

        Object content = message.getContent();
        if (content instanceof String) {
            email.setContent((String) content);
        } else if (content instanceof Multipart) {
            Multipart multipart = (Multipart) content;

            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                String contentType = bodyPart.getContentType().toLowerCase();

                if (contentType.contains("text/plain") || contentType.contains("text/html")) {
                    contentBuilder.append(bodyPart.getContent().toString());
                } else if (!contentType.contains("text/html")) {
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

                    attachments.add(attachment);
                }
            }

            email.setContent(contentBuilder.toString());
        }

        email.setAttachments(attachments);
        return email;
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
