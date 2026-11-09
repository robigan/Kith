# Software Requirements Specification: Personal CRM for iPhone

| | |
|---|---|
| **Project** | TheRealDex (working product name in design: **Kith**) |
| **Platform** | iOS (iPhone), Kotlin Multiplatform shared logic + SwiftUI app shell |
| **Source design** | Claude Design document "Kith — iOS Personal CRM" (boards: Today, People, Contact profile, Log interaction, Groups & smart views) |
| **Status** | Draft v0.1 |
| **Date** | 2026-11-09 |

---

## 1. Introduction

### 1.1 Purpose
This document specifies the functional and non-functional requirements for a personal relationship manager ("personal CRM") for iPhone. It is derived from the five-screen interactive design and its accompanying notes (Core model, Motion spec). It is intended for developers, designers and testers of the app.

### 1.2 Scope
The app helps a single user keep in touch with the people who matter to them. It:

- stores rich, private notes about contacts (how we met, family, birthdays, gift ideas, work history);
- assigns each contact or group a **keep-in-touch cadence** and surfaces who is due;
- lets the user quickly **log interactions** (coffee, call, meeting, message, event), which resets the cadence;
- generates **reasons to reach out** from signals (birthdays, job changes, met-anniversaries, un-noted calendar meetings);
- organizes people into **groups** and **smart views** (saved filters);
- imports and enriches data from **sources**: iPhone Contacts, LinkedIn, Gmail and Google Calendar.

Out of scope for this version: multi-user/shared CRMs, sales pipelines, iPad/Mac-specific layouts, Android UI (the shared-logic module may make this possible later).

### 1.3 Definitions

| Term | Meaning |
|---|---|
| **Contact** | A person record in the app. |
| **Interaction** | A logged touchpoint with one or more contacts (type, date, people, note). |
| **Cadence** | Desired interval between interactions with a contact (1 week to 1 year, or never). |
| **Due date** | Last interaction date + cadence. |
| **Overdue** | Today is after the due date. |
| **Signal** | An external or derived event that suggests reaching out (birthday, title change, anniversary, calendar meeting without a note). |
| **Group** | A user-defined, manually curated set of contacts with its own default cadence. |
| **Smart view** | A saved filter over tags, groups, last contact, location or source. |
| **Source** | A connected data provider (iPhone Contacts, LinkedIn, Gmail, Google Calendar). |

### 1.4 Conventions
Requirements use IDs (`FR-xxx`, `NFR-xxx`) and **SHALL** (mandatory), **SHOULD** (recommended) and **MAY** (optional). Items marked *(inferred)* are not explicit in the design but are needed for a coherent product; they need confirmation (see §8).

---

## 2. Overall description

### 2.1 Product perspective
A standalone iPhone app. Business logic (data model, cadence computation, signal generation, filtering) lives in the Kotlin Multiplatform `sharedLogic` module; UI is native SwiftUI in `iosApp`.

### 2.2 User
A single individual who maintains personal and professional relationships (friends, family, colleagues, founders, mentors). They are a fluent iOS user and expect native iOS conventions, haptics and accessibility support.

### 2.3 Navigation structure
A bottom **tab bar** with three tabs:

1. **Today**: what needs attention now.
2. **People**: the full contact directory.
3. **Groups**: groups, smart views and data sources.

The **Contact profile** opens by pushing it onto the navigation stack from any list. **Log interaction** is a modal **sheet**.

### 2.4 Constraints
- C-1: Targets iPhone with iOS conventions (navigation stacks, sheets with detents, swipe actions, SF Pro system font).
- C-2: Shared logic SHALL be written in Kotlin (commonMain) and exposed to Swift.
- C-3: Third-party integrations (LinkedIn, Gmail, Google Calendar) are subject to those providers' API terms and availability.

---

## 3. Data model

### 3.1 Contact
| Field | Notes |
|---|---|
| id | Stable identifier |
| name | Display name; initials derived for avatar |
| avatar color | One of the palette tints (peach, sage, sky, lilac, sand, rose) |
| photo | Optional *(inferred)* |
| role / company | e.g. "Product Lead at Fernwood" |
| location | e.g. "London" |
| how we met | Free text, may reference another contact ("via Leo Brandt") |
| family | Free text / related people (partner, siblings, children) |
| birthday | Day + month, optional year |
| important dates | Additional named dates |
| gift ideas | List of free-text items |
| work history | Ordered list of employers with years |
| contact methods | Email(s), phone(s), etc. |
| tags | e.g. work, family |
| starred | Boolean |
| groups | Many-to-many with Group |
| cadence | Optional override (else inherited from group) |
| sources | Which sources contributed data (Contacts, LinkedIn, Gmail, Calendar) and per-source stats (e.g. "14 threads", "6 events") |

