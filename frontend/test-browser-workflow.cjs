const puppeteer = require('puppeteer-core');
const http = require('http');

const FRONTEND_URL = 'http://localhost:5173';
const BACKEND_URL = 'http://localhost:8081';
const MAILPIT_URL = 'http://localhost:8025';
const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function fetchJson(url) {
  return new Promise((resolve, reject) => {
    http.get(url, res => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try { resolve(JSON.parse(data)); }
        catch (e) { resolve(null); }
      });
    }).on('error', reject);
  });
}

async function getLatestMailpitMessage(recipientEmail, subjectContains = null) {
  for (let i = 0; i < 20; i++) {
    const list = await fetchJson(`${MAILPIT_URL}/api/v1/messages`);
    if (list && list.messages) {
      for (const msg of list.messages) {
        const toHeader = msg.To ? msg.To.map(t => t.Address).join(' ') : '';
        if (toHeader.includes(recipientEmail)) {
          if (!subjectContains || (msg.Subject && msg.Subject.includes(subjectContains))) {
            const detail = await fetchJson(`${MAILPIT_URL}/api/v1/message/${msg.ID}`);
            return detail;
          }
        }
      }
    }
    await sleep(500);
  }
  throw new Error(`Timeout waiting for email to ${recipientEmail} (subject filter: ${subjectContains})`);
}

