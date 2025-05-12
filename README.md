# Email Management Application

This project is a Spring Boot application that connects to your Gmail account and allows you to manage email operations. The application provides basic email functionalities such as listing, searching, viewing, sending, and replying to emails.

## Features

- **Email Listing**: View all emails in your Gmail inbox
- **Email Search**: Search emails by subject
- **Email Details**: View the content and attachments of a specific email
- **Email Sending**: Create and send new emails (including file attachments)
- **Reply All**: Reply to an email including all recipients
- **Attachment Download**: Download email attachments

## Technologies

- Java 21+
- Spring Boot 3
- Jakarta Mail API
- Lombok
- HTML/CSS/JavaScript (Frontend)

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/emails` | GET | Lists all emails |
| `/api/emails/search?subject={subject}` | GET | Searches emails by subject |
| `/api/emails/{messageId}` | GET | Gets details of a specific email |
| `/api/emails/{messageId}/attachments/{fileName}` | GET | Downloads an email attachment |
| `/api/emails/send` | POST | Sends a new email |
| `/api/emails/{messageId}/reply-all` | POST | Replies to an email with all recipients included |

## Security Notes

- This application stores your Gmail account information in the `application.yml` file. In a real production environment, it is recommended to use environment variables or a secure configuration management system to store this information securely.
- Before making the application publicly accessible, remember to add appropriate authentication and authorization mechanisms.


## License

This project is licensed under the [MIT License](LICENSE).