### 3.2 Interaction
| Field | Notes |
|---|---|
| id | |
| type | Coffee, Call, Meeting, Message, Event. Timeline also shows Email and Note entries |
| date | Default today |
| participants | One or more contacts |
| note | Free text; may be dictated |
| mentions | Contacts detected in the note text |
| follow-ups | Zero or more action items (e.g. "Intro Priya → Ada Lindqvist") |
| origin | Manual, or imported (Gmail thread, Calendar event) |

### 3.3 Group
name, member contacts, default cadence (weekly, 2 weeks, monthly, quarterly, …), and derived counts (members, due).

### 3.4 Smart view
name, icon code/colour, filter predicate over: tags, groups, last-contact age, location, source, birthday window, recent job change, how-we-met context.

### 3.5 Signal / Reason to reach out
type (birthday, new role, met-anniversary, calendar meeting needs note), subject contact(s), date, descriptive text, optional linked data (gift idea, calendar event).

### 3.6 Source connection
provider, connected state, last-sync time, status text.

---

## 4. Functional requirements

### 4.1 Cadence and reminders (core)
- **FR-001** The user SHALL be able to set a keep-in-touch cadence per contact and per group, ranging from 1 week to 1 year, or "Never".
- **FR-002** A contact's effective cadence SHALL be its own cadence if set, else its group's cadence. *(inferred: precedence when the contact belongs to several groups, e.g. shortest wins, needs confirmation.)*
- **FR-003** Due date SHALL be computed as last interaction date + effective cadence.
- **FR-004** Logging an interaction with a contact SHALL reset that contact's cadence (the new last-interaction date becomes the interaction's date).
- **FR-005** The system SHALL describe due status in relative language: "N days overdue", "Due today", "Due tomorrow", "Due <weekday>".
- **FR-006** The system SHALL deliver a local notification on a contact's reminder date *(inferred from "Kith will remind you on …")*.

### 4.2 Today tab
- **FR-010** The Today screen SHALL show the current date (e.g. "Monday, 9 November") and the title "Today".
- **FR-011** It SHALL provide an **Add contact** action.
- **FR-012** **Keep in touch** section: list contacts that are due or overdue (and those due within the next few days), each showing avatar, name, status (colored by urgency; overdue/today in accent orange), and cadence.
- **FR-013** The section header SHALL show the count of open items ("4 due") or "All caught up" when none remain.
- **FR-014** Each row SHALL have a toggle button to mark done or undo. When done, the row shows "Logged · next in <cadence>".
- **FR-015** Swiping a row **right** SHALL mark it **Done**. Swiping **left** SHALL **snooze it 1 week**.
- **FR-016** Marking done from Today SHALL record a lightweight interaction and reset the cadence *(inferred: whether it opens the Log sheet or logs silently needs confirmation)*.
- **FR-017** A helper caption SHALL explain the gestures: "Swipe right to mark done, left to snooze. Logging an interaction resets the cadence."
- **FR-018** **Reasons to reach out** section SHALL list signal cards, each with a label (e.g. "Birthday · Thursday", "New role · LinkedIn", "Met 1 year ago"), the contact avatar and a sentence. Birthday cards SHALL include a saved gift idea when one exists.
- **FR-019** **Needs a note · From Calendar** card: for a past calendar meeting with a known contact and no logged note, show date, title, location and duration with an **Add note** action that opens the Log interaction sheet prefilled.

