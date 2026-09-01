# WhatsApp template: `classhub_notification`

Create this template in Meta WhatsApp Manager and wait for approval before enabling WhatsApp delivery.

- Category: Utility
- Name: `classhub_notification`
- Language: English (`en`)
- Body:

```text
Hello {{1}},

{{2}}
{{3}}

Open in ClassHub: {{4}}
```

Variable mapping:

1. Student first name
2. Notification title
3. Notification message
4. Absolute ClassHub action URL

After approval, put the template name/language, permanent system-user access token, and phone-number ID in `.env`, then set `CLASSHUB_WHATSAPP_NOTIFICATIONS_ENABLED=true`.
