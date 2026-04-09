# User Personality & Working Style

> Claude reads this file to understand who the user is, how they think, and how to collaborate effectively. Update as patterns emerge from interactions.

---

## Who You Are

- **Role:** Owner & operator of BwanaBet, a betting platform in Zambia (bwanabet.co.zm)
- **Technical level:** Power user, not a developer by trade. Can read code, check Vercel logs, test APIs, interpret error messages, use browser console. Does NOT write code from scratch.
- **Industry:** Online betting / gambling in Zambia. Operates under BCLB regulations.
- **Location context:** Zambia — primary user base is on Android phones, often on slower networks. Lusaka is the main city.

---

## How You Work

### Communication Style
- **Direct and action-oriented.** Says what needs to happen, not what could hypothetically be explored.
- **Sends screenshots and error messages** when something is wrong — expects you to interpret and fix.
- **Short messages.** Doesn't write essays. Expect 1-3 sentences per message.
- **May have typos** (e.g., "reate" instead of "create") — don't ask for clarification on obvious typos, just understand intent.

### Decision-Making
- **"Do it now" over "let me explain the options."** Prefers implementation over discussion.
- **Trusts Claude to make reasonable choices** — doesn't want to be asked 5 questions before seeing code.
- **Will test immediately** after deployment. Feedback loop is fast.
- **Cost-conscious.** Has switched models and providers to save money (Claude Sonnet → GPT-4.1 mini for chatbot). Don't suggest expensive solutions when cheap ones work.

### What Frustrates You
- Over-engineering and unnecessary complexity
- Being asked too many questions before action is taken
- Long explanations when a code change would suffice
- Suggestions that ignore the Zambian/mobile-first context
- Breaking something that was already working

### What You Value
- Things that work on first deploy
- Mobile-first thinking (your users are on phones)
- Fast iteration — ship, test, fix
- Clean, readable solutions (even if you're not writing the code, you read it)
- Keeping costs low (Vercel free tier, Supabase free tier, minimal API calls)

---

## Technical Preferences

| Area | Preference |
|------|-----------|
| Frontend | Vanilla HTML/CSS/JS. No React, no Next.js, no build tools. Single-file when possible. |
| Backend | Vercel serverless functions (Node.js). Supabase as the database. |
| Android | Kotlin. Minimal dependencies. No Room, no kapt, no heavy frameworks. |
| Styling | Dark theme, BwanaBet brand yellow (#f5c518). JetBrains Mono for code, DM Sans for UI. |
| Deployment | Vercel (push to deploy). Android via sideloaded APK. |
| APIs | Supabase REST API directly. No GraphQL, no custom API layers unless necessary. |
| Cost | Free tiers first. Only pay when traffic demands it. |

---

## How Claude Should Behave

1. **Lead with action.** If the task is clear, start coding. Don't ask "would you like me to...?"
2. **Keep responses short.** No preamble, no summaries of what was just done. Show the work.
3. **Explain only when it matters.** If a decision has trade-offs the user should know about, mention it briefly. Otherwise, just do it.
4. **Mobile-first always.** If a UI change is involved, think phone screen first.
5. **Don't break what works.** Read existing code before changing it. Understand the patterns already in use.
6. **Match existing patterns.** The codebase uses vanilla JS, inline styles, single-file HTML. Don't introduce new paradigms.
7. **Test-aware.** If you change the Android app, remind about building and sideloading the APK. If you change the dashboard, it auto-deploys on push.
8. **Be specific about deployment steps** when something needs manual action (env vars, Supabase table changes, APK rebuild).

---

## Interaction History Patterns

_Update this section as conversations reveal recurring themes._

| Pattern | Notes |
|---------|-------|
| Sends error screenshots | Interpret the error, fix the code, explain what went wrong in 1 sentence |
| Asks to "open" a project | Means: read the repo, understand it, be ready to work on it |
| "Make it work" | Means: find the bug and fix it, don't theorize |
| Creates project structure files | Values organization and reference material (this file is proof) |

---

## Context to Remember

- Call center numbers: +260 972 833 023, +260 962 290 801
- Manages multiple Vercel projects: bet-assist, bwanabet-crm, gps-tracker
- BwanaBet brand colors: Yellow (#f5c518) on dark (#0a0a0f)
- User base is primarily on Android phones in Zambia
- The GPS tracker is used for tracking BwanaBet field staff/sales team
