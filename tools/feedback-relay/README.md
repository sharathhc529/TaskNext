# Feedback relay (Google Apps Script)

Emails you each **Suggestions & feedback** submission from the app. Runs free in your own Google account; turn it off any time by archiving the deployment.

## Set up (about 5 minutes)

1. Open <https://script.new> while signed in to the Gmail account that should receive the emails.
2. Replace the editor contents with [`Code.gs`](Code.gs) and save. Name the project **TaskNext feedback relay**.
3. **Project Settings → Script properties → Add property**
   - `APP_TOKEN` = the `feedbackToken` value from `android/secrets.properties` (local file, not in git)
   - `NOTIFY_EMAIL` = your address (optional; defaults to the script owner)
4. Optional preview: select `sendTestEmails` in the toolbar → **Run** → approve the permissions. A sample feedback email lands in your inbox.
5. **Deploy → New deployment → Web app**
   - Execute as: **Me**
   - Who has access: **Anyone**
   - Deploy, then copy the **Web app URL** (`https://script.google.com/macros/s/…/exec`).
6. Put the URL in `android/secrets.properties` as `feedbackEndpoint=…` and rebuild the app.

## Safety built in

- Requests without the matching `APP_TOKEN` are ignored.
- 5 submissions per install per hour, 80 per day overall (Gmail's free limit is 100/day).
- Every field is length-limited and HTML-escaped before it goes into the email.
- The token ships inside the app, so treat it as a spam filter, not a password. If it leaks and gets abused, change `APP_TOKEN` and `feedbackToken`, then publish a new app version.
