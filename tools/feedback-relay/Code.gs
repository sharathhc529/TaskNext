/**
 * TaskNext feedback relay (Google Apps Script web app).
 *
 * Receives JSON from the Android app and emails it to you as a designed HTML message.
 * Script properties (Project Settings -> Script properties):
 *   APP_TOKEN     required  must match feedbackToken in android/secrets.properties
 *   NOTIFY_EMAIL  optional  where to send; defaults to the account that owns this script
 */

const MAX_PER_INSTALL_PER_HOUR = 5;
const MAX_PER_DAY = 80; // MailApp's free quota is 100/day

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
    const props = PropertiesService.getScriptProperties();
    const data = JSON.parse((e && e.postData && e.postData.contents) || '{}');

    if (!data.token || data.token !== props.getProperty('APP_TOKEN')) return reply_(false, 'unauthorized');
    if (data.kind !== 'feedback') return reply_(false, 'unknown kind');

    const f = {
      app: clean_(data.app, 40) || 'TaskNext',
      version: clean_(data.version, 20),
      android: clean_(data.android, 10),
      name: clean_(data.name, 80),
      email: clean_(data.email, 120),
      location: clean_(data.location, 120),
      message: clean_(data.message, 2000, true),
      installId: clean_(data.installId, 64)
    };
    if (!f.name || !f.location) return reply_(false, 'missing fields');
    if (!f.message || !isEmail_(f.email)) return reply_(false, 'missing fields');

    // Rate limits: per install per hour, and overall per day
    const cache = CacheService.getScriptCache();
    const installKey = 'install:' + (f.installId || 'unknown');
    const installCount = Number(cache.get(installKey) || 0);
    if (installCount >= MAX_PER_INSTALL_PER_HOUR) return reply_(false, 'slow down');
    const dayKey = 'day:' + Utilities.formatDate(new Date(), 'UTC', 'yyyyMMdd');
    const dayCount = Number(props.getProperty(dayKey) || 0);
    if (dayCount >= MAX_PER_DAY) return reply_(false, 'daily limit');
    cache.put(installKey, String(installCount + 1), 3600);
    props.setProperty(dayKey, String(dayCount + 1));

    const to = props.getProperty('NOTIFY_EMAIL') || Session.getEffectiveUser().getEmail();
    const mail = feedbackEmail_(f);
    const options = { name: f.app + ' Bot', htmlBody: mail.html };
    if (isEmail_(f.email)) options.replyTo = f.email;
    MailApp.sendEmail(to, mail.subject, mail.text, options);
    return reply_(true);
  } catch (err) {
    console.error(err);
    return reply_(false, 'server error');
  } finally {
    lock.releaseLock();
  }
}

function feedbackEmail_(f) {
  const first = f.name.split(' ')[0];
  return {
    subject: '💬 ' + first + ' (' + f.location + ') shared feedback on ' + f.app,
    text: f.name + ' <' + f.email + '>, ' + f.location + '\n\n' + f.message,
    html: shell_(f, 'New feedback for ' + esc_(f.app),
      '<blockquote style="margin:0 0 18px;padding:14px 16px;border-left:4px solid #FB923C;background:#FFF7ED;' +
      'border-radius:8px;font-size:16px;line-height:1.55;color:#1F2937;white-space:pre-wrap">' + esc_(f.message) + '</blockquote>' +
      table_([
        ['From', esc_(f.name)],
        ['Email', '<a href="mailto:' + esc_(f.email) + '" style="color:#EA580C">' + esc_(f.email) + '</a>'],
        ['Location', mapLink_(f.location)],
        ['App', esc_(f.app) + ' v' + esc_(f.version) + ' · Android ' + esc_(f.android)]
      ]) +
      '<p style="margin:20px 0 0"><a href="mailto:' + esc_(f.email) + '?subject=' +
      encodeURIComponent('Re: your ' + f.app + ' feedback') + '" style="display:inline-block;background:#0F172A;' +
      'color:#FDE047;text-decoration:none;padding:10px 18px;border-radius:999px;font-weight:600">Reply to ' + esc_(first) + '</a></p>')
  };
}

function shell_(f, heading, body) {
  const when = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), "EEE d MMM yyyy, h:mm a z");
  return '<div style="background:#F1F5F9;padding:24px 12px;font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif">' +
    '<div style="max-width:560px;margin:0 auto;background:#FFFFFF;border-radius:16px;overflow:hidden;border:1px solid #E2E8F0">' +
    '<div style="background:linear-gradient(135deg,#0F172A,#1E2A44);padding:22px 24px">' +
    '<div style="font-size:12px;letter-spacing:2px;text-transform:uppercase;color:#FDE047">' + esc_(f.app) + '</div>' +
    '<div style="font-size:22px;font-weight:700;color:#FFFFFF;margin-top:6px">' + heading + '</div></div>' +
    '<div style="padding:22px 24px;color:#1F2937">' + body + '</div>' +
    '<div style="padding:14px 24px;background:#F8FAFC;font-size:12px;color:#94A3B8">Sent by the ' + esc_(f.app) +
    ' app · ' + esc_(when) + '</div></div></div>';
}

function table_(rows) {
  return '<table style="width:100%;border-collapse:collapse;font-size:14px">' + rows.map(function (r) {
    return '<tr><td style="padding:8px 0;color:#64748B;width:130px;border-bottom:1px solid #F1F5F9">' + r[0] +
      '</td><td style="padding:8px 0;border-bottom:1px solid #F1F5F9">' + r[1] + '</td></tr>';
  }).join('') + '</table>';
}

function mapLink_(place) {
  return '<a href="https://www.google.com/maps/search/' + encodeURIComponent(place) +
    '" style="color:#EA580C">' + esc_(place) + '</a> 📍';
}

function clean_(value, max, multiline) {
  let s = String(value == null ? '' : value);
  s = multiline ? s.replace(/[^\S\n]+/g, ' ') : s.replace(/\s+/g, ' ');
  return s.replace(/[\u0000-\u0009\u000B-\u001F\u007F]/g, '').trim().slice(0, max);
}

function esc_(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function isEmail_(s) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s || '');
}

function reply_(ok, error) {
  return ContentService.createTextOutput(JSON.stringify(ok ? { ok: true } : { ok: false, error: error }))
    .setMimeType(ContentService.MimeType.JSON);
}

/** Run once from the editor to preview the feedback email in your inbox (uses the real template). */
function sendTestEmails() {
  const sample = { app: 'TaskNext', version: '1.2.1', android: '14', name: 'Asha Rao', email: 'asha@example.com',
    location: 'Bengaluru, India', message: 'Love the ring icon! Could snooze have a 10-minute option too?' };
  const to = PropertiesService.getScriptProperties().getProperty('NOTIFY_EMAIL') || Session.getEffectiveUser().getEmail();
  const m = feedbackEmail_(sample);
  MailApp.sendEmail(to, '[TEST] ' + m.subject, m.text, { name: 'TaskNext Bot', htmlBody: m.html });
  console.log('Test email sent to ' + to + ' | emails left today: ' + MailApp.getRemainingDailyQuota());
}