### 4.3 People tab
- **FR-020** Title "People", with **Scan business card** and **Add contact** actions.
- **FR-021** A search field ("Names, companies, notes") SHALL perform full-text search across names, companies and interaction notes.
- **FR-022** Filter chips with counts: **All, Due, Starred, Family, Work**. Selecting one filters the list. Exactly one is active.
- **FR-023** The list SHALL be sorted alphabetically, sectioned by first letter, with an A–Z (+ #) section index on the trailing edge.
- **FR-024** Each row SHALL show avatar, name, and a subtitle (role/company, relationship, or contextual hint like "Birthday Thursday"), plus a **Due** badge when the contact is due.
- **FR-025** Tapping a row SHALL open the Contact profile.
- **FR-026** **Scan business card** SHALL capture a card with the camera, extract name/role/company/contact details via on-device OCR, and present a prefilled new-contact form *(behavior inferred)*.

### 4.4 Contact profile
- **FR-030** Header: large avatar, name, "role at company · location", and group/tag chips (e.g. "Close friends", "Product people", "London").
- **FR-031** Quick actions: **Message, Call, Email, Log**. Message/Call/Email SHALL hand off to the system apps. **Log** SHALL open the Log interaction sheet with this contact preselected.
- **FR-032** **Keep in touch** card: shows the cadence ("Every month") with a **Change** action, the last interaction ("Last: coffee, 28 Sep"), the overdue/due status, and a progress meter of elapsed vs. cadence days (accessible label e.g. "42 of 30 days elapsed, 12 days overdue").
- **FR-033** Segmented control with **Timeline** and **About**.
- **FR-034** **Timeline**: reverse-chronological interactions, each with a type badge (CAF, CALL, MAIL, EVT, NOTE), title, date and note.
- **FR-035** **About**: labeled facts: How we met, Birthday, Family, Gift ideas, Work history, Email (plus other contact methods).
- **FR-036** **Synced from** section listing contributing sources with stats (e.g. "Gmail · 14 threads", "Calendar · 6 events").
- **FR-037** Navigation bar: back to People, **Star** toggle, **Edit** (opens the editable form for all contact fields).

### 4.5 Log interaction (sheet)
- **FR-040** Presented as a sheet with **Cancel** and **Save**.
- **FR-041** Type selector: **Coffee (CAF), Call, Meeting (MTG), Message (MSG), Event (EVT)**. Coffee is the default.
- **FR-042** **With**: one or more participants, with **Add person**. Logging SHALL apply to every participant.
- **FR-043** **Date**: defaults to today, editable.
- **FR-044** **Note**: multi-line free text with a **Dictate note** button (speech-to-text).
- **FR-045** **Mention detection**: the system SHALL detect known contacts named in the note and show them (e.g. "Mentions: Ada Lindqvist"). It SHOULD detect facts implying updates, such as a new role, and offer to update the contact ("new role detected").
- **FR-046** **Next reminder** selector: **2 wks, 1 mo, 3 mo, Never**, defaulting to the contact's cadence. It shows a confirmation line, "Kith will remind you on <date>." or "Reminders are off for <name>."
- **FR-047** **Add a follow-up**: optional follow-up task(s) with text (suggested from the note, e.g. "Intro Priya → Ada Lindqvist") and an **Add** action.
- **FR-048** **Save** SHALL persist the interaction, reset cadence(s) per FR-004, schedule the next reminder, and dismiss the sheet.

### 4.6 Groups tab
- **FR-050** Title "Groups" with a **New group** action.
- **FR-051** Group list: each row shows up to three member avatars, name, "N people · <cadence>", and an "N due" badge when any members are due.
- **FR-052** Tapping a group SHALL show its members and allow editing name, members and cadence *(detail screen inferred)*.
- **FR-053** **Smart views** section: rows with an icon code, label and live count (e.g. "Not talked in 90+ days", "Birthdays this month", "Changed jobs recently", "Lives in London", "Met at conferences").
- **FR-054** The user SHALL be able to create, edit and delete smart views as saved filters over tags, groups, last contact, location or source.
- **FR-055** **Sources** section: each source (iPhone Contacts, LinkedIn, Google Calendar, Gmail) shows status ("Synced", "Synced · 2 h ago") or a **Connect** button when disconnected.

### 4.7 Sources and signals
- **FR-060** **iPhone Contacts**: import contacts with user permission. Keep them in sync.
- **FR-061** **LinkedIn**: import connections and detect role/company changes, producing "New role" signals.
- **FR-062** **Gmail**: associate email threads with contacts and show them in the timeline and source stats.
- **FR-063** **Google Calendar**: associate events with contacts. Create "Needs a note" signals for past meetings with no logged interaction.
- **FR-064** Signals SHALL also include upcoming birthdays (with gift ideas) and met-anniversaries (from "how we met" date).
- **FR-065** The user SHALL be able to disconnect a source. *(inferred: whether imported data is retained after disconnect needs confirmation.)*
- **FR-066** Data from multiple sources for the same person SHALL be merged into one contact, with duplicate detection *(inferred)*.

### 4.8 Contact management
- **FR-070** Create, edit and delete contacts manually.
- **FR-071** Star/unstar a contact.
- **FR-072** Add/remove tags and group memberships.

---

## 5. Non-functional requirements

### 5.1 Interaction and motion
- **NFR-001** Default spring for push/pop, tab switch and segment change: damping 1.0, response 0.35 s (no overshoot).
- **NFR-002** Sheets: damping 0.8, response 0.3 s, bouncing only after a flick. On release, the target detent SHALL be chosen by projecting release velocity (deceleration rate 0.998). Rubber-band past the top detent.
- **NFR-003** Swipe actions on Today rows: 10 px hysteresis before tracking, 1:1 finger tracking, and a `.success` haptic fired on commit in the same frame as the check fill.
- **NFR-004** Press feedback on touch-down: scale to 0.97 over 100 ms.

### 5.2 Accessibility
- **NFR-010** With **Reduce Motion** enabled, all transitions SHALL become cross-fades.
- **NFR-011** With **Reduce Transparency** enabled, the tab bar and sheets SHALL render with solid backgrounds.
- **NFR-012** All icon-only controls SHALL have VoiceOver labels (e.g. "Add contact", "Scan business card", "Dictate note", "Mark <name> as done" / "Undo, <name>").
- **NFR-013** Progress indicators SHALL expose a textual value (see FR-032).
- **NFR-014** Swipe actions SHALL also be reachable via VoiceOver custom actions *(inferred)*.
- **NFR-015** Text SHALL support Dynamic Type.

### 5.3 Visual design
- **NFR-020** Typography: system font (SF Pro).
- **NFR-021** Palette: near-black text `#111114`, secondary text `#3A3A40` / `#5E5E66`, grouped background `#F2F2F7`, white cards, accent `#B8461B` (urgent/overdue and primary actions).
- **NFR-022** Avatar/badge tints (background / foreground): peach `#FCE3D6/#8A3A12`, sage `#DDEFE3/#1F5E3B`, sky `#DCE8FA/#1E4E8C`, lilac `#E9E1F8/#5B3A99`, sand `#F3EBD6/#6B5317`, rose `#F8DDE4/#8E2D4B`.
- **NFR-023** Interaction types SHALL have consistent tint + code: coffee→peach/CAF, call→sage/CALL, email→sky/MAIL, event→lilac/EVT, note→sand/NOTE.
- **NFR-024** Dark Mode SHALL be supported *(inferred, not in design)*.

### 5.4 Privacy and security
- **NFR-030** All personal data SHALL be stored on-device by default. Any cloud sync SHALL be opt-in *(inferred)*.
- **NFR-031** Third-party OAuth tokens SHALL be stored in the iOS Keychain.
- **NFR-032** The app SHALL request Contacts, Camera, Microphone/Speech, and Notification permissions only when the related feature is first used, with clear purpose strings.
- **NFR-033** Speech recognition and business-card OCR SHOULD run on-device.

### 5.5 Performance
- **NFR-040** Search results SHALL update while typing, within 100 ms for up to 5,000 contacts *(target inferred)*.
- **NFR-041** Today screen SHALL render from local data without waiting for network sync.
- **NFR-042** Source sync SHALL run in the background and never block UI.

### 5.6 Localization
- **NFR-050** Dates SHALL be formatted per user locale (design uses en-GB style: "Monday, 9 November", "Fri 6 Nov").

---

## 6. Screen inventory

| Screen | Type | Entry points |
|---|---|---|
| Today | Tab root | Tab bar |
| People | Tab root | Tab bar |
| Groups & smart views | Tab root | Tab bar |
| Contact profile | Pushed | People row, Today row, signal card, group detail |
| Log interaction | Sheet | Profile "Log", "Add note" on calendar card, Today row *(inferred)* |
| Add / Edit contact | Sheet or push *(not designed)* | Add contact, Edit, card scan |
| Scan business card | Full-screen camera *(not designed)* | People |
| Group detail / editor | Pushed *(not designed)* | Groups row, New group |
| Smart view results / editor | Pushed *(not designed)* | Smart view row |
| Source connect flow | System auth / OAuth *(not designed)* | Connect |

---

## 7. Sample acceptance scenarios

1. **Overdue contact**: Given Priya's cadence is monthly and the last interaction was 28 Sep, when today is 9 Nov, then Today lists Priya as "12 days overdue" in accent color, and her profile meter shows 42 of 30 days elapsed.
2. **Swipe done**: Given Sam is "Due today", when the user swipes his row right past the threshold, then a success haptic fires, the row shows "Logged · next in 2 weeks", and the "due" count decrements.
3. **Snooze**: When the user swipes Jules's row left, then his due date moves forward 1 week and he leaves the due list until then.
4. **Log with reminder**: Given the Log sheet for Priya with "1 mo" selected on Fri 6 Nov, when Save is tapped, then a Coffee interaction is stored, the cadence is reset, and a reminder is scheduled for Sun 6 Dec.
5. **Reminders off**: When "Never" is selected, the sheet shows "Reminders are off for Priya." and no reminder is scheduled.
6. **Filter**: When the People "Starred" chip is tapped, only starred contacts are listed and the chip shows the count.
7. **All caught up**: When every Keep-in-touch row is marked done, the header reads "All caught up".

---

## 8. Open questions

1. Product name: "Kith" (design) vs. "TheRealDex" (repo)?
2. Contact in multiple groups with different cadences: which cadence wins?
3. Does marking done from Today log a silent interaction, or open the Log sheet?
4. Is snooze duration configurable, or fixed at 1 week?
5. Which "Due" window does Today show (overdue + next N days)?
6. LinkedIn integration: the public API is heavily restricted, so which mechanism should we use (official API, data export import, manual)?
7. Is iCloud sync or backup in scope for v1?
8. Should follow-ups become reminders/tasks with due dates, and where do they appear?
9. Retention of source-imported data after disconnecting a source.
10. Dark Mode and iPad support for v1?