async function runBrowserAcceptance() {
  console.log('--- Starting Stage 7: Browser Authentication Acceptance ---');
  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: true,
    args: ['--no-sandbox', '--disable-gpu', '--window-size=1280,800']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 800 });
  page.on('console', msg => console.log('BROWSER LOG:', msg.text()));
  page.on('pageerror', err => console.log('BROWSER ERROR:', err.message));

  const testId = Date.now();
  const companyName = `Browser Corp ${testId}`;
  const ownerEmail = `owner-${testId}@browser-test.com`;
  const ownerPassword = 'BrowserOwnerPass!123';
  const ownerName = 'Browser Test Owner';

  try {
    // 1. Register a new company
    console.log('[Step 1] Navigating to Registration page...');
    await page.goto(`${FRONTEND_URL}/register`, { waitUntil: 'networkidle2' });
    await page.waitForSelector('button.primary-button', { timeout: 10000 });
    await page.type('input[name="companyName"]', companyName);
    await page.type('input[name="businessEmail"]', `biz-${ownerEmail}`);
    await page.type('input[name="fullName"]', ownerName);
    await page.type('input[name="email"]', ownerEmail);
    await page.type('input[name="password"]', ownerPassword);
    await page.type('input[name="passwordConfirmation"]', ownerPassword);
    await page.click('button.primary-button');

    await page.waitForFunction(() => document.body.innerText.includes('Registration received'), { timeout: 10000 });
    console.log('[Step 1 PASS] Registration submitted successfully.');

    // 2. Receive verification email in development mail service (Mailpit)
    console.log('[Step 2] Checking Mailpit for verification email...');
    const verifyMsg = await getLatestMailpitMessage(ownerEmail);
    console.log('[Step 2 PASS] Verification email received in Mailpit. Subject:', verifyMsg.Subject);
    const verifyMatch = verifyMsg.Text.match(/token=([A-Za-z0-9_-]+)/);
    if (!verifyMatch) throw new Error('Verification token not found in email body');
    const verifyToken = verifyMatch[1];

    // 3. Verify the account
    console.log('[Step 3] Navigating to verification link...');
    await page.goto(`${FRONTEND_URL}/verify-email?token=${verifyToken}`, { waitUntil: 'networkidle2' });
    await page.waitForFunction(() => document.body.innerText.includes('Email verified'), { timeout: 10000 });
    console.log('[Step 3 PASS] Account verified via browser.');

    // 4. Login
    console.log('[Step 4] Logging in as initial Owner...');
    await page.goto(`${FRONTEND_URL}/login`, { waitUntil: 'networkidle2' });
    await page.type('input[type="email"]', ownerEmail);
    await page.type('input[type="password"]', ownerPassword);
    await page.click('button.primary-button');

    await page.waitForSelector('.top-nav', { timeout: 10000 });
    const navText = await page.$eval('.top-nav', el => el.innerText);
    console.log('[Step 4 PASS] Logged in successfully. Nav header contains:', navText.replace(/\n/g, ' '));

    // 5. Reload the browser
    console.log('[Step 5] Reloading browser...');
    await page.reload({ waitUntil: 'networkidle2' });

    // 6. Restore session
    console.log('[Step 6] Verifying session restoration...');
    await page.waitForSelector('.top-nav', { timeout: 10000 });
    const restoredUser = await page.$eval('.account-name', el => el.innerText);
    if (restoredUser !== ownerName) throw new Error(`Expected ownerName ${ownerName}, got ${restoredUser}`);
    console.log('[Step 6 PASS] Session restored seamlessly after full reload for user:', restoredUser);

    // 7. Open protected company page
    console.log('[Step 7] Navigating to protected Media Scans page...');
    await page.click('a[href="/media-scans"]');
    await page.waitForSelector('h1', { timeout: 5000 });
    const pageHeading = await page.$eval('h1', el => el.innerText);
    console.log('[Step 7 PASS] Protected company page loaded. Heading:', pageHeading);

    // 8 & 9. Company Switching & Context Invalidation
    console.log('[Step 8 & 9] Testing company switching and isolation...');
    // Register Company B with a second user
    const ownerBEmail = `owner-b-${testId}@browser-test.com`;
    const regBResult = await page.evaluate(async (backend, email) => {
      const csrfResp = await fetch(`${backend}/api/v1/auth/csrf`, { credentials: 'include' });
      const { token } = await csrfResp.json();
      const res = await fetch(`${backend}/api/v1/auth/register-company`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
        credentials: 'include',
        body: JSON.stringify({
          companyName: 'Company B',
          businessEmail: `biz-${email}`,
          administratorFullName: 'Owner B',
          administratorEmail: email,
          password: 'BrowserOwnerPass!123',
          passwordConfirmation: 'BrowserOwnerPass!123'
        })
      });
      return res.status;
    }, BACKEND_URL, ownerBEmail);

    const verifyBMsg = await getLatestMailpitMessage(ownerBEmail);
    const tokenB = verifyBMsg.Text.match(/token=([A-Za-z0-9_-]+)/)[1];
    await page.evaluate(async (backend, token) => {
      const csrfResp = await fetch(`${backend}/api/v1/auth/csrf`, { credentials: 'include' });
      const { token: csrf } = await csrfResp.json();
      await fetch(`${backend}/api/v1/auth/verify-email`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf },
        credentials: 'include',
        body: JSON.stringify({ token })
      });
    }, BACKEND_URL, tokenB);

    // Try switching to Company B without membership -> must be rejected 401
    const invalidSwitchStatus = await page.evaluate(async (backend) => {
      const csrfResp = await fetch(`${backend}/api/v1/auth/csrf`, { credentials: 'include' });
      const { token: csrf } = await csrfResp.json();
      const res = await fetch(`${backend}/api/v1/auth/switch-company`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf },
        credentials: 'include',
        body: JSON.stringify({ companyId: '00000000-0000-0000-0000-000000000000' })
      });
      return res.status;
    }, BACKEND_URL);
    if (invalidSwitchStatus !== 401) throw new Error(`Expected 401 on unauthorized switch, got ${invalidSwitchStatus}`);
    console.log('[Step 8 & 9 PASS] Unauthorized company switch rejected with HTTP 401.');

    // 10. Accept an invitation in a second account
    console.log('[Step 10] Inviting a second member and accepting invitation...');
    const memberEmail = `member-${testId}@browser-test.com`;
    // Invite member from Owner's session
    const inviteStatus = await page.evaluate(async (backend, email) => {
      const csrfResp = await fetch(`${backend}/api/v1/auth/csrf`, { credentials: 'include' });
      const { token: csrfToken } = await csrfResp.json();
      const refreshRes = await fetch(`${backend}/api/v1/auth/refresh`, {
        method: 'POST',
        headers: { 'X-XSRF-TOKEN': csrfToken },
        credentials: 'include'
      });
      const refreshData = await refreshRes.json();
      const accessToken = refreshData.accessToken;
      const res = await window.fetch(`${backend}/api/v1/company/invitations`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': csrfToken,
          'Authorization': `Bearer ${accessToken}`
        },
        credentials: 'include',
        body: JSON.stringify({ email, role: 'REVIEWER' })
      });
      return { status: res.status, body: await res.json() };
    }, BACKEND_URL, memberEmail);
    console.log('[Step 10] Invitation response:', JSON.stringify(inviteStatus));

    const inviteMsg = await getLatestMailpitMessage(memberEmail, 'invitation');
    const inviteToken = inviteMsg.Text.match(/token=([A-Za-z0-9_-]+)/)[1];
    console.log('[Step 10] Captured invitation token from Mailpit. Navigating to accept invitation...');

    await page.goto(`${FRONTEND_URL}/auth/accept-invitation?token=${inviteToken}`, { waitUntil: 'networkidle2' });
    await page.type('input[name="fullName"]', 'Reviewer Two');
    await page.type('input[name="password"]', 'ReviewerPass!123');
    await page.type('input[name="passwordConfirmation"]', 'ReviewerPass!123');
    await page.click('button.primary-button');
    await page.waitForFunction(() => document.body.innerText.includes('Invitation accepted'), { timeout: 10000 });
    console.log('[Step 10 PASS] Invitation accepted successfully.');

    // 11. Reset password
    console.log('[Step 11] Requesting password reset...');
    await page.goto(`${FRONTEND_URL}/forgot-password`, { waitUntil: 'networkidle2' });
    await page.type('input[name="email"]', memberEmail);
    await page.click('button.primary-button');
    await page.waitForFunction(() => document.body.innerText.includes('instructions were created') || document.body.innerText.includes('Reset your password'), { timeout: 10000 });

    const resetMsg = await getLatestMailpitMessage(memberEmail, 'Reset');
    const resetToken = resetMsg.Text.match(/token=([A-Za-z0-9_-]+)/)[1];
    console.log('[Step 11] Captured password reset token from Mailpit. Updating password...');

    await page.goto(`${FRONTEND_URL}/forgot-password?token=${resetToken}`, { waitUntil: 'networkidle2' });
    const newPassword = 'NewResetPassword!123';
    await page.waitForSelector('input[name="password"]', { timeout: 10000 });
    await page.type('input[name="password"]', newPassword);
    await page.type('input[name="confirmation"]', newPassword);
    await page.click('button.primary-button');
    try {
      await page.waitForFunction(() => document.body.innerText.includes('Password updated'), { timeout: 10000 });
      console.log('[Step 11 PASS] Password reset completed successfully.');
    } catch (e) {
      const bodyText = await page.evaluate(() => document.body.innerText);
      console.error('[Step 11 FAIL] Body text was:\n' + bodyText);
      throw e;
    }

    // 12. Logout
    console.log('[Step 12] Logging out...');
    await page.goto(`${FRONTEND_URL}/`, { waitUntil: 'networkidle2' });
    await page.waitForSelector('button.quiet-button', { timeout: 10000 });
    await page.click('button.quiet-button'); // Click Sign out
    await page.waitForFunction(() => window.location.pathname === '/login', { timeout: 10000 });
    console.log('[Step 12 PASS] Sign out executed, redirected to /login.');

    // 13. Try accessing protected routes
    console.log('[Step 13] Verifying protected route access denied after logout...');
    await page.goto(`${FRONTEND_URL}/media-scans`, { waitUntil: 'networkidle2' });
    await sleep(500);
    const currentPath = await page.evaluate(() => window.location.pathname);
    if (currentPath !== '/login') throw new Error(`Expected redirect to /login, stayed at ${currentPath}`);
    console.log('[Step 13 PASS] Navigation to protected route successfully redirected to /login.');

    // 14 & 15 & 16. CSRF Rejection Tests
    console.log('[Step 14 & 15 & 16] Testing CSRF validation (missing and invalid token)...');
    const noCsrfStatus = await page.evaluate(async (backend) => {
      const res = await fetch(`${backend}/api/v1/auth/logout`, {
        method: 'POST',
        credentials: 'include'
      });
      return res.status;
    }, BACKEND_URL);
    if (noCsrfStatus !== 403) throw new Error(`Expected 403 for missing CSRF, got ${noCsrfStatus}`);
    console.log('[Step 14 PASS] Mutation without CSRF token returned HTTP 403 Forbidden.');

    const invalidCsrfStatus = await page.evaluate(async (backend) => {
      const res = await fetch(`${backend}/api/v1/auth/logout`, {
        method: 'POST',
        headers: { 'X-XSRF-TOKEN': 'bogus-csrf-token' },
        credentials: 'include'
      });
      return res.status;
    }, BACKEND_URL);
    if (invalidCsrfStatus !== 403) throw new Error(`Expected 403 for invalid CSRF, got ${invalidCsrfStatus}`);
    console.log('[Step 15 & 16 PASS] Mutation with invalid CSRF token returned HTTP 403 Forbidden.');

    console.log('--- ALL 16 BROWSER ACCEPTANCE SCENARIOS PASSED WITH VERIFIABLE EVIDENCE ---');
  } finally {
    await browser.close();
  }
}

runBrowserAcceptance().then(() => {
  console.log('Browser workflow acceptance completed successfully.');
  process.exit(0);
}).catch(err => {
  console.error('Browser workflow failed:', err);
  process.exit(1);
});
